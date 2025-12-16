package com.taha.newraapp.ui.screens.chat

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taha.newraapp.data.model.UserPresenceStatus
import com.taha.newraapp.data.repository.AttachmentRepository
import com.taha.newraapp.data.repository.UploadProgress
import com.taha.newraapp.data.repository.UploadState
import com.taha.newraapp.data.service.ChatPreloadService
import com.taha.newraapp.data.socket.MessageSyncService
import com.taha.newraapp.data.socket.PresenceService
import com.taha.newraapp.data.socket.SocketManager
import com.taha.newraapp.data.socket.SocketStatus
import com.taha.newraapp.domain.model.Message
import com.taha.newraapp.domain.model.User
import com.taha.newraapp.domain.repository.MessageRepository
import com.taha.newraapp.domain.repository.UserRepository
import com.taha.newraapp.data.socket.TypingService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    socketManager: SocketManager,
    private val messageSyncService: MessageSyncService,
    private val presenceService: PresenceService,
    private val attachmentRepository: AttachmentRepository,
    private val chatPreloadService: ChatPreloadService,
    private val typingService: TypingService
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _messageInput = MutableStateFlow("")
    val messageInput = _messageInput.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _chatPartnerUser = MutableStateFlow<User?>(null)
    val chatPartnerUser = _chatPartnerUser.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError = _sendError.asStateFlow()

    // Upload progress tracking by message ID (for showing progress in chat bubbles)
    private val _uploadProgressMap = MutableStateFlow<Map<String, UploadProgress>>(emptyMap())
    val uploadProgressMap: StateFlow<Map<String, UploadProgress>> = _uploadProgressMap.asStateFlow()

    // Socket connection status
    val connectionStatus: StateFlow<SocketStatus> = socketManager.connectionStatus

    // Chat partner's online presence (derived from global presence state)
    val chatPartnerPresence: StateFlow<UserPresenceStatus?> = presenceService.presenceState
        .map { it[chatId] }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Whether the chat partner is currently typing
    val isPartnerTyping: StateFlow<Boolean> = typingService.typingState
        .map { it.containsKey(chatId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Messages from local database (this is now the single source of truth)
    // Includes PENDING, SENT, DELIVERED, READ, and FAILED statuses
    val messages: StateFlow<List<Message>> = messageRepository.getMessages(chatId)
        .onEach { msgs ->
            Log.d(TAG, "Messages list updated from repository: count=${msgs.size}")
            if (msgs.isNotEmpty()) {
                val lastMsg = msgs.last()
                Log.d(TAG, "Last message: id=${lastMsg.id}, status=${lastMsg.status}, content='${lastMsg.content}'")
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadDataAndInitSync()
    }

    /**
     * Load user data, using preloaded cache if available for instant display.
     * Falls back to database queries if cache miss.
     */
    private fun loadDataAndInitSync() {
        viewModelScope.launch {
            // Check for preloaded cache first (from ContactsScreen tap)
            val cachedData = chatPreloadService.getCachedData(chatId)
            
            if (cachedData != null) {
                // Use cached data instantly - no database query needed!
                Log.d(TAG, "Using preloaded cache for chat: $chatId")
                _currentUser.value = cachedData.currentUser
                _chatPartnerUser.value = cachedData.chatPartner
                chatPreloadService.clearCache()
            } else {
                // Cache miss - load from database (slower, but still works)
                Log.d(TAG, "Cache miss, loading from database for chat: $chatId")
                _currentUser.value = userRepository.getCurrentUser()
                val users = userRepository.getAllUsers()
                _chatPartnerUser.value = users.find { it.id == chatId }
            }

            Log.d(TAG, "Chat room initialized for chat: $chatId")

            // Fetch initial presence status for chat partner (immediate feedback)
            presenceService.fetchPresenceStatus(listOf(chatId))
            
            // Subscribe to real-time presence updates for chat partner (targeted updates)
            presenceService.subscribeToPresence(listOf(chatId))
        }
    }

    /**
     * Mark all unread messages from the chat partner as seen/read.
     * Called when the chat room is opened/visible.
     */
    fun markMessagesAsSeen() {
        viewModelScope.launch {
            try {
                messageSyncService.markMessagesAsSeen(chatId)
                Log.d(TAG, "Marked messages as seen for chat: $chatId")
            } catch (e: Exception) {
                Log.e(TAG, "Error marking messages as seen", e)
            }
        }
    }

    fun onMessageinputChanged(input: String) {
        _messageInput.value = input
        _sendError.value = null
        
        // Trigger typing indicator when user types
        if (input.isNotEmpty()) {
            typingService.sendTypingIndicator(chatId)
        }
    }

    /**
     * Send a message - saves to Room first, then MessageSyncService handles sending.
     * This ensures messages are never lost even if the app crashes.
     */
    fun sendMessage() {
        val content = _messageInput.value.trim()
        if (content.isBlank()) return

        _currentUser.value ?: run {
            _sendError.value = "User not loaded"
            return
        }

        val receiver = _chatPartnerUser.value ?: run {
            _sendError.value = "Chat partner not found"
            return
        }

        _sendError.value = null

        viewModelScope.launch {
            try {
                // Save to Room with PENDING status
                // MessageSyncService will pick it up and send via socket
                messageRepository.sendMessage(
                    peerId = receiver.id,
                    content = content
                )
                
                // Clear input immediately for better UX
                _messageInput.value = ""
                
                // Clear typing debounce so next keystroke triggers typing again
                typingService.clearDebounceFor(receiver.id)
                
                Log.d(TAG, "Message queued for sending to ${receiver.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Error queuing message", e)
                _sendError.value = "Failed to queue message: ${e.message}"
            }
        }
    }

    fun clearError() {
        _sendError.value = null
    }
    
    /**
     * Send an attachment - prepares file and queues for upload.
     * The AttachmentRepository handles file copying, message creation, and WorkManager upload.
     * MessageSyncService will automatically send the message once upload completes.
     * 
     * @param uri Content URI of the file to send
     * @param fileType Display type (IMAGE, VIDEO, AUDIO, FILE)
     */
    fun sendAttachment(uri: Uri, fileType: String) {
        val sender = _currentUser.value ?: run {
            _sendError.value = "User not loaded"
            return
        }

        val receiver = _chatPartnerUser.value ?: run {
            _sendError.value = "Chat partner not found"
            return
        }

        viewModelScope.launch {
            try {
                val messageId = attachmentRepository.prepareAttachment(
                    uri = uri,
                    fileType = fileType,
                    conversationId = receiver.id,
                    senderId = sender.id,
                    recipientId = receiver.id
                )
                Log.d(TAG, "Attachment queued for upload: $messageId, type: $fileType")
                
                // Observe upload progress for this message
                viewModelScope.launch {
                    attachmentRepository.observeUploadProgress(messageId)
                        .collect { progress ->
                            _uploadProgressMap.update { current ->
                                if (progress.state == UploadState.COMPLETE) {
                                    // Remove from map when complete
                                    current - messageId
                                } else {
                                    current + (messageId to progress)
                                }
                            }
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending attachment", e)
                _sendError.value = "Failed to send attachment: ${e.message}"
            }
        }
    }

    /**
     * Retry a failed message.
     */
    fun retryFailedMessage(messageId: String) {
        viewModelScope.launch {
            try {
                messageSyncService.retryFailedMessage(messageId)
                Log.d(TAG, "Retrying failed message: $messageId")
            } catch (e: Exception) {
                Log.e(TAG, "Error retrying message", e)
                _sendError.value = "Failed to retry: ${e.message}"
            }
        }
    }

    /**
     * Delete a failed message (user gave up).
     */
    fun deleteFailedMessage(messageId: String) {
        viewModelScope.launch {
            try {
                messageSyncService.deleteFailedMessage(messageId)
                Log.d(TAG, "Deleted failed message: $messageId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting message", e)
            }
        }
    }

    // Note: Incoming messages are now handled by MessageSyncService.
    // It receives from ChatSocketService.incomingMessages, saves to Room DB,
    // and sends delivery acks. The UI automatically updates via Room Flow.

    // ========================================
    // Audio Recording
    // ========================================
    private var audioRecorder: com.taha.newraapp.ui.util.AudioRecorder? = null
    private var recordingJob: kotlinx.coroutines.Job? = null
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()
    
    private val _recordingAmplitude = MutableStateFlow(0)
    val recordingAmplitude: StateFlow<Int> = _recordingAmplitude.asStateFlow()
    
    /**
     * Start audio recording.
     */
    fun startRecording(context: android.content.Context) {
        if (_isRecording.value) return
        
        audioRecorder = com.taha.newraapp.ui.util.AudioRecorder(context)
        if (audioRecorder?.startRecording() == true) {
            _isRecording.value = true
            
            // Start timer to update duration and amplitude
            recordingJob = viewModelScope.launch {
                while (_isRecording.value) {
                    audioRecorder?.updateDuration()
                    audioRecorder?.updateAmplitude()
                    _recordingDuration.value = audioRecorder?.recordingDuration?.value ?: 0L
                    _recordingAmplitude.value = audioRecorder?.amplitude?.value ?: 0
                    kotlinx.coroutines.delay(100)
                }
            }
        } else {
            _sendError.value = "Failed to start recording"
        }
    }
    
    /**
     * Stop recording and return the file path.
     */
    fun stopRecording(): String? {
        recordingJob?.cancel()
        recordingJob = null
        _isRecording.value = false
        _recordingDuration.value = 0L
        _recordingAmplitude.value = 0
        return audioRecorder?.stopRecording()
    }
    
    /**
     * Manually trigger download for a message attachment.
     * Used for large files that weren't auto-downloaded.
     */
    fun downloadAttachment(messageId: String) {
        viewModelScope.launch {
            try {
                val message = messages.value.find { it.id == messageId }
                if (message != null && message.attachmentFileType != null) {
                    val attachmentId = message.attachmentId ?: messageId
                    Log.d(TAG, "Manual download triggered for message: $messageId, attachmentId: $attachmentId")
                    attachmentRepository.downloadAttachment(
                        messageId = messageId,
                        attachmentId = attachmentId,
                        conversationId = chatId
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering download: ${e.message}")
            }
        }
    }

    /**
     * Cancel recording without saving.
     */
    fun cancelRecording() {
        recordingJob?.cancel()
        recordingJob = null
        audioRecorder?.cancelRecording()
        _isRecording.value = false
        _recordingDuration.value = 0L
        _recordingAmplitude.value = 0
    }

    override fun onCleared() {
        super.onCleared()
        // Note: Message sync is now handled globally by GlobalMessageHandler
        // We only clean up chat-room specific resources here
        
        // Cancel any ongoing recording
        cancelRecording()
        
        // Unsubscribe from chat partner's presence updates
        presenceService.unsubscribeFromPresence(listOf(chatId))
    }
}

