package com.taha.newraapp.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.work.*
import com.taha.newraapp.data.local.dao.MessageDao
import com.taha.newraapp.data.local.dao.PendingUploadDao
import com.taha.newraapp.data.local.entities.MessageEntity
import com.taha.newraapp.data.local.entities.PendingUploadEntity
import com.taha.newraapp.data.local.entities.FileType
import com.taha.newraapp.data.local.entities.UploadStatus
import com.taha.newraapp.data.network.AttachmentApi
import com.taha.newraapp.data.network.AuthenticatedApiExecutor
import com.taha.newraapp.data.worker.FileUploadWorker
import com.taha.newraapp.data.worker.FileDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Repository for handling file attachments.
 * 
 * Responsibilities:
 * - Prepare attachments (copy to internal storage, generate thumbnails)
 * - Queue uploads via WorkManager
 * - Track upload progress
 * - Handle download requests (permanent local storage)
 */
class AttachmentRepository(
    private val context: Context,
    private val pendingUploadDao: PendingUploadDao,
    private val messageDao: MessageDao,
    private val attachmentApi: AttachmentApi,
    private val apiExecutor: AuthenticatedApiExecutor,
    private val workManager: WorkManager
) {
    companion object {
        private const val TAG = "AttachmentRepository"
        const val KEY_MESSAGE_ID = "message_id"
    }
    
    // ================================
    // UPLOAD METHODS
    // ================================
    
    /**
     * Observe upload progress for a specific message.
     * Returns progress as 0.0 to 1.0.
     */
    fun observeUploadProgress(messageId: String): Flow<UploadProgress> {
        return pendingUploadDao.observeByMessageId(messageId)
            .map { upload ->
                when {
                    upload == null -> UploadProgress(1f, UploadState.COMPLETE)
                    upload.status == UploadStatus.DONE -> UploadProgress(1f, UploadState.COMPLETE)
                    upload.status == UploadStatus.FAILED -> UploadProgress(0f, UploadState.FAILED, upload.error)
                    upload.fileSize > 0 -> {
                        val progress = upload.uploadedBytes.toFloat() / upload.fileSize.toFloat()
                        UploadProgress(progress, UploadState.UPLOADING)
                    }
                    else -> UploadProgress(0f, UploadState.PENDING)
                }
            }
    }
    
    /**
     * Prepare and queue an attachment for upload.
     * 
     * This is the main entry point for sending attachments:
     * 1. Copies file to internal storage
     * 2. Creates MessageEntity with PENDING status
     * 3. Creates PendingUploadEntity
     * 4. Enqueues WorkManager job
     * 
     * @param uri Content URI of the file to upload
     * @param fileType Display type (IMAGE, VIDEO, AUDIO, FILE) - user's choice
     * @param conversationId The conversation ID (peer's user ID)
     * @param senderId Current user's ID
     * @param recipientId Recipient's user ID
     * @return The message ID of the created message
     */
    suspend fun prepareAttachment(
        uri: Uri,
        fileType: String,
        conversationId: String,
        senderId: String,
        recipientId: String
    ): String = withContext(Dispatchers.IO) {
        // 1. Get file info
        val fileName = getFileName(uri) ?: "attachment_${System.currentTimeMillis()}"
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        
        // 2. Copy file to internal storage
        val localPath = copyToInternalStorage(uri, fileName)
        
        // 3. Get actual file size from the local file (more reliable than ContentResolver)
        val file = File(localPath)
        val fileSize = file.length()
        
        if (fileSize <= 0) {
            throw IllegalStateException("File size is 0 or invalid: $localPath")
        }

        
        // 3. Generate thumbnail for images (TODO: implement BlurHash)
        val thumbnail: String? = null  // Will implement later with BlurHash library
        
        // 4. Generate message ID
        val messageId = UUID.randomUUID().toString()
        
        // 5. Create MessageEntity (visible in chat immediately)
        val message = MessageEntity(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            recipientId = recipientId,
            content = "",  // Media messages have no text content
            type = "media",
            status = "UPLOADING",
            timestamp = System.currentTimeMillis(),
            attachmentLocalPath = localPath,
            attachmentMimeType = mimeType,
            attachmentFileType = fileType,
            attachmentFileName = fileName,
            attachmentThumbnail = thumbnail,
            attachmentSize = fileSize
        )
        messageDao.insertMessage(message)
        Log.d(TAG, "Created message with attachment: $messageId")
        
        // 6. Create PendingUploadEntity
        val pendingUpload = PendingUploadEntity(
            messageId = messageId,
            localFilePath = localPath,
            fileName = fileName,
            mimeType = mimeType,
            fileType = fileType,
            fileSize = fileSize,
            status = UploadStatus.PENDING
        )
        pendingUploadDao.insert(pendingUpload)
        Log.d(TAG, "Created pending upload for: $messageId")
        
        // 7. Enqueue upload worker
        enqueueUpload(messageId)
        
        messageId
    }
    
    /**
     * Retry a failed upload.
     */
    suspend fun retryUpload(messageId: String) {
        val upload = pendingUploadDao.getByMessageId(messageId) ?: return
        
        // Reset status
        pendingUploadDao.update(upload.copy(
            status = UploadStatus.PENDING,
            error = null,
            uploadedBytes = 0
        ))
        
        // Re-enqueue
        enqueueUpload(messageId)
    }
    
    /**
     * Cancel a pending upload.
     */
    suspend fun cancelUpload(messageId: String) {
        // Cancel WorkManager job
        workManager.cancelUniqueWork("upload_$messageId")
        
        // Delete pending upload record
        pendingUploadDao.deleteByMessageId(messageId)
        
        // Update message status to FAILED
        messageDao.updateMessageStatus(messageId, "FAILED")
    }
    
    /**
     * Enqueue a WorkManager job for upload.
     */
    private fun enqueueUpload(messageId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val inputData = workDataOf(KEY_MESSAGE_ID to messageId)
        
        val uploadWork = OneTimeWorkRequestBuilder<FileUploadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .build()
        
        workManager.enqueueUniqueWork(
            "upload_$messageId",
            ExistingWorkPolicy.KEEP,
            uploadWork
        )
        
        Log.d(TAG, "Enqueued upload worker for: $messageId")
    }
    
    // ================================
    // DOWNLOAD METHODS
    // ================================
    
    /**
     * Download an attachment to permanent local storage.
     * 
     * Call this when receiving a message with an attachment or when user
     * explicitly requests to download. Files are stored permanently since
     * server deletes them after 30 days.
     * 
     * @param messageId The message ID containing the attachment
     * @param attachmentId The server attachment ID
     * @param conversationId The conversation ID (for organizing storage)
     */
    fun downloadAttachment(
        messageId: String,
        attachmentId: String,
        conversationId: String
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val inputData = workDataOf(
            FileDownloadWorker.KEY_MESSAGE_ID to messageId,
            FileDownloadWorker.KEY_ATTACHMENT_ID to attachmentId,
            FileDownloadWorker.KEY_CONVERSATION_ID to conversationId
        )
        
        val downloadWork = OneTimeWorkRequestBuilder<FileDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .build()
        
        workManager.enqueueUniqueWork(
            "download_$messageId",
            ExistingWorkPolicy.KEEP,
            downloadWork
        )
        
        Log.d(TAG, "Enqueued download worker for message: $messageId, attachment: $attachmentId")
    }
    
    /**
     * Check if an attachment is already downloaded locally.
     * 
     * @param messageId The message ID
     * @return True if file exists locally
     */
    suspend fun isDownloaded(messageId: String): Boolean = withContext(Dispatchers.IO) {
        val message = messageDao.getMessageById(messageId) ?: return@withContext false
        val localPath = message.attachmentLocalPath ?: return@withContext false
        File(localPath).exists()
    }
    
    /**
     * Get local file path for an attachment.
     * Returns null if not downloaded yet.
     * 
     * @param messageId The message ID
     * @return Local file path or null
     */
    suspend fun getLocalFilePath(messageId: String): String? = withContext(Dispatchers.IO) {
        val message = messageDao.getMessageById(messageId) ?: return@withContext null
        val localPath = message.attachmentLocalPath ?: return@withContext null
        if (File(localPath).exists()) localPath else null
    }
    
    /**
     * Get the attachments directory for a conversation.
     */
    fun getAttachmentsDir(conversationId: String): File {
        val dir = File(context.filesDir, "attachments/$conversationId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    // ================================
    // HELPER METHODS
    // ================================
    
    /**
     * Copy file from content URI to internal storage.
     */
    private fun copyToInternalStorage(uri: Uri, fileName: String): String {
        val uploadsDir = File(context.filesDir, "pending_uploads")
        if (!uploadsDir.exists()) {
            uploadsDir.mkdirs()
        }
        
        val destFile = File(uploadsDir, "${UUID.randomUUID()}_$fileName")
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        
        return destFile.absolutePath
    }
    
    /**
     * Get filename from content URI.
     */
    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
    
    /**
     * Clear all attachment files from internal storage.
     * Call this on sign-out to remove all local files and free up space.
     * 
     * This method:
     * 1. Cancels all pending upload/download WorkManager jobs
     * 2. Deletes all files in the attachments directory
     * 3. Deletes all files in the pending_uploads directory
     * 4. Clears pending uploads from database
     */
    suspend fun clearAllAttachmentFiles() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Clearing all attachment files...")
        
        // 1. Cancel all pending work
        workManager.cancelAllWork()
        
        // 2. Delete attachments directory
        val attachmentsDir = File(context.filesDir, "attachments")
        if (attachmentsDir.exists()) {
            attachmentsDir.deleteRecursively()
            Log.d(TAG, "Deleted attachments directory")
        }
        
        // 3. Delete pending_uploads directory
        val uploadsDir = File(context.filesDir, "pending_uploads")
        if (uploadsDir.exists()) {
            uploadsDir.deleteRecursively()
            Log.d(TAG, "Deleted pending_uploads directory")
        }
        
        // 4. Clear pending uploads from database
        pendingUploadDao.clearAll()
        Log.d(TAG, "Cleared pending uploads from database")
    }

}

/**
 * Upload progress data class for UI observation.
 */
@Immutable
data class UploadProgress(
    val progress: Float,        // 0.0 to 1.0
    val state: UploadState,
    val error: String? = null
)

enum class UploadState {
    PENDING,
    UPLOADING,
    COMPLETE,
    FAILED
}

