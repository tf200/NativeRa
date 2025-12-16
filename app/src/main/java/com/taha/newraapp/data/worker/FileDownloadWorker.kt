package com.taha.newraapp.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.taha.newraapp.data.local.dao.MessageDao
import com.taha.newraapp.data.network.AttachmentApi
import com.taha.newraapp.data.network.AuthenticatedApiExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * WorkManager worker for downloading attachments from S3.
 * 
 * Downloads are stored permanently in app's internal storage since
 * the server deletes files after 30 days.
 * 
 * Flow:
 * 1. Get presigned download URL from server
 * 2. Download file to permanent local storage
 * 3. Update message with local file path
 */
class FileDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {
    
    companion object {
        private const val TAG = "FileDownloadWorker"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "download_channel"
        const val KEY_MESSAGE_ID = "message_id"
        const val KEY_ATTACHMENT_ID = "attachment_id"
        const val KEY_CONVERSATION_ID = "conversation_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_TOTAL = "total"
    }
    
    private val messageDao: MessageDao by inject()
    private val attachmentApi: AttachmentApi by inject()
    private val apiExecutor: AuthenticatedApiExecutor by inject()
    private val okHttpClient: OkHttpClient by inject()
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val messageId = inputData.getString(KEY_MESSAGE_ID)
            ?: return@withContext Result.failure()
        val attachmentId = inputData.getString(KEY_ATTACHMENT_ID)
            ?: return@withContext Result.failure()
        val conversationId = inputData.getString(KEY_CONVERSATION_ID)
            ?: return@withContext Result.failure()
        
        Log.d(TAG, "Starting download for attachment: $attachmentId, message: $messageId")
        
        try {
            // Mark as DOWNLOADING
            messageDao.updateDownloadStatus(messageId, com.taha.newraapp.domain.model.DownloadStatus.DOWNLOADING.name)
            
            // Set foreground for long-running download
            setForeground(getForegroundInfo())
            
            // Step 1: Get presigned download URL
            Log.d(TAG, "Step 1: Getting download URL")
            val downloadInfo = apiExecutor.executeWithBearer { auth ->
                attachmentApi.getDownloadUrl(auth, attachmentId)
            }
            Log.d(TAG, "Got download URL for: ${downloadInfo.filename}")
            
            // Step 2: Create permanent storage directory
            val attachmentsDir = File(
                applicationContext.filesDir,
                "attachments/$conversationId"
            )
            if (!attachmentsDir.exists()) {
                attachmentsDir.mkdirs()
            }
            
            // Use attachmentId + original filename for unique but recognizable name
            val localFile = File(attachmentsDir, "${attachmentId}_${downloadInfo.filename}")
            
            // Skip if already downloaded
            if (localFile.exists() && localFile.length() == downloadInfo.size) {
                Log.d(TAG, "File already exists, updating message path")
                updateMessageWithLocalPath(messageId, localFile.absolutePath, downloadInfo)
                return@withContext Result.success()
            }
            
            // Step 3: Download file
            Log.d(TAG, "Step 2: Downloading file to ${localFile.absolutePath}")
            downloadFile(
                downloadUrl = downloadInfo.downloadUrl,
                destFile = localFile,
                fileSize = downloadInfo.size
            )
            Log.d(TAG, "Download complete")
            
            // Step 4: Update message with local path
            updateMessageWithLocalPath(messageId, localFile.absolutePath, downloadInfo)
            
            Result.success()
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error during download: ${e.message}")
            handleRetry(messageId, e.message ?: "Network error")
        } catch (e: Exception) {
            Log.e(TAG, "Error during download: ${e.message}")
            // Mark as FAILED
            messageDao.updateDownloadStatus(messageId, com.taha.newraapp.domain.model.DownloadStatus.FAILED.name)
            Result.failure()
        }
    }
    
    /**
     * Download file from presigned URL to local storage.
     */
    private fun downloadFile(
        downloadUrl: String,
        destFile: File,
        fileSize: Long
    ) {
        val request = Request.Builder()
            .url(downloadUrl)
            .get()
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("Download failed: ${response.code} ${response.message}")
        }

        response.body.let { body ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L

                body.byteStream().use { input ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Report progress
                        setProgressAsync(workDataOf(
                            KEY_PROGRESS to totalBytesRead,
                            KEY_TOTAL to fileSize
                        ))
                    }
                }
            }
        }
    }
    
    /**
     * Update message entity with local file path and metadata.
     */
    private suspend fun updateMessageWithLocalPath(
        messageId: String,
        localPath: String,
        downloadInfo: com.taha.newraapp.data.network.DownloadResponse
    ) {
        // Get existing message and update with attachment info
        val message = messageDao.getMessageById(messageId) ?: return
        
        val updatedMessage = message.copy(
            attachmentLocalPath = localPath,
            attachmentMimeType = downloadInfo.mimeType,
            attachmentFileType = downloadInfo.fileType,
            attachmentFileName = downloadInfo.filename,
            attachmentSize = downloadInfo.size,
            downloadStatus = com.taha.newraapp.domain.model.DownloadStatus.COMPLETE.name
        )
        
        messageDao.updateMessage(updatedMessage)
        Log.d(TAG, "Updated message $messageId with local path: $localPath")
    }
    
    private suspend fun handleRetry(messageId: String, error: String): Result {
        return if (runAttemptCount < 5) {
            Log.d(TAG, "Retrying download, attempt ${runAttemptCount + 1}")
            Result.retry()
        } else {
            Log.e(TAG, "Max retries reached, failing download: $error")
            // Mark as FAILED so user can manually retry
            messageDao.updateDownloadStatus(messageId, com.taha.newraapp.domain.model.DownloadStatus.FAILED.name)
            Result.failure()
        }
    }
    
    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading file...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()
        
        // Android 14+ requires foreground service type
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "File Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows file download progress"
        }
        
        val notificationManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
