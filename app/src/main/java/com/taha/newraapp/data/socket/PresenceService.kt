package com.taha.newraapp.data.socket

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.taha.newraapp.data.model.UserPresenceStatus
import com.taha.newraapp.data.network.AuthenticatedApiExecutor
import com.taha.newraapp.data.network.ChatApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Manages user presence via heartbeat and socket events.
 * 
 * Key behaviors:
 * - Sends heartbeat every 10s when app is in FOREGROUND
 * - Stops heartbeat when app goes to BACKGROUND (server marks offline after timeout)
 * - Stores presence in-memory only (StateFlow, not persisted)
 * - Fetches initial presence via REST API when opening a chat
 * - Receives real-time presence updates via socket events
 */
class PresenceService(
    private val socketManager: SocketManager,
    private val chatApi: ChatApi,
    private val apiExecutor: AuthenticatedApiExecutor
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "PresenceService"
        private const val HEARTBEAT_INTERVAL_MS = 10_000L // 10 seconds
    }

    // In-memory presence state - NOT persisted to database
    private val _presenceState = MutableStateFlow<Map<String, UserPresenceStatus>>(emptyMap())
    val presenceState: StateFlow<Map<String, UserPresenceStatus>> = _presenceState.asStateFlow()

    private var heartbeatJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isInitialized = false

    /**
     * Initialize the service and register lifecycle observer.
     * Call this once when the app starts (e.g., in Application.onCreate())
     */
    fun initialize() {
        if (isInitialized) return
        isInitialized = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        Log.d(TAG, "PresenceService initialized with lifecycle observer")
    }

    // ===========================================
    // Lifecycle Callbacks (Automatic)
    // ===========================================

    /**
     * Called when app comes to FOREGROUND
     */
    override fun onStart(owner: LifecycleOwner) {
        Log.d(TAG, "App in foreground - starting heartbeat")
        startHeartbeat()
        startPresenceListener()
    }

    /**
     * Called when app goes to BACKGROUND
     */
    override fun onStop(owner: LifecycleOwner) {
        Log.d(TAG, "App in background - stopping heartbeat")
        stopHeartbeat()
        // Keep presence listener active to receive updates when app resumes
    }

    // ===========================================
    // Heartbeat Management
    // ===========================================

    /**
     * Start sending heartbeats every 10 seconds.
     * Only runs when socket is connected.
     */
    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) {
            Log.d(TAG, "Heartbeat already running")
            return
        }
        
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                if (socketManager.isConnected()) {
                    sendHeartbeat()
                } else {
                    Log.d(TAG, "Socket not connected, skipping heartbeat")
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
        Log.d(TAG, "Heartbeat started")
    }

    /**
     * Stop sending heartbeats.
     * Server will mark user offline after ~30s of no heartbeats.
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.d(TAG, "Heartbeat stopped")
    }

    private fun sendHeartbeat() {
        socketManager.emit(
            SocketEvents.REQUEST_HEARTBEAT,
            JSONObject()
        ) { ack ->
            Log.d(TAG, "Heartbeat acknowledged: ${ack.contentToString()}")
        }
    }

    // ===========================================
    // REST API - Initial Presence Fetch
    // ===========================================

    /**
     * Fetch initial presence status via REST API.
     * Call this when opening a chat room for immediate feedback.
     */
    suspend fun fetchPresenceStatus(userIds: List<String>) {
        if (userIds.isEmpty()) return
        
        try {
            val response = apiExecutor.executeWithBearer { auth ->
                chatApi.getPresenceStatus(auth, userIds.joinToString(","))
            }
            _presenceState.update { current ->
                current + response.statuses
            }
            Log.d(TAG, "Fetched presence for ${userIds.size} users: ${response.statuses}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch presence status", e)
        }
    }

    // ===========================================
    // Socket Events - Real-time Updates
    // ===========================================

    /**
     * Start listening for real-time presence updates from socket.
     */
    fun startPresenceListener() {
        socketManager.on(SocketEvents.PRESENCE_UPDATE) { data ->
            handlePresenceUpdate(data)
        }
        Log.d(TAG, "Presence listener started")
    }

    /**
     * Stop listening for presence updates.
     */
    fun stopPresenceListener() {
        socketManager.off(SocketEvents.PRESENCE_UPDATE)
        Log.d(TAG, "Presence listener stopped")
    }

    // ===========================================
    // Targeted Presence Subscription
    // ===========================================

    /**
     * Subscribe to real-time presence updates for specific users.
     * This replaces the global presence broadcast with targeted updates.
     * Call this when opening a chat room to receive presence updates for the chat partner.
     */
    fun subscribeToPresence(userIds: List<String>) {
        if (userIds.isEmpty()) return
        
        val payload = JSONObject().apply {
            put("userIds", org.json.JSONArray(userIds))
        }
        
        socketManager.emit(
            SocketEvents.REQUEST_PRESENCE_SUBSCRIBE,
            payload
        ) { ack ->
            val response = ack.firstOrNull()
            if (response is JSONObject && response.optBoolean("acknowledged", false)) {
                Log.d(TAG, "Subscribed to presence for users: $userIds")
            } else {
                Log.w(TAG, "Presence subscription not acknowledged: ${ack.contentToString()}")
            }
        }
    }

    /**
     * Unsubscribe from real-time presence updates for specific users.
     * Call this when closing a chat room to stop receiving presence updates.
     */
    fun unsubscribeFromPresence(userIds: List<String>) {
        if (userIds.isEmpty()) return
        
        val payload = JSONObject().apply {
            put("userIds", org.json.JSONArray(userIds))
        }
        
        socketManager.emit(
            SocketEvents.REQUEST_PRESENCE_UNSUBSCRIBE,
            payload
        ) { ack ->
            Log.d(TAG, "Unsubscribed from presence for users: $userIds, ack: ${ack.contentToString()}")
        }
        
        // Remove unsubscribed users from local presence state
        _presenceState.update { current ->
            val updated = current.toMutableMap()
            userIds.forEach { userId -> updated.remove(userId) }
            updated
        }
    }

    private fun handlePresenceUpdate(data: Any) {
        try {
            val json = data as? JSONObject ?: return
            val userId = json.getString("userId")
            val status = json.getString("status")
            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
            
            val isOnline = status == "online"

            
            _presenceState.update { current ->
                val updated = current.toMutableMap()
                updated[userId] = UserPresenceStatus(
                    online = isOnline,
                    lastSeen = if (isOnline) null else timestamp
                )
                updated
            }
            
            Log.d(TAG, "Presence update: $userId is ${if (isOnline) "online" else "offline"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing presence update", e)
        }
    }

    // ===========================================
    // Helpers
    // ===========================================

    /**
     * Get presence for a specific user.
     */
    fun getPresence(userId: String): UserPresenceStatus? {
        return _presenceState.value[userId]
    }

    /**
     * Clean up when service is no longer needed.
     */
    fun cleanup() {
        stopHeartbeat()
        stopPresenceListener()
        serviceScope.cancel()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    }
}
