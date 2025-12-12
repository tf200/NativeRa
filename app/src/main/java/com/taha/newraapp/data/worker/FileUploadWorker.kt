package com.taha.newraapp.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.taha.newraapp.R
import com.taha.newraapp.data.local.dao.MessageDao
import com.taha.newraapp.data.local.dao.PendingUploadDao
import com.taha.newraapp.data.local.entities.UploadStatus
import com.taha.newraapp.data.network.AttachmentApi
import com.taha.newraapp.data.network.AuthenticatedApiExecutor
import com.taha.newraapp.data.network.ConfirmUploadBody
import com.taha.newraapp.data.network.RequestUploadBody
import com.taha.newraapp.data.repository.AttachmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.IOException

/**
 * WorkManager worker for uploading files to S3.
 * 
 * Flow:
 * 1. Get presigned URL from server
 * 2. Upload file to S3 with progress tracking
 * 3. Confirm upload with server
 * 4. Update message with attachment ID
 * 5. MessageSyncService will automatically pick up and send the message
 */
class FileUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {
    
    companion object {
        private const val TAG = "FileUploadWorker"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "upload_channel"
        const val KEY_MESSAGE_ID = "message_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_TOTAL = "total"
    }
    
    private val pendingUploadDao: PendingUploadDao by inject()
    private val messageDao: MessageDao by inject()
    private val attachmentApi: AttachmentApi by inject()
    private val apiExecutor: AuthenticatedApiExecutor by inject()
    private val okHttpClient: OkHttpClient by inject()
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val messageId = inputData.getString(KEY_MESSAGE_ID)
            ?: return@withContext Result.failure()
        
        Log.d(TAG, "Starting upload for message: $messageId")
        
        val upload = pendingUploadDao.getByMessageId(messageId)
            ?: return@withContext Result.failure()
        
        try {
            // Set foreground for long-running upload
            setForeground(getForegroundInfo())
            
            // Step 1: Request presigned URL
            Log.d(TAG, "Step 1: Requesting presigned URL")
            val urlResponse = apiExecutor.executeWithBearer { auth ->
                attachmentApi.requestUpload(auth, RequestUploadBody(
                    filename = upload.fileName,
                    mimeType = upload.mimeType,
                    fileType = upload.fileType,
                    size = upload.fileSize
                ))
            }
            
            // Update with presigned URL info
            pendingUploadDao.updateWithUploadUrl(
                messageId = messageId,
                uploadUrl = urlResponse.uploadUrl,
                fileKey = urlResponse.key,
                expiresAt = urlResponse.expiresAt
            )
            Log.d(TAG, "Got presigned URL, key: ${urlResponse.key}")
            
            // Step 2: Upload to S3
            Log.d(TAG, "Step 2: Uploading to S3")
            uploadToS3(
                filePath = upload.localFilePath,
                uploadUrl = urlResponse.uploadUrl,
                mimeType = upload.mimeType,
                messageId = messageId,
                fileSize = upload.fileSize
            )
            Log.d(TAG, "Upload to S3 complete")
            
            // Step 3: Confirm upload
            Log.d(TAG, "Step 3: Confirming upload")
            pendingUploadDao.updateProgress(messageId, UploadStatus.CONFIRMING, upload.fileSize)
            
            val confirmResponse = apiExecutor.executeWithBearer { auth ->
                attachmentApi.confirmUpload(auth, ConfirmUploadBody(
                    key = urlResponse.key,
                    filename = upload.fileName,
                    mimeType = upload.mimeType,
                    fileType = upload.fileType,
                    size = upload.fileSize
                ))
            }
            Log.d(TAG, "Upload confirmed, attachment ID: ${confirmResponse.id}")
            
            // Step 4: Update message with attachment ID
            messageDao.updateAttachmentId(messageId, confirmResponse.id)
            pendingUploadDao.markComplete(messageId, confirmResponse.id)
            
            // Message is now ready to be sent by MessageSyncService
            // It's already in PENDING status, so it will be picked up automatically
            Log.d(TAG, "Upload complete, message ready for send: $messageId")
            
            Result.success()
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error during upload: ${e.message}")
            handleRetry(messageId, e.message ?: "Network error")
        } catch (e: Exception) {
            Log.e(TAG, "Error during upload: ${e.message}")
            pendingUploadDao.markFailed(messageId, e.message ?: "Upload failed")
            Result.failure()
        }
    }
    
    /**
     * Upload file to S3 using presigned URL.
     * Reports progress via WorkManager's setProgress.
     */
    private suspend fun uploadToS3(
        filePath: String,
        uploadUrl: String,
        mimeType: String,
        messageId: String,
        fileSize: Long
    ) {
        val file = File(filePath)
        if (!file.exists()) {
            throw IOException("File not found: $filePath")
        }
        
        // Create progress-tracking request body
        val requestBody = ProgressRequestBody(
            file = file,
            contentType = mimeType.toMediaType(),
            onProgress = { bytesWritten ->
                // Update database
                pendingUploadDao.updateProgress(messageId, UploadStatus.UPLOADING, bytesWritten)
                
                // Update WorkManager progress
                setProgressAsync(workDataOf(
                    KEY_PROGRESS to bytesWritten,
                    KEY_TOTAL to fileSize
                ))
            }
        )
        
        val request = Request.Builder()
            .url(uploadUrl)
            .put(requestBody)
            .header("Content-Type", mimeType)
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("S3 upload failed: ${response.code} ${response.message}")
        }
    }
    
    private suspend fun handleRetry(messageId: String, error: String): Result {
        return if (runAttemptCount < 5) {
            Log.d(TAG, "Retrying upload, attempt ${runAttemptCount + 1}")
            pendingUploadDao.markFailed(messageId, "Retry: $error")
            Result.retry()
        } else {
            Log.e(TAG, "Max retries reached, failing upload")
            pendingUploadDao.markFailed(messageId, error)
            Result.failure()
        }
    }
    
    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Uploading file...")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()
        
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Uploads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows file upload progress"
            }
            
            val notificationManager = applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

/**
 * RequestBody that reports upload progress.
 */
class ProgressRequestBody(
    private val file: File,
    private val contentType: okhttp3.MediaType,
    private val onProgress: suspend (bytesWritten: Long) -> Unit
) : RequestBody() {
    
    override fun contentType() = contentType
    
    override fun contentLength() = file.length()
    
    override fun writeTo(sink: BufferedSink) {
        val source = file.source()
        var totalBytesWritten = 0L
        val buffer = okio.Buffer()
        val bufferSize = 8 * 1024L // 8KB chunks
        
        var bytesRead: Long
        while (source.read(buffer, bufferSize).also { bytesRead = it } != -1L) {
            sink.write(buffer, bytesRead)
            totalBytesWritten += bytesRead
            
            // Report progress (using runBlocking to call suspend function)
            kotlinx.coroutines.runBlocking {
                onProgress(totalBytesWritten)
            }
        }
        
        source.close()
    }
}
