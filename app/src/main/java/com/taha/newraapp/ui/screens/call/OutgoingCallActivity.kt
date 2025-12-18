package com.taha.newraapp.ui.screens.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.taha.newraapp.ui.theme.TestRaTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.UUID

/**
 * Activity for making outgoing calls.
 * 
 * Flow:
 * 1. Shows custom OutgoingCallScreen while waiting for callee
 * 2. When callee accepts, launches JitsiMeetActivity for the actual call
 * 3. On decline/error, closes activity
 */
class OutgoingCallActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "OutgoingCallActivity"
        
        private const val EXTRA_CALLEE_ID = "callee_id"
        private const val EXTRA_CALLEE_NAME = "callee_name"
        private const val EXTRA_CALLEE_INITIALS = "callee_initials"
        private const val EXTRA_IS_VIDEO_CALL = "is_video_call"
        private const val EXTRA_USER_DISPLAY_NAME = "user_display_name"
        
        fun createIntent(
            context: Context,
            calleeId: String,
            calleeName: String,
            calleeInitials: String,
            isVideoCall: Boolean,
            userDisplayName: String
        ): Intent {
            return Intent(context, OutgoingCallActivity::class.java).apply {
                putExtra(EXTRA_CALLEE_ID, calleeId)
                putExtra(EXTRA_CALLEE_NAME, calleeName)
                putExtra(EXTRA_CALLEE_INITIALS, calleeInitials)
                putExtra(EXTRA_IS_VIDEO_CALL, isVideoCall)
                putExtra(EXTRA_USER_DISPLAY_NAME, userDisplayName)
            }
        }
    }
    
    private val viewModel: OutgoingCallViewModel by viewModel()
    
    // Call parameters
    private lateinit var calleeId: String
    private lateinit var calleeName: String
    private lateinit var calleeInitials: String
    private var isVideoCall: Boolean = false
    private lateinit var userDisplayName: String
    private lateinit var roomId: String
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Extract intent extras
        calleeId = intent.getStringExtra(EXTRA_CALLEE_ID) ?: run {
            finish()
            return
        }
        calleeName = intent.getStringExtra(EXTRA_CALLEE_NAME) ?: "Unknown"
        calleeInitials = intent.getStringExtra(EXTRA_CALLEE_INITIALS) ?: "?"
        isVideoCall = intent.getBooleanExtra(EXTRA_IS_VIDEO_CALL, false)
        userDisplayName = intent.getStringExtra(EXTRA_USER_DISPLAY_NAME) ?: "You"
        
        // Generate room ID
        roomId = "newra_${UUID.randomUUID()}"
        Log.d(TAG, "Generated room ID: $roomId")
        
        // Initiate call via API (sends FCM to callee)
        viewModel.initiateCall(calleeId, calleeName, isVideoCall, roomId)
        
        setContent {
            TestRaTheme {
                val callStatus by viewModel.callStatus.collectAsState()
                val callEnded by viewModel.callEnded.collectAsState()
                val isMuted by viewModel.isMuted.collectAsState()
                val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
                val showCallUI by viewModel.showJitsiUI.collectAsState()
                val callerToken by viewModel.callerToken.collectAsState()
                
                // Close activity when call ends
                LaunchedEffect(callEnded) {
                    if (callEnded) {
                        finish()
                    }
                }
                
                // Launch LiveKit CallActivity when call is accepted
                LaunchedEffect(showCallUI, callerToken) {
                    if (showCallUI && callerToken != null) {
                        Log.d(TAG, "Call accepted - launching CallActivity")
                        val callIntent = CallActivity.createIntent(
                            context = this@OutgoingCallActivity,
                            token = callerToken!!,
                            roomId = roomId,
                            callId = viewModel.currentCallId ?: "",
                            isVideo = isVideoCall,
                            remoteName = calleeName,
                            remoteInitials = calleeInitials
                        )
                        startActivity(callIntent)
                        finish()
                    }
                }
                
                OutgoingCallScreen(
                    calleeName = calleeName,
                    calleeInitials = calleeInitials,
                    isVideoCall = isVideoCall,
                    callStatus = callStatus,
                    onEndCallClick = {
                        viewModel.endCall()
                    },
                    onMuteClick = {
                        viewModel.toggleMute()
                    },
                    onSpeakerClick = {
                        viewModel.toggleSpeaker()
                    },
                    isMuted = isMuted,
                    isSpeakerOn = isSpeakerOn
                )
            }
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        viewModel.endCall()
        super.onBackPressed()
    }
}
