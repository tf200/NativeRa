package com.taha.newraapp.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val content: String,
    val type: MessageType,
    val status: MessageStatus,
    val timestamp: Long,
    // Attachment fields for media messages
    val attachmentId: String? = null,  // Server attachment ID
    val attachmentLocalPath: String? = null,
    val attachmentFileType: String? = null,  // IMAGE, VIDEO, AUDIO, FILE
    val attachmentMimeType: String? = null,
    val attachmentFileName: String? = null,
    val attachmentSize: Long? = null,
    val downloadStatus: DownloadStatus? = null
)

enum class MessageType {
    text, media
}

enum class MessageStatus {
    PENDING, UPLOADING, SENT, DELIVERED, READ, FAILED
}

/**
 * Download status for attachments.
 */
enum class DownloadStatus {
    NOT_STARTED,  // Large file, waiting for user to initiate
    PENDING,      // Queued for download
    DOWNLOADING,  // Currently downloading
    COMPLETE,     // Downloaded, file available locally
    FAILED        // Download failed, can retry
}
