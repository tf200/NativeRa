package com.taha.newraapp.data.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.taha.newraapp.data.local.TokenManager
import com.taha.newraapp.data.local.dao.MessageDao
import com.taha.newraapp.data.local.entities.ConversationEntity
import com.taha.newraapp.data.local.entities.MessageEntity
import com.taha.newraapp.data.socket.ChatSocketService
import com.taha.newraapp.data.socket.MessageSyncService
import com.taha.newraapp.data.socket.SocketManager
import com.taha.newraapp.data.socket.model.ReceivedMessage
import com.taha.newraapp.domain.model.MessageStatus
import com.taha.newraapp.domain.repository.UserRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Foreground service that processes FCM messages when app is in background.
 * 
 * Lifecycle:
 * 1. Started via startForegroundService() with ReceivedMessage in intent
 * 2. Starts foreground with notification (skipping UI implementation for now)
 * 3. Connects to socket if needed
 * 4. Stores message in Room DB
 * 5. Sends delivery acknowledgment
 * 6. Updates conversation
 * 7. Stops self after completion
 */
class MessageProcessingService : Service() {
    
    companion object {
        private const val TAG = "MessageProcessingService"
        private const val EXTRA_MESSAGE = "extra_message_json"
        private const val TIMEOUT_MS = 15_000L // 15 seconds max
        
        /**
         * Start the service to process an FCM message.
         */
        fun start(context: Context, message: ReceivedMessage) {
            val intent = Intent(context, MessageProcessingService::class.java).apply {
                putExtra(EXTRA_MESSAGE, Json.encodeToString(message))
            }
            context.startForegroundService(intent)
        }
    }
    
    private val socketManager: SocketManager by inject()
    private val chatSocketService: ChatSocketService by inject()
    private val messageDao: MessageDao by inject()
    private val messageSyncService: MessageSyncService by inject()
    private val userRepository: UserRepository by inject()
    private val tokenManager: TokenManager by inject()
    private val notificationManager: android.app.NotificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var processingJob: Job? = null
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        
        // CRITICAL: Call startForeground immediately (within 5 seconds) to prevent crash
        val notificationId = 999
        val channelId = "background_processing_service_v2"
        createNotificationChannel(channelId)
        
        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("NewRa")
            .setContentText("asdas")
            .setSmallIcon(com.taha.newraapp.R.drawable.ic_notification)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .build()
            
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(notificationId, notification)
        }
        
        val messageJson = intent?.getStringExtra(EXTRA_MESSAGE)
        if (messageJson == null) {
            Log.e(TAG, "No message data in intent")
            stopSelf() // Use stopSelf without ID when manually stopping
            return START_NOT_STICKY
        }
        
        processingJob = serviceScope.launch {
            try {
                val message = Json.decodeFromString<ReceivedMessage>(messageJson)
                processMessage(message)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message", e)
            } finally {
                stopSelf()
            }
        }
        
        return START_NOT_STICKY
    }
    
    private fun createNotificationChannel(channelId: String) {
        val channel = android.app.NotificationChannel(
            channelId,
            "Background Processing",
            android.app.NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Processing incoming messages"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    private suspend fun processMessage(message: ReceivedMessage) {
        Log.i(TAG, ">>> Processing FCM message in background: ${message.id}")
        
        try {
            // Set timeout to prevent hanging
            val processingResult = withTimeout(TIMEOUT_MS) {
                // Step 1: Ensure socket is connected
                if (!socketManager.isConnected()) {
                    Log.d(TAG, "Connecting to socket...")
                    socketManager.connect()
                    
                    // Wait for connection (up to 5 seconds)
                    var waitTime = 0L
                    while (!socketManager.isConnected() && waitTime < 5000) {
                        delay(100)
                        waitTime += 100
                    }
                    
                    if (!socketManager.isConnected()) {
                        Log.e(TAG, "Failed to connect to socket within timeout")
                        // Store message anyway, will send ack when socket reconnects
                        storeMessageLocally(message)
                        return@withTimeout false // Socket connection failed
                    }
                }
                
                // Step 2: Set current user ID
                val currentUser = userRepository.getCurrentUser()
                if (currentUser == null) {
                    Log.e(TAG, "No current user found")
                    return@withTimeout false // No user
                }
                messageSyncService.setCurrentUserId(currentUser.id)
                
                // Step 3: Store message in Room DB
                val isStored = storeMessageLocally(message)
                
                if (isStored) {
                    // Step 4: Send delivery acknowledgment ONLY if stored successfully
                    Log.d(TAG, "Sending delivery confirmation...")
                    chatSocketService.sendDeliveryConfirmation(
                        messageId = message.id,
                        recipientId = currentUser.id
                    )
                    
                    // Step 5: Update conversation
                    updateConversation(message)
                } else {
                     Log.e(TAG, "Failed to store message, SKIPPING delivery confirmation")
                     return@withTimeout false
                }
                
                true // Success
            }
            
            if (processingResult) {
                Log.i(TAG, "✓ Successfully processed FCM message: ${message.id}")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Message processing timed out: ${message.id}")
            // Store message locally even if timeout
            storeMessageLocally(message)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message: ${message.id}", e)
        }
    }
    
    private suspend fun storeMessageLocally(message: ReceivedMessage): Boolean {
        try {
            // Get current user for recipientId
            val currentUser = userRepository.getCurrentUser() ?: return false
            
            // Parse timestamp
            val timestampMillis = parseIsoTimestamp(message.timestamp)
            
            // Extract attachment metadata
            val attachment = message.attachment
            
            // Determine download status
            val autoDownloadThreshold = 5 * 1024 * 1024L // 5MB
            val downloadStatus = when {
                attachment == null -> null
                attachment.size <= autoDownloadThreshold -> 
                    com.taha.newraapp.domain.model.DownloadStatus.PENDING.name
                else -> 
                    com.taha.newraapp.domain.model.DownloadStatus.NOT_STARTED.name
            }
            
            // Create message entity
            val messageEntity = MessageEntity(
                id = message.id,
                conversationId = message.senderId,
                senderId = message.senderId,
                recipientId = currentUser.id,
                content = message.content ?: "",
                type = message.messageType,
                status = MessageStatus.DELIVERED.name,
                timestamp = timestampMillis,
                attachmentId = attachment?.id,
                attachmentFileType = attachment?.type,
                attachmentMimeType = attachment?.mimeType,
                attachmentFileName = attachment?.filename,
                attachmentSize = attachment?.size,
                downloadStatus = downloadStatus
            )
            
            // Save to database
            messageDao.insertMessage(messageEntity)
            Log.i(TAG, "✓ Message stored locally: ${message.id}")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error storing message locally: ${message.id}", e)
            return false
        }
    }
    
    private suspend fun updateConversation(message: ReceivedMessage) {
        try {
            val timestampMillis = parseIsoTimestamp(message.timestamp)
            
            val conversation = ConversationEntity(
                peerId = message.senderId,
                lastMessageContent = message.content ?: "[Media]",
                lastMessageTimestamp = timestampMillis,
                unreadCount = 0, // Not used - dynamic counting from messages table
                lastMessageSenderId = message.senderId,
                lastMessageStatus = MessageStatus.DELIVERED.name
            )
            messageDao.insertConversation(conversation)
            Log.d(TAG, "✓ Conversation updated: ${message.senderId}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating conversation", e)
        }
    }
    
    private fun parseIsoTimestamp(isoString: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(isoString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse timestamp: $isoString")
            System.currentTimeMillis()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        processingJob?.cancel()
        Log.d(TAG, "Service destroyed")
    }
}
