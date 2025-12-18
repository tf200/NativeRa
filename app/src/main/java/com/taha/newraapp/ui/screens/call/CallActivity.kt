package com.taha.newraapp.ui.screens.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.taha.newraapp.data.call.LiveKitCallManager
import com.taha.newraapp.data.socket.CallSocketService
import com.taha.newraapp.ui.theme.TestRaTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Full-screen activity for an active LiveKit call.
 * 
 * Features:
 * - Displays remote participant video/avatar
 * - Local video preview (PiP)
 * - Mute/camera/speaker controls
 * - End call button
 */
class CallActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "CallActivity"
        
        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_ROOM_ID = "room_id"
        private const val EXTRA_CALL_ID = "call_id"
        private const val EXTRA_IS_VIDEO = "is_video"
        private const val EXTRA_REMOTE_NAME = "remote_name"
        private const val EXTRA_REMOTE_INITIALS = "remote_initials"
        
        fun createIntent(
            context: Context,
            token: String,
            roomId: String,
            callId: String,
            isVideo: Boolean,
            remoteName: String,
            remoteInitials: String
        ): Intent {
            return Intent(context, CallActivity::class.java).apply {
                putExtra(EXTRA_TOKEN, token)
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_IS_VIDEO, isVideo)
                putExtra(EXTRA_REMOTE_NAME, remoteName)
                putExtra(EXTRA_REMOTE_INITIALS, remoteInitials)
            }
        }
    }
    
    private val liveKitCallManager: LiveKitCallManager by inject()
    private val callSocketService: CallSocketService by inject()
    
    // Call parameters
    private lateinit var token: String
    private lateinit var roomId: String
    private lateinit var callId: String
    private var isVideo: Boolean = false
    private lateinit var remoteName: String
    private lateinit var remoteInitials: String
    
    // Permission request launcher for initial media permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        
        if (audioGranted) {
            // Permissions granted, enable media
            lifecycleScope.launch {
                try {
                    liveKitCallManager.enableMedia(enableCamera = isVideo && cameraGranted)
                    Log.d(TAG, "Media enabled: mic=true, camera=${isVideo && cameraGranted}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable media", e)
                }
            }
        } else {
            Log.e(TAG, "Audio permission denied - cannot proceed with call")
            endCall()
        }
    }
    
    // Camera permission launcher for toggling camera mid-call
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            lifecycleScope.launch {
                try {
                    liveKitCallManager.toggleCamera()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to toggle camera", e)
                }
            }
        } else {
            Log.w(TAG, "Camera permission denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Extract intent extras
        token = intent.getStringExtra(EXTRA_TOKEN) ?: run {
            Log.e(TAG, "Missing token")
            finish()
            return
        }
        roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: ""
        callId = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
        isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
        remoteName = intent.getStringExtra(EXTRA_REMOTE_NAME) ?: "Unknown"
        remoteInitials = intent.getStringExtra(EXTRA_REMOTE_INITIALS) ?: "?"
        
        Log.d(TAG, "Starting call: room=$roomId, video=$isVideo")
        
        // Connect to LiveKit room first, then request permissions
        lifecycleScope.launch {
            try {
                liveKitCallManager.connect(token)
                Log.d(TAG, "Connected to LiveKit room")
                
                // Now request permissions for mic/camera
                requestMediaPermissions()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to LiveKit", e)
                finish()
                return@launch
            }
        }
        
        setContent {
            TestRaTheme {
                val room by liveKitCallManager.room.collectAsState()
                val isMicEnabled by liveKitCallManager.isMicEnabled.collectAsState()
                val isCameraEnabled by liveKitCallManager.isCameraEnabled.collectAsState()
                
                CallScreen(
                    room = room,
                    remoteName = remoteName,
                    remoteInitials = remoteInitials,
                    isVideoCall = isVideo,
                    isMicEnabled = isMicEnabled,
                    isCameraEnabled = isCameraEnabled,
                    onMicToggle = {
                        lifecycleScope.launch {
                            liveKitCallManager.toggleMicrophone()
                        }
                    },
                    onCameraToggle = {
                        toggleCameraWithPermission()
                    },
                    onSwitchCamera = {
                        lifecycleScope.launch {
                            liveKitCallManager.switchCamera()
                        }
                    },
                    onEndCall = {
                        endCall()
                    }
                )
            }
        }
    }
    
    private fun requestMediaPermissions() {
        val neededPermissions = mutableListOf<String>()
        
        // Always need audio for calls
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.RECORD_AUDIO)
        }
        
        // Need camera for video calls
        if (isVideo && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.CAMERA)
        }
        
        if (neededPermissions.isEmpty()) {
            // All permissions already granted
            lifecycleScope.launch {
                try {
                    liveKitCallManager.enableMedia(enableCamera = isVideo)
                    Log.d(TAG, "Media enabled with existing permissions")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable media", e)
                }
            }
        } else {
            // Request missing permissions
            permissionLauncher.launch(neededPermissions.toTypedArray())
        }
    }
    
    /**
     * Toggle camera with permission check.
     * If camera is on, turn it off (no permission needed).
     * If camera is off, check for permission before enabling.
     */
    private fun toggleCameraWithPermission() {
        val isCameraCurrentlyEnabled = liveKitCallManager.isCameraEnabled.value
        
        if (isCameraCurrentlyEnabled) {
            // Turning off - no permission needed
            lifecycleScope.launch {
                try {
                    liveKitCallManager.toggleCamera()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to toggle camera off", e)
                }
            }
        } else {
            // Turning on - check permission first
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED) {
                lifecycleScope.launch {
                    try {
                        liveKitCallManager.toggleCamera()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to toggle camera on", e)
                    }
                }
            } else {
                // Request camera permission
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun endCall() {
        Log.d(TAG, "Ending call")
        
        // Disconnect from LiveKit
        liveKitCallManager.disconnect()
        
        // Notify server via socket
        if (callId.isNotEmpty()) {
            callSocketService.endCall(callId, "hangup")
        }
        
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Ensure we disconnect on activity destroy
        liveKitCallManager.disconnect()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Confirm before ending call on back press
        endCall()
        super.onBackPressed()
    }
}

