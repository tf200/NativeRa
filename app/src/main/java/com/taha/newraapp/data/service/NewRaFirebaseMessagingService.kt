package com.taha.newraapp.data.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.taha.newraapp.data.local.FcmTokenManager
import com.taha.newraapp.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class NewRaFirebaseMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "NewRaFCMService"
    }

    private val fcmTokenManager: FcmTokenManager by inject()
    private val authRepository: AuthRepository by inject()
    private val notificationManager: MessageNotificationManager by inject()
    private val userRepository: com.taha.newraapp.domain.repository.UserRepository by inject()
    private val fcmMessageHandler: FcmMessageHandler by inject()
    private val powerSyncManager: com.taha.newraapp.data.sync.PowerSyncManager by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            fcmTokenManager.saveToken(token)
            // If user is logged in, we should send the new token to backend
            // For now we just save it locally, the update logic will check stored token
            // Ideally we could check if user is logged in and update immediately
            try {
               authRepository.updateFcmToken(token)
            } catch (e: Exception) {
                // Ignore if not logged in or network fails, will retry on next app launch/login
                e.printStackTrace()
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        // Check if this is a data message
        if (message.data.isNotEmpty()) {
            Log.d(TAG, "FCM Data message received: ${message.data}")
            
            // Check message type
            when (message.data["type"]) {
                "chat_message" -> {
                    handleChatMessage(message.data)
                }
                else -> {
                    Log.w(TAG, "Unknown FCM message type: ${message.data["type"]}")
                }
            }
        }
        
        // Handle notification messages (if any)
        message.notification?.let {
            Log.d(TAG, "FCM Notification: ${it.title} - ${it.body}")
            // We're using data messages only, but log if server sends notification
        }
    }
    
    /**
     * Handle incoming chat message from FCM.
     * Parses flattened FCM data and delegates to FcmMessageHandler.
     */
    private fun handleChatMessage(data: Map<String, String>) {
        serviceScope.launch {
            try {
                // Parse FCM data into ReceivedMessage
                val receivedMessage = com.taha.newraapp.data.socket.model.ReceivedMessage.fromFcmData(data)
                
                if (receivedMessage == null) {
                    Log.e(TAG, "Failed to parse FCM data into ReceivedMessage: $data")
                    return@launch
                }
                
                Log.i(TAG, ">>> Parsed FCM message: id=${receivedMessage.id}, sender=${receivedMessage.senderId}")
                
                // Show notification (will check ActiveChatTracker internally)
                    
                    // Initialize database if needed (for background wakeups)
                    if (powerSyncManager.database == null) {
                        try {
                            Log.d(TAG, "Initializing PowerSync in background (no wait)...")
                            powerSyncManager.initialize(waitForSync = false)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to init PowerSync in background", e)
                        }
                    }

                    // Get sender name
                    val sender = userRepository.getUserById(receivedMessage.senderId)
                    val senderName = sender?.let { "${it.firstName} ${it.lastName}".trim() }
                        ?: "New Message" // Fallback generic title instead of "Unknown"
                    
                    notificationManager.showMessageNotification(
                        messageId = receivedMessage.id,
                        senderId = receivedMessage.senderId,
                        senderName = senderName,
                        messageContent = receivedMessage.content ?: "",
                        messageType = receivedMessage.messageType,
                        attachmentType = receivedMessage.attachment?.type
                    )
                
                // Get FcmMessageHandler and delegate processing
                fcmMessageHandler.handleFcmMessage(receivedMessage)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error handling chat message from FCM", e)
            }
        }
    }
}
