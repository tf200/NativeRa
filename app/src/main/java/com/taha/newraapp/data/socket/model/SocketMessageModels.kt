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
 */
@Serializable
data class IncomingMessagePayload(
    val message: SocketMessage
)
