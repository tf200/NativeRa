package com.taha.newraapp.ui.screens.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.taha.newraapp.data.service.CallNotificationManager
import com.taha.newraapp.data.socket.CallSocketService
import com.taha.newraapp.ui.theme.TestRaTheme
import org.koin.android.ext.android.inject

/**
 * Full-screen activity for incoming calls.
 * Shows when device is locked or from notification.
 * 
 * Features:
 * - Shows over lock screen
 * - Turns screen on
 * - Accept/Decline buttons
 * - Caller info display
 */
class IncomingCallActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "IncomingCallActivity"
        
        fun createIntent(
            context: Context,
            callId: String,
            roomId: String,
            callType: String,
            callerId: String,
            callerName: String
        ): Intent {
            return Intent(context, IncomingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra(CallNotificationManager.EXTRA_CALL_ID, callId)
                putExtra(CallNotificationManager.EXTRA_ROOM_ID, roomId)
                putExtra(CallNotificationManager.EXTRA_CALL_TYPE, callType)
                putExtra(CallNotificationManager.EXTRA_CALLER_ID, callerId)
                putExtra(CallNotificationManager.EXTRA_CALLER_NAME, callerName)
            }
        }
    }
    
    private val callSocketService: CallSocketService by inject()
    private val callNotificationManager: CallNotificationManager by inject()
    
    // Call data
    private lateinit var callId: String
    private lateinit var roomId: String
    private lateinit var callType: String
    private lateinit var callerId: String
    private lateinit var callerName: String
    private lateinit var token: String
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Show over lock screen and turn screen on
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        
        enableEdgeToEdge()
        
        // Extract call data
        callId = intent.getStringExtra(CallNotificationManager.EXTRA_CALL_ID) ?: run {
            Log.e(TAG, "Missing callId")
            finish()
            return
        }
        roomId = intent.getStringExtra(CallNotificationManager.EXTRA_ROOM_ID) ?: ""
        callType = intent.getStringExtra(CallNotificationManager.EXTRA_CALL_TYPE) ?: "audio"
        callerId = intent.getStringExtra(CallNotificationManager.EXTRA_CALLER_ID) ?: ""
        callerName = intent.getStringExtra(CallNotificationManager.EXTRA_CALLER_NAME) ?: "Unknown"
        token = intent.getStringExtra(CallNotificationManager.EXTRA_TOKEN) ?: run {
            Log.e(TAG, "Missing LiveKit token")
            finish()
            return
        }
        
        Log.d(TAG, "Incoming call from: $callerName, type: $callType, room: $roomId")
        
        // Check if opened via accept action
        val action = intent.getStringExtra(CallNotificationManager.EXTRA_ACTION)
        if (action == CallNotificationManager.ACTION_ACCEPT) {
            Log.d(TAG, "Call accepted via notification action")
            acceptCall()
            return
        }
        
        // Show incoming call UI
        setContent {
            TestRaTheme {
                IncomingCallScreen(
                    callerName = callerName,
                    callerInitials = getInitials(callerName),
                    isVideoCall = callType == "video",
                    onAcceptClick = { acceptCall() },
                    onDeclineClick = { declineCall() }
                )
            }
        }
    }
    
    /**
     * Accept the call - send socket event and launch call UI.
     */
    private fun acceptCall() {
        Log.d(TAG, "Accepting call: $callId")
        
        // Cancel notification
        callNotificationManager.cancelNotification()
        
        // Send accept via socket
        callSocketService.acceptCall(callId, roomId)
        
        // Launch LiveKit call activity
        val callIntent = CallActivity.createIntent(
            context = this,
            token = token,
            roomId = roomId,
            callId = callId,
            isVideo = callType == "video",
            remoteName = callerName,
            remoteInitials = getInitials(callerName)
        )
        startActivity(callIntent)
        
        finish()
    }
    
    /**
     * Decline the call - send socket event and close.
     */
    private fun declineCall() {
        Log.d(TAG, "Declining call: $callId")
        
        // Cancel notification
        callNotificationManager.cancelNotification()
        
        // Send reject via socket
        callSocketService.rejectCall(callId, "declined")
        
        finish()
    }
    
    private fun getInitials(name: String): String {
        val words = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(2).uppercase()
            else -> "${words[0].first()}${words[1].first()}".uppercase()
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Don't allow back - must accept or decline
        // super.onBackPressed()
    }
}
