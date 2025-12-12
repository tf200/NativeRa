package com.taha.newraapp.data.socket

import android.util.Log
import com.taha.newraapp.data.socket.model.DeliveredInfo
import com.taha.newraapp.data.socket.model.DeliveryConfirmation
import com.taha.newraapp.data.socket.model.MessageDeliveryPayload
import com.taha.newraapp.data.socket.model.SeenConfirmation
import com.taha.newraapp.data.socket.model.ReceivedMessage
import com.taha.newraapp.data.socket.model.ReceivedMessagePayload
import com.taha.newraapp.data.socket.model.SocketMessage
import com.taha.newraapp.data.socket.model.SocketMessageMetadata
import com.taha.newraapp.data.socket.model.SocketMessagePayload
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Handles chat-related Socket.IO events.
 * Responsible for sending messages and processing incoming message events.
 */
class ChatSocketService(
    private val socketManager: SocketManager
) {
    companion object {
        private const val TAG = "ChatSocketService"
        
        // Event names
        const val EVENT_REQUEST_MESSAGE_SEND = "request:message:send"
        const val EVENT_REQUEST_MESSAGE_DELIVERED = "request:message:delivered"
        const val EVENT_REQUEST_MESSAGE_SEEN = "request:message:seen"
        const val EVENT_ACTION_MESSAGE_SEND = "action:message:send"
        const val EVENT_ACTION_MESSAGE_DELIVERED = "action:message:delivered"
        const val EVENT_ACTION_MESSAGE_SEEN = "action:message:seen"
        const val EVENT_ACTION_MESSAGE_ACKNOWLEDGED = "action:message:acknowledged"
    }

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    // Local ID counter for tracking pending messages
    private val localIdCounter = AtomicLong(System.currentTimeMillis())

    // Pending messages waiting for acknowledgment
    private val _pendingMessages = MutableStateFlow<Map<Long, PendingMessage>>(emptyMap())
    val pendingMessages: StateFlow<Map<Long, PendingMessage>> = _pendingMessages.asStateFlow()

    // Flow for incoming messages (new backend format)
    private val _incomingMessages = MutableSharedFlow<ReceivedMessage>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val incomingMessages: SharedFlow<ReceivedMessage> = _incomingMessages.asSharedFlow()

    // Flow for message acknowledgments
    private val _messageAcknowledged = MutableSharedFlow<MessageAckResult>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val messageAcknowledged: SharedFlow<MessageAckResult> = _messageAcknowledged.asSharedFlow()

    // Flow for delivery confirmations (when recipient acks our message)
    private val _deliveryConfirmations = MutableSharedFlow<DeliveryConfirmation>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val deliveryConfirmations: SharedFlow<DeliveryConfirmation> = _deliveryConfirmations.asSharedFlow()

    // Flow for seen confirmations (when recipient reads our message)
    private val _seenConfirmations = MutableSharedFlow<SeenConfirmation>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val seenConfirmations: SharedFlow<SeenConfirmation> = _seenConfirmations.asSharedFlow()

    init {
        registerEventHandlers()
    }

    /**
     * Register handlers for incoming socket events.
     */
    private fun registerEventHandlers() {
        // Handle incoming messages from other users
        // Payload format: { message: { id, content?, messageType, senderId, receivers, attachmentId?, timestamp } }
        socketManager.on(EVENT_ACTION_MESSAGE_SEND) { data ->
            try {
                val jsonStr = data.toString()
                Log.d(TAG, "Received message event: $jsonStr")
                val payload = json.decodeFromString<ReceivedMessagePayload>(jsonStr)
                Log.d(TAG, "Parsed message payload: ${payload.message.id}, content=${payload.message.content}")
                
                val emitted = _incomingMessages.tryEmit(payload.message)
                if (emitted) {
                    Log.d(TAG, "Successfully emitted message to flow: ${payload.message.id}")
                } else {
                    Log.e(TAG, "Failed to emit message to flow (buffer full?): ${payload.message.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing incoming message", e)
            }
        }

        // Handle message acknowledgments
        socketManager.on(EVENT_ACTION_MESSAGE_ACKNOWLEDGED) { data ->
            try {
                val jsonObj = data as? JSONObject ?: JSONObject(data.toString())
                val localId = jsonObj.optLong("id", -1)
                val acknowledged = jsonObj.optJSONObject("acknowledged")
                
                if (localId != -1L && acknowledged != null) {
                    val messageId = acknowledged.optString("messageId")
                    val timestamp = acknowledged.optString("timestamp")
                    
                    Log.d(TAG, "Message acknowledged: localId=$localId, messageId=$messageId")
                    
                    // Remove from pending and emit success
                    removePendingMessage(localId)
                    _messageAcknowledged.tryEmit(
                        MessageAckResult.Success(localId, messageId, timestamp)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing message acknowledgment", e)
            }
        }

        // Handle delivery confirmations (recipient acked our message)
        // Payload: { messageId, delivered: { to, timestamp } }
        socketManager.on(EVENT_ACTION_MESSAGE_DELIVERED) { data ->
            try {
                val jsonStr = data.toString()
                Log.d(TAG, "Received delivery confirmation event: $jsonStr")
                val confirmation = json.decodeFromString<DeliveryConfirmation>(jsonStr)
                Log.d(TAG, "Parsed delivery confirmation: msgId=${confirmation.messageId}, to=${confirmation.delivered.to}")

                val emitted = _deliveryConfirmations.tryEmit(confirmation)
                if (emitted) {
                    Log.d(TAG, "Successfully emitted delivery confirmation: ${confirmation.messageId}")
                } else {
                    Log.e(TAG, "Failed to emit delivery confirmation (buffer full?): ${confirmation.messageId}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing delivery confirmation", e)
            }
        }

        // Handle seen confirmations (recipient read our message)
        // Payload: { messageId, seen: { by, timestamp } }
        socketManager.on(EVENT_ACTION_MESSAGE_SEEN) { data ->
            try {
                val jsonStr = data.toString()
                Log.d(TAG, "Received seen confirmation event: $jsonStr")
                val confirmation = json.decodeFromString<SeenConfirmation>(jsonStr)
                Log.d(TAG, "Parsed seen confirmation: msgId=${confirmation.messageId}, by=${confirmation.seen.by}")

                val emitted = _seenConfirmations.tryEmit(confirmation)
                if (emitted) {
                    Log.d(TAG, "Successfully emitted seen confirmation: ${confirmation.messageId}")
                } else {
                    Log.e(TAG, "Failed to emit seen confirmation (buffer full?): ${confirmation.messageId}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing seen confirmation", e)
            }
        }
    }

    /**
     * Send a text message to one or more recipients.
     * OLD METHOD - kept for backward compatibility.
     * @deprecated Use sendMessage() instead for new code
     */
    @Deprecated("Use sendMessage() instead", ReplaceWith("sendMessage()"))
    fun sendTextMessage(
        content: String,
        senderId: String,
        senderName: String,
        receiverIds: List<String>,
        deviceId: String,
        roomId: String? = null
    ): Long {
        val localId = localIdCounter.incrementAndGet()
        val timestamp = getUtcTimestamp()

        val payload = SocketMessagePayload(
            message = SocketMessage(
                content = content,
                sendTimestamp = timestamp,
                metadata = SocketMessageMetadata(
                    type = "text",
                    sender = senderId,
                    senderName = senderName,
                    receivers = receiverIds,
                    deviceId = deviceId,
                    localId = localId,
                    roomId = roomId,
                    status = "sending"
                )
            )
        )

        // Track as pending
        addPendingMessage(localId, payload)

        // Send via socket with acknowledgment
        try {
            val jsonPayload = json.encodeToString(payload)
            val jsonObject = JSONObject(jsonPayload)
            
            Log.d(TAG, "Sending message: localId=$localId")
            
            socketManager.emit(EVENT_REQUEST_MESSAGE_SEND, jsonObject) { ackArgs ->
                handleSendAcknowledgment(localId, ackArgs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            removePendingMessage(localId)
            _messageAcknowledged.tryEmit(
                MessageAckResult.Failure(localId, e.message ?: "Failed to send message")
            )
        }

        return localId
    }

    /**
     * Send a message using the new backend interface.
     * Matches SendMessagePayload interface exactly.
     * 
     * @param messageId Client-generated UUID for the message
     * @param content Message text (required for 'text', optional for 'media')
     * @param messageType Either "text" or "media"
     * @param senderId Your user ID (must match socket.data.userId)
     * @param receiverIds Array of recipient user IDs
     * @param attachmentId Optional, only for media messages
     * @param onAck Callback with (success: Boolean, error: String?)
     */
    fun sendMessage(
        messageId: String,
        content: String,
        messageType: String,
        senderId: String,
        receiverIds: List<String>,
        attachmentId: String? = null,
        onAck: (success: Boolean, error: String?) -> Unit
    ) {
        val timestamp = getUtcTimestamp()

        // Build payload matching backend SendMessagePayload interface
        val messageObj = JSONObject().apply {
            put("id", messageId)
            if (content.isNotBlank()) put("content", content)
            put("messageType", messageType)
            put("senderId", senderId)
            put("receivers", org.json.JSONArray(receiverIds))
            if (!attachmentId.isNullOrBlank()) put("attachmentId", attachmentId)
            put("timestamp", timestamp)
            // Note: encryption is optional and will be added later
        }

        val payload = JSONObject().apply {
            put("message", messageObj)
        }

        Log.d(TAG, "Sending message: id=$messageId, type=$messageType")

        try {
            socketManager.emit(EVENT_REQUEST_MESSAGE_SEND, payload) { ackArgs ->
                handleNewSendAck(messageId, ackArgs, onAck)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message: ${e.message}")
            onAck(false, e.message ?: "Failed to send message")
        }
    }

    /**
     * Send delivery confirmation to server when we receive a message.
     * This notifies the sender that their message was successfully delivered to us.
     * 
     * @param messageId The UUID of the message that was delivered
     * @param recipientId Our user ID (the one who received the message)
     */
    fun sendDeliveryConfirmation(
        messageId: String,
        recipientId: String
    ) {
        val timestamp = getUtcTimestamp()

        // Build payload matching backend MessageDeliveryPayload interface
        val deliveredObj = JSONObject().apply {
            put("to", recipientId)
            put("timestamp", timestamp)
        }

        val payload = JSONObject().apply {
            put("messageId", messageId)
            put("delivered", deliveredObj)
        }

        Log.d(TAG, "Sending delivery confirmation: messageId=$messageId, to=$recipientId")

        try {
            socketManager.emit(EVENT_REQUEST_MESSAGE_DELIVERED, payload) { ackArgs ->
                handleDeliveryAck(messageId, ackArgs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending delivery confirmation: ${e.message}")
        }
    }

    /**
     * Handle ACK for delivery confirmation.
     */
    private fun handleDeliveryAck(messageId: String, ackArgs: Array<Any>) {
        try {
            if (ackArgs.isEmpty()) {
                Log.w(TAG, "Empty acknowledgment for delivery: $messageId")
                return
            }

            val ackData = ackArgs[0]
            val jsonObj = when (ackData) {
                is JSONObject -> ackData
                else -> JSONObject(ackData.toString())
            }

            val acknowledged = jsonObj.optBoolean("acknowledged", false)
            if (acknowledged) {
                Log.d(TAG, "Delivery confirmation acknowledged: $messageId")
            } else {
                val error = jsonObj.optString("error", "Unknown error")
                Log.w(TAG, "Delivery confirmation not acknowledged: $messageId, error: $error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling delivery acknowledgment: ${e.message}")
        }
    }

    /**
     * Send seen confirmation to server when we view/read a message.
     * This notifies the sender that their message was seen by us.
     * 
     * @param messageId The UUID of the message that was seen
     * @param seenByUserId Our user ID (the one who saw the message)
     */
    fun sendSeenConfirmation(
        messageId: String,
        seenByUserId: String
    ) {
        val timestamp = getUtcTimestamp()

        // Build payload matching backend SeenConfirmation interface
        val seenObj = JSONObject().apply {
            put("by", seenByUserId)
            put("timestamp", timestamp)
        }

        val payload = JSONObject().apply {
            put("messageId", messageId)
            put("seen", seenObj)
        }

        Log.d(TAG, "Sending seen confirmation: messageId=$messageId, by=$seenByUserId")

        try {
            socketManager.emit(EVENT_REQUEST_MESSAGE_SEEN, payload) { ackArgs ->
                handleSeenAck(messageId, ackArgs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending seen confirmation: ${e.message}")
        }
    }

    /**
     * Handle ACK for seen confirmation.
     */
    private fun handleSeenAck(messageId: String, ackArgs: Array<Any>) {
        try {
            if (ackArgs.isEmpty()) {
                Log.w(TAG, "Empty acknowledgment for seen: $messageId")
                return
            }

            val ackData = ackArgs[0]
            val jsonObj = when (ackData) {
                is JSONObject -> ackData
                else -> JSONObject(ackData.toString())
            }

            val acknowledged = jsonObj.optBoolean("acknowledged", false)
            if (acknowledged) {
                Log.d(TAG, "Seen confirmation acknowledged: $messageId")
            } else {
                val error = jsonObj.optString("error", "Unknown error")
                Log.w(TAG, "Seen confirmation not acknowledged: $messageId, error: $error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling seen acknowledgment: ${e.message}")
        }
    }

    /**
     * Handle ACK for the new sendMessage method.
     */
    private fun handleNewSendAck(
        messageId: String, 
        ackArgs: Array<Any>, 
        onAck: (Boolean, String?) -> Unit
    ) {
        try {
            if (ackArgs.isEmpty()) {
                Log.w(TAG, "Empty acknowledgment for message: $messageId")
                onAck(false, "Empty acknowledgment from server")
                return
            }

            val ackData = ackArgs[0]
            val jsonObj = when (ackData) {
                is JSONObject -> ackData
                else -> JSONObject(ackData.toString())
            }

            val acknowledged = jsonObj.optBoolean("acknowledged", false)
            val error = jsonObj.optString("error", "")

            if (acknowledged) {
                Log.d(TAG, "Message acknowledged: $messageId")
                onAck(true, null)
            } else {
                val errorMsg = if (error.isNotBlank()) error else "Server did not acknowledge"
                Log.e(TAG, "Message not acknowledged: $messageId, error: $errorMsg")
                onAck(false, errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling acknowledgment: ${e.message}")
            onAck(false, e.message ?: "Error processing acknowledgment")
        }
    }

    /**
     * Handle the immediate acknowledgment from the socket emit.
     */
    private fun handleSendAcknowledgment(localId: Long, ackArgs: Array<Any>) {
        try {
            if (ackArgs.isEmpty()) {
                Log.w(TAG, "Empty acknowledgment for localId=$localId")
                return
            }

            val ackData = ackArgs[0]
            val jsonObj = when (ackData) {
                is JSONObject -> ackData
                else -> JSONObject(ackData.toString())
            }

            val acknowledged = jsonObj.optBoolean("acknowledged", false)
            val error = jsonObj.optString("error", null)

            if (!acknowledged && !error.isNullOrBlank()) {
                Log.e(TAG, "Message send failed: localId=$localId, error=$error")
                removePendingMessage(localId)
                _messageAcknowledged.tryEmit(
                    MessageAckResult.Failure(localId, error)
                )
            } else if (acknowledged) {
                Log.d(TAG, "Message send acknowledged: localId=$localId")
                // The actual message ID will come via EVENT_ACTION_MESSAGE_ACKNOWLEDGED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling send acknowledgment", e)
        }
    }

    private fun addPendingMessage(localId: Long, payload: SocketMessagePayload) {
        _pendingMessages.value = _pendingMessages.value + (localId to PendingMessage(
            localId = localId,
            payload = payload,
            timestamp = System.currentTimeMillis()
        ))
    }

    private fun removePendingMessage(localId: Long) {
        _pendingMessages.value = _pendingMessages.value - localId
    }

    private fun getUtcTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}

/**
 * Represents a message waiting for server acknowledgment.
 */
data class PendingMessage(
    val localId: Long,
    val payload: SocketMessagePayload,
    val timestamp: Long
)

/**
 * Result of a message send operation.
 */
sealed class MessageAckResult {
    data class Success(
        val localId: Long,
        val messageId: String,
        val timestamp: String
    ) : MessageAckResult()

    data class Failure(
        val localId: Long,
        val error: String
    ) : MessageAckResult()
}
