package com.taha.newraapp.ui.screens.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taha.newraapp.data.socket.MessageSyncService
import com.taha.newraapp.data.socket.SocketManager
import com.taha.newraapp.data.socket.SocketStatus
import com.taha.newraapp.domain.model.Message
import com.taha.newraapp.domain.model.User
import com.taha.newraapp.domain.repository.MessageRepository
import com.taha.newraapp.domain.repository.UserRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val socketManager: SocketManager,
    private val messageSyncService: MessageSyncService
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

    // Socket connection status
    val connectionStatus: StateFlow<SocketStatus> = socketManager.connectionStatus

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
     * Load user data first, then initialize message sync.
     * Order matters: we need currentUserId before starting incoming listener.
     */
    private fun loadDataAndInitSync() {
        viewModelScope.launch {
            // Load current user first
            _currentUser.value = userRepository.getCurrentUser()
            val users = userRepository.getAllUsers()
            _chatPartnerUser.value = users.find { it.id == chatId }

            // Set currentUserId on MessageSyncService for incoming message handling
            _currentUser.value?.let { user ->
                messageSyncService.setCurrentUserId(user.id)
                Log.d(TAG, "Set current user ID for message sync: ${user.id}")
            }

            // Start observing connection status
            startMessageSync()
        }
    }

    /**
     * Start the MessageSyncService to process pending messages, incoming messages,
     * and delivery confirmations. This runs while the ViewModel is alive.
     */
    private fun startMessageSync() {
        viewModelScope.launch {
            // Start sync when socket connects
            connectionStatus.collect { status ->
                if (status == SocketStatus.CONNECTED) {
                    Log.d(TAG, "Socket connected, starting message sync")
                    messageSyncService.startSync()
                    messageSyncService.startIncomingMessageListener()
                    messageSyncService.startDeliveryConfirmationListener()
                    messageSyncService.startSeenConfirmationListener()
                } else {
                    Log.d(TAG, "Socket disconnected, stopping message sync")
                    messageSyncService.stopSync()
                    messageSyncService.stopIncomingListener()
                    messageSyncService.stopDeliveryListener()
                    messageSyncService.stopSeenListener()
                }
            }
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
    }

    /**
     * Send a message - saves to Room first, then MessageSyncService handles sending.
     * This ensures messages are never lost even if the app crashes.
     */
    fun sendMessage() {
        val content = _messageInput.value.trim()
        if (content.isBlank()) return

        val sender = _currentUser.value ?: run {
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

    override fun onCleared() {
        super.onCleared()
        // Stop sync when ViewModel is cleared
        messageSyncService.stopSync()
    }
}

