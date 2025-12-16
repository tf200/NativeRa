package com.taha.newraapp.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.taha.newraapp.data.socket.CallSocketService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Broadcast receiver for handling call action buttons from notification.
 * Handles "Decline" action when user taps decline button on notification.
 */
class CallActionReceiver : BroadcastReceiver(), KoinComponent {
    
    companion object {
        private const val TAG = "CallActionReceiver"
    }
    
    private val callSocketService: CallSocketService by inject()
    private val callNotificationManager: CallNotificationManager by inject()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")
        
        when (intent.action) {
            "com.taha.newraapp.DECLINE_CALL" -> {
                val callId = intent.getStringExtra(CallNotificationManager.EXTRA_CALL_ID) ?: return
                val roomId = intent.getStringExtra(CallNotificationManager.EXTRA_ROOM_ID) ?: return
                
                Log.d(TAG, "Declining call: $callId")
                
                scope.launch {
                    // Send reject via socket
                    callSocketService.rejectCall(callId, "declined")
                    
                    // Cancel notification
                    callNotificationManager.cancelNotification()
                }
            }
        }
    }
}
