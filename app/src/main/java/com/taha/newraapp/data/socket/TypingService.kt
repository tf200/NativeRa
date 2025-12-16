package com.taha.newraapp.data.socket

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Manages typing indicator state for chat conversations.
 * 
 * Features:
 * - Debounced typing event emission (max once per 3 seconds)
 * - Receives typing events from other users via socket
 * - Auto-clears typing state after 5 seconds of no updates
 * - In-memory state only (not persisted)
 */
class TypingService(
    private val socketManager: SocketManager
) {
    companion object {
        private const val TAG = "TypingService"
        private const val DEBOUNCE_INTERVAL_MS = 3000L  // 3 seconds between typing events
        private const val TYPING_TIMEOUT_MS = 5000L     // Clear typing after 5 seconds
    }

    // In-memory typing state: peerId -> timestamp of last typing event
    private val _typingState = MutableStateFlow<Map<String, Long>>(emptyMap())
    val typingState: StateFlow<Map<String, Long>> = _typingState.asStateFlow()

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timeoutJob: Job? = null
    
    // Debouncing: track last time we sent a typing event per recipient
    private val lastTypingSentMap = mutableMapOf<String, Long>()

    // ===========================================
    // Sending Typing Events
    // ===========================================

    /**
     * Send typing indicator to a recipient.
     * Debounced to max once per 3 seconds per recipient.
     * 
     * @param recipientId The user we're typing to
     */
    fun sendTypingIndicator(recipientId: String) {
        val now = System.currentTimeMillis()
        val lastSent = lastTypingSentMap[recipientId] ?: 0L
        
        if (now - lastSent < DEBOUNCE_INTERVAL_MS) {
            // Debounce: skip if we sent recently
            return
        }
        
        if (!socketManager.isConnected()) {
            Log.d(TAG, "Socket not connected, skipping typing indicator")
            return
        }
        
        lastTypingSentMap[recipientId] = now
        
        val payload = JSONObject().apply {
            put("recipientId", recipientId)
        }
        
        socketManager.emit(SocketEvents.REQUEST_TYPING, payload) { ack ->
            Log.d(TAG, "Typing indicator sent to $recipientId, ack: ${ack.contentToString()}")
        }
        
        Log.d(TAG, "Sent typing indicator to $recipientId")
    }

    // ===========================================
    // Receiving Typing Events
    // ===========================================

    /**
     * Start listening for typing events from other users.
     */
    fun startTypingListener() {
        socketManager.on(SocketEvents.ACTION_TYPING) { data ->
            handleTypingEvent(data)
        }
        startTimeoutChecker()
        Log.d(TAG, "Typing listener started")
    }

    /**
     * Stop listening for typing events.
     */
    fun stopTypingListener() {
        socketManager.off(SocketEvents.ACTION_TYPING)
        timeoutJob?.cancel()
        timeoutJob = null
        Log.d(TAG, "Typing listener stopped")
    }

    /**
     * Handle incoming typing event from socket.
     */
    private fun handleTypingEvent(data: Any) {
        try {
            val json = data as? JSONObject ?: return
            val senderId = json.getString("senderId")
            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
            
            // Atomic update using update {}
            // This prevents race conditions where multiple events coming in rapidly could overwrite each other
            _typingState.update { current ->
                current + (senderId to timestamp)
            }
            
            Log.d(TAG, "User $senderId is typing")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing typing event", e)
        }
    }

    // ===========================================
    // Timeout Management
    // ===========================================

    /**
     * Start a periodic job that clears stale typing indicators.
     */
    private fun startTimeoutChecker() {
        if (timeoutJob?.isActive == true) return
        
        timeoutJob = serviceScope.launch {
            while (isActive) {
                delay(1000L) // Check every second
                
                // Optimization: Don't do anything if map is empty
                if (_typingState.value.isEmpty()) continue
                
                clearStaleTypingStates()
            }
        }
    }

    /**
     * Remove typing states older than TYPING_TIMEOUT_MS.
     */
    private fun clearStaleTypingStates() {
        val now = System.currentTimeMillis()
        
        // This atomic update checks logic inside the block
        _typingState.update { current ->
            // Optimization: Filter logic to only create new map if changes needed
            val fresh = current.filterValues { timestamp ->
                now - timestamp < TYPING_TIMEOUT_MS
            }
            
            if (fresh.size != current.size) {
                val cleared = current.keys - fresh.keys
                Log.d(TAG, "Cleared stale typing for: $cleared")
                fresh
            } else {
                current // Return same object if no changes, preventing state emission
            }
        }
    }

    // ===========================================
    // External Triggers
    // ===========================================

    /**
     * Clear typing indicator for a specific user.
     * Called when we receive a message from that user.
     * 
     * @param userId The user who sent a message (no longer "typing")
     */
    fun clearTypingFor(userId: String) {
        // Atomic remove
        _typingState.update { current ->
            if (userId in current) {
                Log.d(TAG, "Cleared typing for $userId (message received)")
                current - userId
            } else {
                current
            }
        }
    }

    /**
     * Check if a specific user is currently typing.
     * 
     * @param userId The user to check
     * @return true if user is typing (has active non-expired state)
     */
    fun isTyping(userId: String): Boolean {
        val timestamp = _typingState.value[userId] ?: return false
        return System.currentTimeMillis() - timestamp < TYPING_TIMEOUT_MS
    }

    /**
     * Clear debounce state for a recipient.
     * Call this after sending a message so next keystroke triggers typing again.
     * 
     * @param recipientId The user we sent a message to
     */
    fun clearDebounceFor(recipientId: String) {
        lastTypingSentMap.remove(recipientId)
    }
}
