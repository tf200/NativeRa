package com.taha.newraapp.ui.screens.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.taha.newraapp.ui.theme.TestRaTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Activity for making outgoing calls.
 * Launched from ChatRoomScreen when user taps call button.
 * 
 * Features:
 * - Shows custom OutgoingCallScreen while waiting
 * - Launches Jitsi when call is accepted
 * - Handles call declined/timeout
 */
class OutgoingCallActivity : ComponentActivity() {
    
    companion object {
        private const val EXTRA_CALLEE_ID = "callee_id"
        private const val EXTRA_CALLEE_NAME = "callee_name"
        private const val EXTRA_CALLEE_INITIALS = "callee_initials"
        private const val EXTRA_IS_VIDEO_CALL = "is_video_call"
        private const val EXTRA_USER_DISPLAY_NAME = "user_display_name"
        
        /**
         * Create intent to start OutgoingCallActivity.
         */
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Extract intent extras
        val calleeId = intent.getStringExtra(EXTRA_CALLEE_ID) ?: run {
            finish()
            return
        }
        val calleeName = intent.getStringExtra(EXTRA_CALLEE_NAME) ?: "Unknown"
        val calleeInitials = intent.getStringExtra(EXTRA_CALLEE_INITIALS) ?: "?"
        val isVideoCall = intent.getBooleanExtra(EXTRA_IS_VIDEO_CALL, false)
        val userDisplayName = intent.getStringExtra(EXTRA_USER_DISPLAY_NAME) ?: "You"
        
        // Initiate the call
        viewModel.initiateCall(calleeId, calleeName, isVideoCall)
        
        setContent {
            TestRaTheme {
                val callStatus by viewModel.callStatus.collectAsState()
                val callEnded by viewModel.callEnded.collectAsState()
                val isMuted by viewModel.isMuted.collectAsState()
                val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
                
                // Close activity when call ends
                LaunchedEffect(callEnded) {
                    if (callEnded) {
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
    
    override fun onBackPressed() {
        // End call instead of just going back
        viewModel.endCall()
        super.onBackPressed()
    }
}
