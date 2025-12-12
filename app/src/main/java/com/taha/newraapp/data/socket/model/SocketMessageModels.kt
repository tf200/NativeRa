package com.taha.newraapp.data.socket.model

import kotlinx.serialization.Serializable

/**
 * Message payload for Socket.IO communication.
 * Matches the backend MESSAGE_SCHEMA (excluding encryption for now).
 */
@Serializable
data class SocketMessagePayload(
    val message: SocketMessage
)

@Serializable
data class SocketMessage(
    val id: String? = null,
    val content: String? = null,
    val sendTimestamp: String,
    val attachment: SocketAttachment? = null,
    val metadata: SocketMessageMetadata,
    val receipts: SocketReceipts? = null
)

@Serializable
data class SocketAttachment(
    val id: String,
    val path: String,
    val filename: String,
    val type: String, // "audio", "video", "picture", "file"
    val size: Long,
    val duration: Long,
    val mimeType: String,
    val height: String,
    val width: String,
    val localId: String,
    val progress: Int? = null
)

@Serializable
data class SocketMessageMetadata(
    val type: String, // "text" or "media"
    val sender: String,
    val senderName: String,
    val receivers: List<String>,
    val deviceId: String,
    val localId: Long? = null,
    val roomId: String? = null,
    val senderLocalId: Long? = null,
    val status: String? = null
)

@Serializable
data class SocketReceipts(
    val seen: List<String> = emptyList(),
    val delivered: List<String> = emptyList()
)

/**
 * Acknowledgment response from the server after sending a message.
 */
@Serializable
data class MessageAcknowledgment(
    val acknowledged: Boolean,
    val error: String? = null
)

@Serializable
data class MessageAcknowledgedPayload(
    val id: Long, // localId
    val acknowledged: AcknowledgedDetails? = null
)

@Serializable
data class AcknowledgedDetails(
    val messageId: String,
    val timestamp: String
)

/**
 * Incoming message event payload (when receiving a message from someone else).
 * OLD FORMAT - kept for backward compatibility.
 */
@Serializable
data class IncomingMessagePayload(
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
    val id: String,
    val content: String? = null,
    val messageType: String, // "text" or "media"
    val senderId: String,
    val receivers: List<String>,
    val attachmentId: String? = null,
    val timestamp: String // ISO 8601 format
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
    val messageId: String,
    val delivered: DeliveredInfo
)

@Serializable
data class DeliveredInfo(
    val to: String,           // Your user ID (the recipient)
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
    val messageId: String,
    val delivered: DeliveryReceipt
)

@Serializable
data class DeliveryReceipt(
    val to: String,           // The recipient who received the message
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
    val messageId: String,
    val seen: SeenReceipt
)

@Serializable
data class SeenReceipt(
    val by: String,           // The user who saw the message
    val timestamp: String     // When they saw it
)
