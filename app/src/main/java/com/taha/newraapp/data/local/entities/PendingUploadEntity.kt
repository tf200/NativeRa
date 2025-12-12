package com.taha.newraapp.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks file uploads in progress.
 * Links to a MessageEntity via messageId.
 */
@Entity(tableName = "pending_uploads")
data class PendingUploadEntity(
    @PrimaryKey
    val messageId: String,           // Links to MessageEntity.id
    val localFilePath: String,       // Path to local file being uploaded
    val fileName: String,            // Original filename
    val mimeType: String,            // Actual MIME type
    val fileType: String,            // Display type: IMAGE, VIDEO, AUDIO, FILE
    val fileSize: Long,              // File size in bytes
    val status: String,              // PENDING, UPLOADING, CONFIRMING, DONE, FAILED
    val uploadedBytes: Long = 0,     // Progress tracking
    val uploadUrl: String? = null,   // Presigned URL from server
    val fileKey: String? = null,     // S3 key from request-upload
    val attachmentId: String? = null,// Attachment ID after confirm
    val error: String? = null,       // Error message if failed
    val expiresAt: String? = null,   // When presigned URL expires
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Upload status values
 */
object UploadStatus {
    const val PENDING = "PENDING"       // Waiting to start
    const val UPLOADING = "UPLOADING"   // Upload in progress
    const val CONFIRMING = "CONFIRMING" // Confirming with server
    const val DONE = "DONE"             // Complete
    const val FAILED = "FAILED"         // Failed, can retry
}

/**
 * File type values (matches server FILE_TYPE_ENUM)
 */
object FileType {
    const val IMAGE = "IMAGE"
    const val VIDEO = "VIDEO"
    const val AUDIO = "AUDIO"
    const val FILE = "FILE"
}
