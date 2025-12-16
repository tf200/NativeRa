package com.taha.newraapp.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String, // UUID - client-generated
    val conversationId: String, // Database ID of the peer (used for per-conversation queue)
    val senderId: String,
    val recipientId: String,
    val content: String,
    val type: String, // "text", "media"
    val status: String, // "PENDING", "SENT", "DELIVERED", "READ", "FAILED"
    val timestamp: Long, // UTC timestamp in millis
    
    // Retry tracking for message queue
    val retryCount: Int = 0, // Number of send attempts
    val nextRetryAt: Long = 0L, // When to retry (0 = immediately, >0 = scheduled)
    
    // Attachment fields
    val attachmentId: String? = null,              // Server attachment ID (after upload confirmed)
    val attachmentLocalPath: String? = null,       // Local file path for display
    val attachmentMimeType: String? = null,        // Actual MIME type (image/jpeg, video/mp4, etc.)
    val attachmentFileType: String? = null,        // Display type: IMAGE, VIDEO, AUDIO, FILE
    val attachmentFileName: String? = null,        // Original filename
    val attachmentThumbnail: String? = null,       // BlurHash or base64 thumbnail for images
    val attachmentSize: Long? = null,              // File size in bytes
    val downloadStatus: String? = null             // NOT_STARTED, PENDING, DOWNLOADING, COMPLETE, FAILED
)
