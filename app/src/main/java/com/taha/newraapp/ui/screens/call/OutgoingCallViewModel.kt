package com.taha.newraapp.ui.screens.call

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taha.newraapp.data.call.JitsiMeetManager
import com.taha.newraapp.domain.repository.CallRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing outgoing call state.
 * Handles call initiation, Jitsi room joining, and call status updates.
 */
class OutgoingCallViewModel(
    private val callRepository: CallRepository,
    private val jitsiMeetManager: JitsiMeetManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "OutgoingCallVM"
    }
    
    // Call state
    private val _callStatus = MutableStateFlow("Calling...")
    val callStatus: StateFlow<String> = _callStatus.asStateFlow()
    
    private val _isCallActive = MutableStateFlow(false)
    val isCallActive: StateFlow<Boolean> = _isCallActive.asStateFlow()
    
    private val _callEnded = MutableStateFlow(false)
    val callEnded: StateFlow<Boolean> = _callEnded.asStateFlow()
    
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    
    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()
    
    // Current call info
    private var currentCallId: String? = null
    private var currentRoomId: String? = null
    
    /**
     * Initiate a call to the specified user.
     * 1. Generates a room ID
     * 2. Sends POST request to backend
     * 3. Immediately joins Jitsi room (caller joins first)
     */
    fun initiateCall(
        calleeId: String,
        calleeName: String,
        isVideoCall: Boolean
    ) {
        viewModelScope.launch {
            try {
                // Generate unique room ID
                val roomId = jitsiMeetManager.generateRoomId()
                currentRoomId = roomId
                
                Log.d(TAG, "Initiating call to $calleeId, room: $roomId, video: $isVideoCall")
                
                // Update status
                _callStatus.value = "Connecting..."
                
                // Send initiate call request to backend
                val callType = if (isVideoCall) "video" else "audio"
                val result = callRepository.initiateCall(
                    calleeId = calleeId,
                    roomId = roomId,
                    callType = callType
                )
                
                result.onSuccess { response ->
                    currentCallId = response.callId
                    _callStatus.value = "Ringing..."
                    _isCallActive.value = true
                    
                    Log.d(TAG, "Call initiated successfully: ${response.callId}")
                    
                    // TODO: Launch Jitsi meeting in background
                    // For now, we'll launch it when user sees the screen
                    // jitsiMeetManager.launchMeeting(roomId, "You", isVideoCall)
                    
                }.onFailure { error ->
                    Log.e(TAG, "Failed to initiate call", error)
                    _callStatus.value = "Call failed"
                    // End call after short delay
                    kotlinx.coroutines.delay(2000)
                    _callEnded.value = true
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception during call initiation", e)
                _callStatus.value = "Call failed"
                _callEnded.value = true
            }
        }
    }
    
    /**
     * Called when callee accepts the call (from WebSocket event).
     */
    fun onCallAccepted() {
        Log.d(TAG, "Call accepted!")
        _callStatus.value = "Connected"
        
        // TODO: Show Jitsi UI / transition to video call
    }
    
    /**
     * Called when callee declines the call.
     */
    fun onCallDeclined(reason: String = "declined") {
        Log.d(TAG, "Call declined: $reason")
        _callStatus.value = when (reason) {
            "busy" -> "User is busy"
            "timeout" -> "No answer"
            else -> "Call declined"
        }
        
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _callEnded.value = true
        }
    }
    
    /**
     * End the current call.
     */
    fun endCall() {
        Log.d(TAG, "Ending call")
        _callStatus.value = "Call ended"
        _isCallActive.value = false
        _callEnded.value = true
        
        // TODO: Send end call request to backend
        // TODO: Leave Jitsi room
    }
    
    /**
     * Toggle mute state.
     */
    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        // TODO: Mute Jitsi audio
    }
    
    /**
     * Toggle speaker state.
     */
    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
        // TODO: Toggle speaker in Jitsi
    }
    
    /**
     * Launch the Jitsi meeting room.
     * Should be called when transitioning from call screen to actual call.
     */
    fun launchJitsiMeeting(userDisplayName: String, isVideoCall: Boolean) {
        currentRoomId?.let { roomId ->
            Log.d(TAG, "Launching Jitsi meeting: $roomId")
            if (isVideoCall) {
                jitsiMeetManager.launchVideoCall(roomId, userDisplayName)
            } else {
                jitsiMeetManager.launchAudioCall(roomId, userDisplayName)
            }
        }
    }
}
