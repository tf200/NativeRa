package com.taha.newraapp.data.socket.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Message payload for Socket.IO communication.
 * Matches the backend MESSAGE_SCHEMA (excluding encryption for now).
 */
@Serializable
data class SocketMessagePayload(
    @SerialName("message")
    val message: SocketMessage
)

@Serializable
data class SocketMessage(
    @SerialName("id")
    val id: String? = null,
    @SerialName("content")
    val content: String? = null,
    @SerialName("sendTimestamp")
    val sendTimestamp: String,
    @SerialName("attachment")
    val attachment: SocketAttachment? = null,
    @SerialName("metadata")
    val metadata: SocketMessageMetadata,
    @SerialName("receipts")
    val receipts: SocketReceipts? = null
)

@Serializable
data class SocketAttachment(
    @SerialName("id")
    val id: String,
    @SerialName("path")
    val path: String,
    @SerialName("filename")
    val filename: String,
    @SerialName("type")
    val type: String, // "audio", "video", "picture", "file"
    @SerialName("size")
    val size: Long,
    @SerialName("duration")
    val duration: Long,
    @SerialName("mimeType")
    val mimeType: String,
    @SerialName("height")
    val height: String,
    @SerialName("width")
    val width: String,
    @SerialName("localId")
    val localId: String,
    @SerialName("progress")
    val progress: Int? = null
)

@Serializable
data class SocketMessageMetadata(
    @SerialName("type")
    val type: String, // "text" or "media"
    @SerialName("sender")
    val sender: String,
    @SerialName("senderName")
    val senderName: String,
    @SerialName("receivers")
    val receivers: List<String>,
    @SerialName("deviceId")
    val deviceId: String,
    @SerialName("localId")
    val localId: Long? = null,
    @SerialName("roomId")
    val roomId: String? = null,
    @SerialName("senderLocalId")
    val senderLocalId: Long? = null,
    @SerialName("status")
    val status: String? = null
)

@Serializable
data class SocketReceipts(
    @SerialName("seen")
    val seen: List<String> = emptyList(),
    @SerialName("delivered")
    val delivered: List<String> = emptyList()
)

/**
 * Acknowledgment response from the server after sending a message.
 */
@Serializable
data class MessageAcknowledgment(
    @SerialName("acknowledged")
    val acknowledged: Boolean,
    @SerialName("error")
    val error: String? = null
)

@Serializable
data class MessageAcknowledgedPayload(
    @SerialName("id")
    val id: Long, // localId
    @SerialName("acknowledged")
    val acknowledged: AcknowledgedDetails? = null
)

@Serializable
data class AcknowledgedDetails(
    @SerialName("messageId")
    val messageId: String,
    @SerialName("timestamp")
    val timestamp: String
)

/**
 * Incoming message event payload (when receiving a message from someone else).
 * OLD FORMAT - kept for backward compatibility.
 */
@Serializable
data class IncomingMessagePayload(
    @SerialName("message")
    val message: SocketMessage
)

// ========================================
// New Backend Format (action:message:send)
// ========================================

/**
 * Payload wrapper for incoming messages from action:message:send event.
 * This is the new simplified format from the backend.
 */
@Serializable
data class ReceivedMessagePayload(
    @SerialName("message")
    val message: ReceivedMessage
)

/**
 * Incoming message structure matching the backend payload.
 * 
 * Example payload:
 * ```json
 * {
 *   "message": {
 *     "id": "uuid",
 *     "content": "Hello!",
 *     "messageType": "text",
 *     "senderId": "user-uuid",
 *     "receivers": ["recipient-uuid"],
 *     "attachmentId": null,
 *     "timestamp": "2024-01-01T12:00:00.000Z"
 *   }
 * }
 * ```
 */
@Serializable
data class ReceivedMessage(
    @SerialName("id")
    val id: String,
    @SerialName("content")
    val content: String? = null,
    @SerialName("messageType")
    val messageType: String, // "text" or "media"
    @SerialName("senderId")
    val senderId: String,
    @SerialName("receivers")
    val receivers: List<String>,
    @SerialName("attachment")
    val attachment: MessageAttachment? = null,
    @SerialName("timestamp")
    val timestamp: String // ISO 8601 format
) {
    companion object {
        /**
         * Parse flattened FCM data payload into ReceivedMessage.
         * 
         * FCM Data Payload Format (flattened):
         * - Text Message: type, messageId, senderId, content, messageType, timestamp
         * - Media Message: type, messageId, senderId, content, messageType, timestamp,
         *                  attachmentId, attachmentType, attachmentFilename
         *
         * @param data Map from RemoteMessage.data
         * @return ReceivedMessage object or null if required fields are missing
         */
        fun fromFcmData(data: Map<String, String>): ReceivedMessage? {
            // Validate required fields
            val messageId = data["messageId"] ?: return null
            val senderId = data["senderId"] ?: return null
            val messageType = data["messageType"] ?: return null
            val timestamp = data["timestamp"] ?: return null
            
            // Parse attachment if present (media messages)
            val attachment = if (!data["attachmentId"].isNullOrBlank()) {
                MessageAttachment(
                    id = data["attachmentId"]!!,
                    type = data["attachmentType"] ?: "FILE",
                    filename = data["attachmentFilename"] ?: "attachment",
                    mimeType = "application/octet-stream", // Not provided by FCM, will be fetched on download
                    size = 0L // Not provided by FCM, will be fetched on download
                )
            } else null
            
            return ReceivedMessage(
                id = messageId,
                content = data["content"], // Can be null for media-only messages
                messageType = messageType,
                senderId = senderId,
                receivers = emptyList(), // Not provided in FCM payload
                attachment = attachment,
                timestamp = timestamp
            )
        }
    }
}

/**
 * Attachment object for both sending and receiving messages.
 */
@Serializable
data class MessageAttachment(
    @SerialName("id")
    val id: String,
    @SerialName("type")
    val type: String,        // "IMAGE", "VIDEO", "AUDIO", "FILE"
    @SerialName("filename")
    val filename: String,
    @SerialName("mimeType")
    val mimeType: String,
    @SerialName("size")
    val size: Long
)

// ========================================
// Delivery Acknowledgment (request:message:delivered)
// Sent BY US when we receive a message
// ========================================

/**
 * Payload to send when we receive a message.
 * Notifies the server (and original sender) that we got their message.
 * 
 * Event: request:message:delivered (we send this)
 */
@Serializable
data class MessageDeliveryPayload(
    @SerialName("messageId")
    val messageId: String,
    @SerialName("delivered")
    val delivered: DeliveredInfo
)

@Serializable
data class DeliveredInfo(
    @SerialName("to")
    val to: String,           // Your user ID (the recipient)
    @SerialName("timestamp")
    val timestamp: String     // ISO 8601 timestamp of when you received it
)

// ========================================
// Delivery Confirmation (action:message:delivered)
// Received BY US when recipient acks our message
// ========================================

/**
 * Payload received when recipient confirms they got our message.
 * Server sends this to the ORIGINAL SENDER after recipient acks.
 * 
 * Event: action:message:delivered (we receive this)
 * 
 * Example payload:
 * ```json
 * {
 *   "messageId": "uuid",
 *   "delivered": {
 *     "to": "recipient-user-id",
 *     "timestamp": "2024-01-01T12:00:00.000Z"
 *   }
 * }
 * ```
 */
@Serializable
data class DeliveryConfirmation(
    @SerialName("messageId")
    val messageId: String,
    @SerialName("delivered")
    val delivered: DeliveryReceipt
)

@Serializable
data class DeliveryReceipt(
    @SerialName("to")
    val to: String,           // The recipient who received the message
    @SerialName("timestamp")
    val timestamp: String     // When they received it
)

// ========================================
// Seen Confirmation (action:message:seen)
// Received BY US when recipient reads our message
// ========================================

/**
 * Payload received when recipient confirms they saw/read our message.
 * Server sends this to the ORIGINAL SENDER after recipient marks as seen.
 * 
 * Event: action:message:seen (we receive this)
 * 
 * Example payload:
 * ```json
 * {
 *   "messageId": "uuid",
 *   "seen": {
 *     "by": "reader-user-id",
 *     "timestamp": "2024-01-01T12:00:00.000Z"
 *   }
 * }
 * ```
 */
@Serializable
data class SeenConfirmation(
    @SerialName("messageId")
    val messageId: String,
    @SerialName("seen")
    val seen: SeenReceipt
)

@Serializable
data class SeenReceipt(
    @SerialName("by")
    val by: String,           // The user who saw the message
    @SerialName("timestamp")
    val timestamp: String     // When they saw it
)

// ========================================
// Call Socket Events
// ========================================

/**
 * Request payload for accepting a call.
 * Event: request:call:accept (we send this)
 */
@Serializable
data class CallAcceptPayload(
    @SerialName("callId")
    val callId: String,
    @SerialName("roomId")
    val roomId: String
)

/**
 * Request payload for rejecting a call.
 * Event: request:call:reject (we send this)
 */
@Serializable
data class CallRejectPayload(
    @SerialName("callId")
    val callId: String,
    @SerialName("reason")
    val reason: String? = null  // "busy", "declined", etc.
)

/**
 * Request payload for ending a call.
 * Event: request:call:end (we send this)
 */
@Serializable
data class CallEndPayload(
    @SerialName("callId")
    val callId: String,
    @SerialName("reason")
    val reason: String? = null  // "hangup", "network_error", etc.
)

/**
 * Action payload received when callee accepts a call.
 * Event: action:call:accepted (we receive this)
 */
@Serializable
data class CallAcceptedEvent(
    @SerialName("type")
    val type: String = "call_accepted",
    @SerialName("callId")
    val callId: String,
    @SerialName("roomId")
    val roomId: String,
    @SerialName("acceptedBy")
    val acceptedBy: String  // User ID who accepted
)

/**
 * Action payload received when callee rejects a call.
 * Event: action:call:rejected (we receive this)
 */
@Serializable
data class CallRejectedEvent(
    @SerialName("type")
    val type: String = "call_rejected",
    @SerialName("callId")
    val callId: String,
    @SerialName("rejectedBy")
    val rejectedBy: String,  // User ID who rejected
    @SerialName("reason")
    val reason: String? = null
)

/**
 * Action payload received when call is ended.
 * Event: action:call:ended (we receive this)
 */
@Serializable
data class CallEndedEvent(
    @SerialName("type")
    val type: String = "call_ended",
    @SerialName("callId")
    val callId: String,
    @SerialName("endedBy")
    val endedBy: String,  // User ID who ended the call
    @SerialName("reason")
    val reason: String? = null
)

