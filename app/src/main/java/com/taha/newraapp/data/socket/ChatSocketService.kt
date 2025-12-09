package com.taha.newraapp.data.socket

import android.util.Log
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
        const val EVENT_ACTION_MESSAGE_SEND = "action:message:send"
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

    // Flow for incoming messages
    private val _incomingMessages = MutableSharedFlow<SocketMessagePayload>(replay = 0)
    val incomingMessages: SharedFlow<SocketMessagePayload> = _incomingMessages.asSharedFlow()

    // Flow for message acknowledgments
    private val _messageAcknowledged = MutableSharedFlow<MessageAckResult>(replay = 0)
    val messageAcknowledged: SharedFlow<MessageAckResult> = _messageAcknowledged.asSharedFlow()

    init {
        registerEventHandlers()
    }

    /**
     * Register handlers for incoming socket events.
     */
    private fun registerEventHandlers() {
        // Handle incoming messages from other users
        socketManager.on(EVENT_ACTION_MESSAGE_SEND) { data ->
            try {
                val jsonStr = data.toString()
                Log.d(TAG, "Received message: $jsonStr")
                val payload = json.decodeFromString<SocketMessagePayload>(jsonStr)
                _incomingMessages.tryEmit(payload)
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
    }

    /**
     * Send a text message to one or more recipients.
     *
     * @param content The message text
     * @param senderId The sender's user ID
     * @param senderName The sender's display name
     * @param receiverIds List of recipient user IDs
     * @param deviceId The sender's device ID
     * @param roomId Optional room/conversation ID
     * @return The local ID used for tracking this message
     */
    fun sendTextMessage(
        content: String,
        senderId: String,
        senderName: String,
        receiverIds: List<String>,
        deviceId: String,
        roomId: String? = null
    ): Long {
        val localId = localIdCounter.incrementAndGet()
        val timestamp = getCurrentTimestamp()

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

    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
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
