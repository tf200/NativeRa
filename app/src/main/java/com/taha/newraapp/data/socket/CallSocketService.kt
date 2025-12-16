package com.taha.newraapp.data.socket

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject

/**
 * Service for handling call-related socket events.
 * 
 * Listens for:
 * - action:call:accepted - When callee accepts a call
 * - action:call:rejected - When callee rejects a call
 * - action:call:ended - When call is ended by either party
 * 
 * Emits:
 * - request:call:accept - When we accept an incoming call
 * - request:call:reject - When we reject an incoming call  
 * - request:call:end - When we end an active call
 */
class CallSocketService(
    private val socketManager: SocketManager
) {
    companion object {
        private const val TAG = "CallSocketService"
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    
    // Event flows for call state changes
    private val _callAccepted = MutableSharedFlow<CallAcceptedData>()
    val callAccepted: SharedFlow<CallAcceptedData> = _callAccepted.asSharedFlow()
    
    private val _callRejected = MutableSharedFlow<CallRejectedData>()
    val callRejected: SharedFlow<CallRejectedData> = _callRejected.asSharedFlow()
    
    private val _callEnded = MutableSharedFlow<CallEndedData>()
    val callEnded: SharedFlow<CallEndedData> = _callEnded.asSharedFlow()
    
    init {
        registerEventListeners()
    }
    
    private fun registerEventListeners() {
        // Listen for call accepted
        socketManager.on(SocketEvents.ACTION_CALL_ACCEPTED) { data ->
            Log.d(TAG, "Received call accepted event: $data")
            parseCallAccepted(data)
        }
        
        // Listen for call rejected
        socketManager.on(SocketEvents.ACTION_CALL_REJECTED) { data ->
            Log.d(TAG, "Received call rejected event: $data")
            parseCallRejected(data)
        }
        
        // Listen for call ended
        socketManager.on(SocketEvents.ACTION_CALL_ENDED) { data ->
            Log.d(TAG, "Received call ended event: $data")
            parseCallEnded(data)
        }
    }
    
    // =============================================
    // Incoming Event Parsers
    // =============================================
    
    private fun parseCallAccepted(data: Any) {
        try {
            val jsonObj = data as? JSONObject ?: return
            val callId = jsonObj.optString("callId")
            val roomId = jsonObj.optString("roomId")
            val acceptedBy = jsonObj.optString("acceptedBy")
            
            if (callId.isNotEmpty()) {
                serviceScope.launch {
                    _callAccepted.emit(CallAcceptedData(callId, roomId, acceptedBy))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing call accepted event", e)
        }
    }
    
    private fun parseCallRejected(data: Any) {
        try {
            val jsonObj = data as? JSONObject ?: return
            val callId = jsonObj.optString("callId")
            val rejectedBy = jsonObj.optString("rejectedBy")
            val reason = jsonObj.optString("reason", null)
            
            if (callId.isNotEmpty()) {
                serviceScope.launch {
                    _callRejected.emit(CallRejectedData(callId, rejectedBy, reason))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing call rejected event", e)
        }
    }
    
    private fun parseCallEnded(data: Any) {
        try {
            val jsonObj = data as? JSONObject ?: return
            val callId = jsonObj.optString("callId")
            val endedBy = jsonObj.optString("endedBy")
            val reason = jsonObj.optString("reason", null)
            
            if (callId.isNotEmpty()) {
                serviceScope.launch {
                    _callEnded.emit(CallEndedData(callId, endedBy, reason))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing call ended event", e)
        }
    }
    
    // =============================================
    // Outgoing Event Emitters
    // =============================================
    
    /**
     * Accept an incoming call.
     * 
     * @param callId The call ID from the incoming call notification
     * @param roomId The Jitsi room ID
     */
    fun acceptCall(callId: String, roomId: String) {
        Log.d(TAG, "Accepting call: $callId")
        
        val payload = JSONObject().apply {
            put("callId", callId)
            put("roomId", roomId)
        }
        
        socketManager.emit(SocketEvents.REQUEST_CALL_ACCEPT, payload)
    }
    
    /**
     * Reject an incoming call.
     * 
     * @param callId The call ID to reject
     * @param reason Optional reason (e.g., "busy", "declined")
     */
    fun rejectCall(callId: String, reason: String? = null) {
        Log.d(TAG, "Rejecting call: $callId, reason: $reason")
        
        val payload = JSONObject().apply {
            put("callId", callId)
            reason?.let { put("reason", it) }
        }
        
        socketManager.emit(SocketEvents.REQUEST_CALL_REJECT, payload)
    }
    
    /**
     * End an active call.
     * 
     * @param callId The call ID to end
     * @param reason Optional reason (e.g., "hangup", "network_error")
     */
    fun endCall(callId: String, reason: String? = null) {
        Log.d(TAG, "Ending call: $callId, reason: $reason")
        
        val payload = JSONObject().apply {
            put("callId", callId)
            reason?.let { put("reason", it) }
        }
        
        socketManager.emit(SocketEvents.REQUEST_CALL_END, payload)
    }
}

// =============================================
// Data Classes for Call Events
// =============================================

data class CallAcceptedData(
    val callId: String,
    val roomId: String,
    val acceptedBy: String
)

data class CallRejectedData(
    val callId: String,
    val rejectedBy: String,
    val reason: String?
)

data class CallEndedData(
    val callId: String,
    val endedBy: String,
    val reason: String?
)
