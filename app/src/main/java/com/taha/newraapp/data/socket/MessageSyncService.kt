package com.taha.newraapp.data.socket

import android.util.Log
import com.taha.newraapp.data.local.dao.MessageDao
import com.taha.newraapp.data.local.entities.ConversationEntity
import com.taha.newraapp.data.local.entities.MessageEntity
import com.taha.newraapp.data.socket.model.ReceivedMessage
import com.taha.newraapp.data.socket.model.SeenConfirmation
import com.taha.newraapp.domain.model.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.min
import kotlin.math.pow

/**
 * MessageSyncService handles the reliable delivery of messages using a per-conversation queue.
 * 
 * Features:
 * - Per-conversation ordering (messages to different users don't block each other)
 * - Exponential backoff retry (1s, 2s, 4s, 8s... up to 60s)
 * - Max retry limit (fails after N attempts, user can manually retry)
 * - Observes Room DB for pending messages (instant when foreground)
 * - Socket connection status aware
 * - Automatic attachment download for incoming messages
 */
class MessageSyncService(
    private val messageDao: MessageDao,
    private val chatSocketService: ChatSocketService,
    private val socketManager: SocketManager,
    private val attachmentRepository: com.taha.newraapp.data.repository.AttachmentRepository? = null,
    private val typingService: TypingService? = null
) {
    companion object {
        private const val TAG = "MessageSyncService"
        private const val MAX_RETRY_COUNT = 5
        private const val BASE_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 60_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    private var incomingJob: Job? = null  // Job for incoming message listener
    private var deliveryJob: Job? = null  // Job for delivery confirmation listener

    // Track messages currently being sent to avoid duplicate sends
    private val _sendingMessages = MutableStateFlow<Set<String>>(emptySet())
    val sendingMessages: StateFlow<Set<String>> = _sendingMessages.asStateFlow()

    // Current user ID - set this before starting sync
    private var currentUserId: String = ""

    /**
     * Set the current user ID (required for incoming message handling).
     * This should be the database ID that matches socket.data.userId.
     */
    fun setCurrentUserId(userId: String) {
        currentUserId = userId
        Log.d(TAG, "Current user ID set: $userId")
    }

    /**
     * Start observing pending messages and processing the queue.
     * Call this when the app comes to foreground or socket connects.
     */
    fun startSync() {
        if (syncJob?.isActive == true) {
            Log.d(TAG, "Sync already running")
            return
        }

        Log.d(TAG, "Starting message sync service. Current user ID: $currentUserId")
        syncJob = scope.launch {
            // Observe the oldest pending message per conversation
            // This ensures we maintain per-conversation ordering
            messageDao.getOldestPendingPerConversation(System.currentTimeMillis())
                .distinctUntilChanged()
                .collect { pendingMessages ->
                    Log.d(TAG, "Found ${pendingMessages.size} pending messages conversations to process")
                    processPendingMessages(pendingMessages)
                }
        }
    }

    /**
     * Stop the sync service.
     * Call this when app goes to background or socket disconnects.
     */
    fun stopSync() {
        Log.d(TAG, "Stopping message sync service")
        syncJob?.cancel()
        syncJob = null
    }

    /**
     * Process pending messages - one per conversation (head-of-line).
     * This runs in parallel for different conversations but sequentially within each.
     */
    private suspend fun processPendingMessages(messages: List<MessageEntity>) {
        // Skip if socket not connected
        if (socketManager.connectionStatus.value != SocketStatus.CONNECTED) {
            Log.d(TAG, "Socket not connected, skipping message processing")
            return
        }

        messages.forEach { message ->
            // Skip if already being sent
            if (_sendingMessages.value.contains(message.id)) {
                return@forEach
            }

            // Check if it's time to retry (nextRetryAt)
            if (message.nextRetryAt > System.currentTimeMillis()) {
                // Schedule delayed processing
                val delayMs = message.nextRetryAt - System.currentTimeMillis()
                scope.launch {
                    delay(delayMs)
                    sendMessage(message)
                }
            } else {
                sendMessage(message)
            }
        }
    }

    /**
     * Send a single message via Socket.IO.
     */
    private suspend fun sendMessage(message: MessageEntity) {
        // Mark as sending to prevent duplicates
        _sendingMessages.value = _sendingMessages.value + message.id
        
        Log.d(TAG, "Sending message via socket: ${message.id}, content='${message.content}'")

        try {
            // Build attachment object if present
            val attachment = if (!message.attachmentId.isNullOrBlank()) {
                com.taha.newraapp.data.socket.model.MessageAttachment(
                    id = message.attachmentId,
                    type = message.attachmentFileType ?: "FILE",
                    filename = message.attachmentFileName ?: "attachment",
                    mimeType = message.attachmentMimeType ?: "application/octet-stream",
                    size = message.attachmentSize ?: 0L
                )
            } else null
            
            // Send via ChatSocketService
            chatSocketService.sendMessage(
                messageId = message.id,
                content = message.content,
                messageType = message.type,
                senderId = message.senderId,
                receiverIds = listOf(message.recipientId),
                attachment = attachment,
                onAck = { success, error ->
                    scope.launch {
                        handleSendResult(message, success, error)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message: ${e.message}")
            handleSendResult(message, false, e.message)
        }
    }

    /**
     * Handle the result of a send attempt.
     */
    private suspend fun handleSendResult(message: MessageEntity, success: Boolean, error: String?) {
        // Remove from sending set
        _sendingMessages.value = _sendingMessages.value - message.id

        if (success) {
            Log.d(TAG, "Message sent successfully: ${message.id}")
            messageDao.updateMessageStatus(message.id, MessageStatus.SENT.name)
        } else {
            Log.w(TAG, "Message send failed: ${message.id}, error: $error")
            handleRetry(message)
        }
    }

    /**
     * Handle retry logic with exponential backoff.
     */
    private suspend fun handleRetry(message: MessageEntity) {
        val newRetryCount = message.retryCount + 1

        if (newRetryCount >= MAX_RETRY_COUNT) {
            // Max retries reached - mark as failed
            Log.w(TAG, "Max retries reached for message: ${message.id}")
            messageDao.updateMessageStatusAndRetry(
                messageId = message.id,
                status = MessageStatus.FAILED.name,
                retryCount = newRetryCount,
                nextRetryAt = 0L
            )
        } else {
            // Calculate exponential backoff delay
            val delayMs = calculateBackoffDelay(newRetryCount)
            val nextRetryAt = System.currentTimeMillis() + delayMs
            
            Log.d(TAG, "Scheduling retry for message: ${message.id}, attempt: $newRetryCount, delay: ${delayMs}ms")
            messageDao.updateRetryInfo(
                messageId = message.id,
                retryCount = newRetryCount,
                nextRetryAt = nextRetryAt
            )
        }
    }

    /**
     * Calculate exponential backoff delay.
     * 1s, 2s, 4s, 8s, 16s, 32s, up to 60s max.
     */
    private fun calculateBackoffDelay(retryCount: Int): Long {
        val delay = BASE_RETRY_DELAY_MS * 2.0.pow(retryCount.toDouble()).toLong()
        return min(delay, MAX_RETRY_DELAY_MS)
    }

    /**
     * Manually retry a failed message.
     */
    suspend fun retryFailedMessage(messageId: String) {
        Log.d(TAG, "Manually retrying message: $messageId")
        messageDao.updateMessageStatusAndRetry(
            messageId = messageId,
            status = MessageStatus.PENDING.name,
            retryCount = 0,
            nextRetryAt = 0L
        )
    }

    /**
     * Delete a failed message (user gave up).
     */
    suspend fun deleteFailedMessage(messageId: String) {
        // For now, just mark as a special status. 
        // In production, you might actually delete or archive.
        Log.d(TAG, "User deleted failed message: $messageId")
        messageDao.updateMessageStatus(messageId, "DELETED")
    }

    // ========================================
    // Incoming Message Handling
    // ========================================

    /**
     * Start listening for incoming messages from ChatSocketService.
     * Saves received messages to local DB.
     */
    fun startIncomingMessageListener() {
        if (incomingJob?.isActive == true) {
            Log.d(TAG, "Incoming message listener already running")
            return
        }

        Log.d(TAG, "Starting incoming message listener. Current ID: $currentUserId")
        incomingJob = scope.launch {
            chatSocketService.incomingMessages.collect { message ->
                handleIncomingMessage(message)
            }
        }
    }

    /**
     * Stop listening for incoming messages.
     */
    fun stopIncomingListener() {
        Log.d(TAG, "Stopping incoming message listener")
        incomingJob?.cancel()
        incomingJob = null
    }

    /**
     * Handle an incoming message from the socket OR from FCM.
     * 1. Save to local Room DB
     * 2. Update conversation with unread count
     * 3. Queue attachment download if present
     * 4. Send delivery confirmation
     * 
     * Note: Duplicate messages are handled by Room's REPLACE strategy.
     * Can be called from socket listener OR FCM handler when socket is connected.
     */
    suspend fun handleIncomingMessage(message: ReceivedMessage) {
        try {
            // Skip messages we sent ourselves (echoed back from server)
            if (message.senderId == currentUserId) {
                Log.d(TAG, "Ignoring own message echo: ${message.id}")
                return
            }

            // Clear typing indicator for this sender since they sent a message
            typingService?.clearTypingFor(message.senderId)

            Log.i(TAG, ">>> Processing incoming message: id=${message.id}, sender=${message.senderId}, content=${message.content}")

            // Parse ISO timestamp to millis
            val timestampMillis = parseIsoTimestamp(message.timestamp)

            // Extract attachment metadata if present
            val attachment = message.attachment
            
            // LOGGING FOR DEBUGGING
            if (attachment != null) {
                Log.d(TAG, ">>> Attachment Received: id=${attachment.id}, type=${attachment.type}, filename=${attachment.filename}, size=${attachment.size}, mime=${attachment.mimeType}")
            } else {
                Log.d(TAG, ">>> No attachment in this message (type=${message.messageType})")
            }

            // Determine download status based on file size
            // Auto-download for files ≤ 5MB, manual for larger
            val autoDownloadThreshold = 5 * 1024 * 1024L // 5MB
            val downloadStatus = when {
                attachment == null -> null
                attachment.size <= autoDownloadThreshold -> 
                    com.taha.newraapp.domain.model.DownloadStatus.PENDING.name
                else -> 
                    com.taha.newraapp.domain.model.DownloadStatus.NOT_STARTED.name
            }

            // Create message entity with attachment metadata
            val messageEntity = MessageEntity(
                id = message.id,
                conversationId = message.senderId,  // Group by sender for conversation view
                senderId = message.senderId,
                recipientId = currentUserId,
                content = message.content ?: "",
                type = message.messageType,
                status = MessageStatus.DELIVERED.name,
                timestamp = timestampMillis,
                attachmentId = attachment?.id,
                attachmentFileType = attachment?.type,
                attachmentMimeType = attachment?.mimeType,
                attachmentFileName = attachment?.filename,
                attachmentSize = attachment?.size,
                downloadStatus = downloadStatus
            )

            // Save message to DB (REPLACE handles duplicates)
            messageDao.insertMessage(messageEntity)
            Log.i(TAG, ">>> Successfully saved incoming message to DB: ${message.id}")

            // Queue attachment download only for small files (auto-download)
            if (attachment != null && attachment.size <= autoDownloadThreshold) {
                Log.i(TAG, ">>> Auto-downloading attachment (${attachment.size} bytes): ${attachment.id}")
                attachmentRepository?.downloadAttachment(
                    messageId = message.id,
                    attachmentId = attachment.id,
                    conversationId = message.senderId
                )
            } else if (attachment != null) {
                Log.i(TAG, ">>> Large attachment (${attachment.size} bytes), waiting for manual download: ${attachment.id}")
            }

            // Send delivery confirmation to server
            chatSocketService.sendDeliveryConfirmation(
                messageId = message.id,
                recipientId = currentUserId
            )
            Log.d(TAG, "Sent delivery confirmation for message: ${message.id}")

            // Update conversation with unread count
            updateConversationForIncoming(message, timestampMillis)

        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming message: ${message.id}", e)
        }
    }

    /**
     * Update or create conversation entry for incoming message.
     */
    private suspend fun updateConversationForIncoming(message: ReceivedMessage, timestampMillis: Long) {
        val conversation = ConversationEntity(
            peerId = message.senderId,
            lastMessageContent = message.content ?: "[Media]",
            lastMessageTimestamp = timestampMillis,
            unreadCount = 0, // Dynamic counting now used
            lastMessageSenderId = message.senderId,
            lastMessageStatus = MessageStatus.DELIVERED.name
        )
        messageDao.insertConversation(conversation)
        Log.d(TAG, "Updated conversation: ${message.senderId}")
    }

    /**
     * Parse ISO 8601 timestamp to milliseconds.
     * Example input: "2024-01-01T12:00:00.000Z"
     */
    private fun parseIsoTimestamp(isoString: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(isoString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse timestamp: $isoString, using current time")
            System.currentTimeMillis()
        }
    }

    // ========================================
    // Delivery Confirmation Handling
    // ========================================

    /**
     * Start listening for delivery confirmations from ChatSocketService.
     * When recipient acks our message, update status to DELIVERED.
     */
    fun startDeliveryConfirmationListener() {
        if (deliveryJob?.isActive == true) {
            Log.d(TAG, "Delivery confirmation listener already running")
            return
        }

        Log.d(TAG, "Starting delivery confirmation listener")
        deliveryJob = scope.launch {
            chatSocketService.deliveryConfirmations.collect { confirmation ->
                Log.d(TAG, "Delivery listener received confirmation for: ${confirmation.messageId}")
                handleDeliveryConfirmation(confirmation.messageId)
            }
        }
    }

    /**
     * Stop listening for delivery confirmations.
     */
    fun stopDeliveryListener() {
        Log.d(TAG, "Stopping delivery confirmation listener")
        deliveryJob?.cancel()
        deliveryJob = null
    }

    /**
     * Handle delivery confirmation - update message status to DELIVERED.
     * This is called when the recipient confirms they received our message.
     */
    private suspend fun handleDeliveryConfirmation(messageId: String) {
        try {
            Log.i(TAG, ">>> Processing delivery confirmation for message: $messageId")
            
            // Update message status in Room DB
            // This triggers the Flow to emit, updating UI instantly
            messageDao.updateMessageStatus(messageId, MessageStatus.DELIVERED.name)
            
            Log.i(TAG, ">>> Message status updated to DELIVERED in DB: $messageId")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling delivery confirmation for: $messageId", e)
        }
    }

    // ========================================
    // Seen/Read Confirmation Handling
    // ========================================

    /**
     * Mark all unseen messages from a specific peer as READ.
     * This is called when the user opens a chat room.
     * 
     * Flow:
     * 1. Query DB for unseen message IDs from this peer
     * 2. Update local DB to READ status
     * 3. Send seen confirmation to server for each message
     * 4. Update conversation unread count to 0
     * 
     * @param peerId The ID of the chat partner whose messages we're marking as seen
     */
    suspend fun markMessagesAsSeen(peerId: String) {
        try {
            // Get all unseen message IDs from this peer
            val unseenMessageIds = messageDao.getUnseenMessageIds(peerId)
            
            if (unseenMessageIds.isEmpty()) {
                Log.d(TAG, "No unseen messages to mark as read for peer: $peerId")
                return
            }

            Log.i(TAG, ">>> Marking ${unseenMessageIds.size} messages as seen from peer: $peerId")

            // Update local DB first (batch update for efficiency)
            messageDao.markMessagesAsRead(unseenMessageIds)
            
            // Unread count is automatically updated via dynamic query in Repository


            // Send seen confirmation to server for each message
            // This runs asynchronously - we don't need to wait for ack
            unseenMessageIds.forEach { messageId ->
                chatSocketService.sendSeenConfirmation(
                    messageId = messageId,
                    seenByUserId = currentUserId
                )
            }

            Log.i(TAG, ">>> Successfully marked ${unseenMessageIds.size} messages as seen")
        } catch (e: Exception) {
            Log.e(TAG, "Error marking messages as seen for peer: $peerId", e)
        }
    }

    /**
     * Start listening for seen confirmations from ChatSocketService.
     * When recipient sees our message, update status to READ.
     */
    private var seenJob: Job? = null

    fun startSeenConfirmationListener() {
        if (seenJob?.isActive == true) {
            Log.d(TAG, "Seen confirmation listener already running")
            return
        }

        Log.d(TAG, "Starting seen confirmation listener")
        seenJob = scope.launch {
            chatSocketService.seenConfirmations.collect { confirmation ->
                Log.d(TAG, "Seen listener received confirmation for: ${confirmation.messageId}")
                handleSeenConfirmation(confirmation.messageId)
            }
        }
    }

    /**
     * Stop listening for seen confirmations.
     */
    fun stopSeenListener() {
        Log.d(TAG, "Stopping seen confirmation listener")
        seenJob?.cancel()
        seenJob = null
    }

    /**
     * Handle seen confirmation - update message status to READ.
     * This is called when the recipient confirms they saw our message.
     * Skips update if message is already READ to avoid redundant DB writes.
     */
    private suspend fun handleSeenConfirmation(messageId: String) {
        try {
            // First check current status to avoid redundant updates
            val message = messageDao.getMessageById(messageId)
            if (message == null) {
                Log.w(TAG, "Message not found for seen confirmation: $messageId")
                return
            }
            
            if (message.status == MessageStatus.READ.name) {
                Log.d(TAG, "Message already READ, skipping: $messageId")
                return
            }
            
            Log.i(TAG, ">>> Processing seen confirmation for message: $messageId")
            
            // Update message status in Room DB
            // This triggers the Flow to emit, updating UI instantly
            messageDao.updateMessageStatus(messageId, MessageStatus.READ.name)
            
            Log.i(TAG, ">>> Message status updated to READ in DB: $messageId")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling seen confirmation for: $messageId", e)
        }
    }
}

