package com.taha.newraapp.data.call

import android.content.Context
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.LocalVideoTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager for LiveKit room connections.
 * Handles connecting/disconnecting and controlling local audio/video.
 */
class LiveKitCallManager(private val context: Context) {
    
    companion object {
        private const val TAG = "LiveKitCallManager"
        const val LIVEKIT_SERVER_URL = "wss://jitsi.micladevops.com"
    }
    
    private val _room = MutableStateFlow<Room?>(null)
    val room: StateFlow<Room?> = _room.asStateFlow()
    
    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // Local media state
    private val _isMicEnabled = MutableStateFlow(true)
    val isMicEnabled: StateFlow<Boolean> = _isMicEnabled.asStateFlow()
    
    private val _isCameraEnabled = MutableStateFlow(false)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()
    
    /**
     * Connect to a LiveKit room.
     * 
     * @param token JWT access token from backend
     * @return The connected Room (mic/camera not enabled yet - call enableMedia after permissions)
     */
    suspend fun connect(token: String): Room {
        Log.d(TAG, "Connecting to LiveKit room...")
        
        val room = LiveKit.create(context)
        room.connect(LIVEKIT_SERVER_URL, token)
        
        _room.value = room
        _isConnected.value = true
        
        Log.d(TAG, "Connected to LiveKit room successfully")
        return room
    }
    
    /**
     * Enable media tracks after permissions are granted.
     * Call this after the user has granted RECORD_AUDIO and CAMERA permissions.
     */
    suspend fun enableMedia(enableCamera: Boolean) {
        val room = _room.value ?: return
        
        Log.d(TAG, "Enabling media: mic=true, camera=$enableCamera")
        
        // Enable microphone
        room.localParticipant.setMicrophoneEnabled(true)
        _isMicEnabled.value = true
        
        // Enable camera if requested
        if (enableCamera) {
            room.localParticipant.setCameraEnabled(true)
            _isCameraEnabled.value = true
        }
    }
    
    /**
     * Toggle microphone on/off.
     */
    suspend fun toggleMicrophone() {
        val room = _room.value ?: return
        val newState = !_isMicEnabled.value
        room.localParticipant.setMicrophoneEnabled(newState)
        _isMicEnabled.value = newState
        Log.d(TAG, "Microphone enabled: $newState")
    }
    
    /**
     * Toggle camera on/off.
     */
    suspend fun toggleCamera() {
        val room = _room.value ?: return
        val newState = !_isCameraEnabled.value
        room.localParticipant.setCameraEnabled(newState)
        _isCameraEnabled.value = newState
        Log.d(TAG, "Camera enabled: $newState")
    }
    
    /**
     * Switch between front and back camera.
     */
    suspend fun switchCamera() {
        val room = _room.value ?: return
        val videoTrack = room.localParticipant.getTrackPublication(io.livekit.android.room.track.Track.Source.CAMERA)
            ?.track as? LocalVideoTrack
            
        if (videoTrack != null) {
            videoTrack.switchCamera()
            Log.d(TAG, "Switching camera")
        } else {
             Log.w(TAG, "No local video track found to switch")
        }
    }

    /**
     * Toggle speakerphone.
     */
    fun toggleSpeaker(enable: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.isSpeakerphoneOn = enable
        Log.d(TAG, "Speakerphone enabled: $enable")
    }
    
    /**
     * Disconnect from the room.
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting from LiveKit room")
        _room.value?.disconnect()
        _room.value = null
        _isConnected.value = false
        _isMicEnabled.value = false
        _isCameraEnabled.value = false
    }
}
