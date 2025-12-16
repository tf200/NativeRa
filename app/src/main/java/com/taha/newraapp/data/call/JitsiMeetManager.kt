package com.taha.newraapp.data.call

import android.content.Context
import android.util.Log
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import org.jitsi.meet.sdk.JitsiMeetUserInfo
import java.net.URL
import java.util.UUID

/**
 * Manager class for Jitsi Meet video/audio calls.
 * Handles launching and managing Jitsi conference calls.
 */
class JitsiMeetManager(private val context: Context) {
    
    companion object {
        private const val TAG = "JitsiMeetManager"
        private const val JITSI_SERVER_URL = "https://meet.jit.si"
    }
    
    /**
     * Generate a unique room ID for a new call.
     * Format: newra_<UUID>
     */
    fun generateRoomId(): String {
        return "newra_${UUID.randomUUID()}"
    }
    
    /**
     * Launch a Jitsi meeting with the given room ID.
     * 
     * @param roomId The unique room identifier
     * @param userDisplayName Name to display for this user in the call
     * @param isAudioOnly If true, camera will be disabled (audio call)
     * @param serverUrl Optional custom Jitsi server URL (defaults to meet.jit.si)
     */
    fun launchMeeting(
        roomId: String,
        userDisplayName: String,
        isAudioOnly: Boolean = false,
        serverUrl: String = JITSI_SERVER_URL
    ) {
        Log.d(TAG, "Launching Jitsi meeting: room=$roomId, user=$userDisplayName, audioOnly=$isAudioOnly")
        
        try {
            val userInfo = JitsiMeetUserInfo().apply {
                displayName = userDisplayName
            }
            
            val options = JitsiMeetConferenceOptions.Builder()
                .setServerURL(URL(serverUrl))
                .setRoom(roomId)
                .setAudioMuted(false)
                .setVideoMuted(isAudioOnly)
                .setAudioOnly(isAudioOnly)
                .setUserInfo(userInfo)
                // Disable some features for a cleaner call experience
                .setFeatureFlag("invite.enabled", false)
                .setFeatureFlag("meeting-password.enabled", false)
                .setFeatureFlag("live-streaming.enabled", false)
                .setFeatureFlag("recording.enabled", false)
                .setFeatureFlag("calendar.enabled", false)
                .setFeatureFlag("call-integration.enabled", true)
                .setFeatureFlag("pip.enabled", true)
                .build()
            
            JitsiMeetActivity.launch(context, options)
            Log.d(TAG, "Jitsi meeting launched successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Jitsi meeting", e)
            throw e
        }
    }
    
    /**
     * Launch a video call.
     */
    fun launchVideoCall(roomId: String, userDisplayName: String) {
        launchMeeting(
            roomId = roomId,
            userDisplayName = userDisplayName,
            isAudioOnly = false
        )
    }
    
    /**
     * Launch an audio-only call.
     */
    fun launchAudioCall(roomId: String, userDisplayName: String) {
        launchMeeting(
            roomId = roomId,
            userDisplayName = userDisplayName,
            isAudioOnly = true
        )
    }
}
