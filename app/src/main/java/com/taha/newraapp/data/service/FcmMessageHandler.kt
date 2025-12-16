package com.taha.newraapp.data.service

import android.content.Context
import android.util.Log
import com.taha.newraapp.data.socket.MessageSyncService
import com.taha.newraapp.data.socket.SocketManager
import com.taha.newraapp.data.socket.model.ReceivedMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Orchestrates FCM message handling.
 * Routes messages to appropriate handler based on socket connection state.
 * 
 * Decision Flow:
 * 1. Parse FCM data into ReceivedMessage
 * 2. If socket connected → SKIP (message will arrive via socket event listener)
 * 3. If socket NOT connected → Start MessageProcessingService
 */
class FcmMessageHandler(
    private val socketManager: SocketManager,
    private val context: Context
) {
    companion object {
        private const val TAG = "FcmMessageHandler"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Handle incoming FCM message.
     * Called from NewRaFirebaseMessagingService.onMessageReceived()
     * 
     * IMPORTANT: FCM is ONLY used to wake the app when socket is NOT connected.
     * If socket is connected, the message will arrive via normal socket event
     * listener (action:message:send), so we skip FCM processing to avoid duplicates.
     * 
     * @param message ReceivedMessage parsed from FCM data
     */
    fun handleFcmMessage(message: ReceivedMessage) {
        Log.i(TAG, ">>> FCM message received: id=${message.id}, sender=${message.senderId}")
        
        scope.launch {
            try {
                if (socketManager.isConnected()) {
                    // Socket is connected - message will arrive via socket event listener
                    // Skip FCM processing to avoid duplicates
                    Log.d(TAG, "Socket connected - SKIPPING FCM processing (will arrive via socket)")
                    return@launch
                }
                
                // Socket is NOT connected - app is in background/killed
                // Use FCM to wake app and process message
                Log.d(TAG, "Socket NOT connected - starting MessageProcessingService")
                MessageProcessingService.start(context, message)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error handling FCM message: ${message.id}", e)
            }
        }
    }
}
