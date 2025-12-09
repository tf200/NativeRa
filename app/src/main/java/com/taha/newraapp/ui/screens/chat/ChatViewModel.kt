package com.taha.newraapp.ui.screens.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taha.newraapp.data.socket.ChatSocketService
import com.taha.newraapp.data.socket.MessageAckResult
import com.taha.newraapp.data.socket.SocketManager
import com.taha.newraapp.data.socket.SocketStatus
import com.taha.newraapp.data.socket.model.SocketMessagePayload
import com.taha.newraapp.domain.model.Message
import com.taha.newraapp.domain.model.MessageStatus
import com.taha.newraapp.domain.model.MessageType
import com.taha.newraapp.domain.model.User
import com.taha.newraapp.domain.repository.MessageRepository
import com.taha.newraapp.domain.repository.UserRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val chatSocketService: ChatSocketService,
    private val socketManager: SocketManager
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        // TODO: Get this from device info storage
        private const val DEVICE_ID = "android-device"
    }

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _messageInput = MutableStateFlow("")
    val messageInput = _messageInput.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _chatPartnerUser = MutableStateFlow<User?>(null)
    val chatPartnerUser = _chatPartnerUser.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError = _sendError.asStateFlow()

    // Socket connection status
    val connectionStatus: StateFlow<SocketStatus> = socketManager.connectionStatus

    // Messages from local database
    val messages: StateFlow<List<Message>> = messageRepository.getMessages(chatId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Pending messages (optimistic UI)
    private val _pendingMessages = MutableStateFlow<List<Message>>(emptyList())
    val pendingMessages = _pendingMessages.asStateFlow()

    // Combined messages (database + pending)
    val allMessages: StateFlow<List<Message>> = combine(messages, pendingMessages) { dbMessages, pending ->
        (dbMessages + pending).sortedBy { it.timestamp }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadData()
        observeIncomingMessages()
        observeMessageAcknowledgments()
    }

    private fun loadData() {
        viewModelScope.launch {
            _currentUser.value = userRepository.getCurrentUser()
            val users = userRepository.getAllUsers()
            _chatPartnerUser.value = users.find { it.officerId == chatId }
        }
    }

    /**
     * Observe incoming messages from Socket.IO and save to local database.
     */
    private fun observeIncomingMessages() {
        viewModelScope.launch {
            chatSocketService.incomingMessages.collect { payload ->
                handleIncomingMessage(payload)
            }
        }
    }

    /**
     * Observe message acknowledgments and update pending messages.
     */
    private fun observeMessageAcknowledgments() {
        viewModelScope.launch {
            chatSocketService.messageAcknowledged.collect { result ->
                when (result) {
                    is MessageAckResult.Success -> {
                        Log.d(TAG, "Message sent successfully: ${result.messageId}")
                        // Remove from pending - the message will appear from database sync
                        removePendingMessage(result.localId)
                        _isSending.value = false
                    }
                    is MessageAckResult.Failure -> {
                        Log.e(TAG, "Message send failed: ${result.error}")
                        markPendingAsFailed(result.localId)
                        _sendError.value = result.error
                        _isSending.value = false
                    }
                }
            }
        }
    }

    fun onMessageinputChanged(input: String) {
        _messageInput.value = input
        _sendError.value = null
    }

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

        _isSending.value = true
        _sendError.value = null

        // Send via Socket.IO
        val localId = chatSocketService.sendTextMessage(
            content = content,
            senderId = sender.officerId,
            senderName = "${sender.firstName} ${sender.lastName}",
            receiverIds = listOf(receiver.officerId),
            deviceId = DEVICE_ID,
            roomId = chatId
        )

        // Add to pending messages for optimistic UI
        val pendingMessage = Message(
            id = "pending_$localId",
            conversationId = chatId,
            senderId = sender.officerId,
            recipientId = receiver.officerId,
            content = content,
            type = MessageType.TEXT,
            status = MessageStatus.PENDING,
            timestamp = System.currentTimeMillis()
        )
        _pendingMessages.value = _pendingMessages.value + pendingMessage

        // Clear input immediately for better UX
        _messageInput.value = ""
    }

    fun clearError() {
        _sendError.value = null
    }

    fun retryFailedMessage(localId: Long) {
        // Find the failed message and retry
        val failedMessage = _pendingMessages.value.find { 
            it.id == "pending_$localId" || it.id == "failed_$localId" 
        }
        
        if (failedMessage != null) {
            // Remove the failed message
            removePendingMessage(localId)
            
            // Set the input and trigger send
            _messageInput.value = failedMessage.content
            sendMessage()
        }
    }

    private fun handleIncomingMessage(payload: SocketMessagePayload) {
        viewModelScope.launch {
            try {
                val socketMessage = payload.message
                val metadata = socketMessage.metadata

                // Only process if this message is for the current chat
                if (metadata.sender == chatId || metadata.receivers.contains(_currentUser.value?.officerId)) {
                    // Save to local database
                    messageRepository.sendMessage(
                        peerId = if (metadata.sender == _currentUser.value?.officerId) 
                            metadata.receivers.firstOrNull() ?: chatId 
                        else metadata.sender,
                        content = socketMessage.content ?: ""
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming message", e)
            }
        }
    }

    private fun removePendingMessage(localId: Long) {
        _pendingMessages.value = _pendingMessages.value.filter { 
            it.id != "pending_$localId" && it.id != "failed_$localId"
        }
    }

    private fun markPendingAsFailed(localId: Long) {
        _pendingMessages.value = _pendingMessages.value.map { msg ->
            if (msg.id == "pending_$localId") {
                msg.copy(id = "failed_$localId", status = MessageStatus.FAILED)
            } else msg
        }
    }
}
