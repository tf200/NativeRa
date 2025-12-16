package com.taha.newraapp.data.service

import android.util.Log
import com.taha.newraapp.domain.model.Message
import com.taha.newraapp.domain.model.User
import com.taha.newraapp.domain.repository.MessageRepository
import com.taha.newraapp.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Service that preloads chat data before navigation.
 * 
 * Call [preloadChatData] when a contact is tapped (before navigation starts).
 * ChatViewModel can then retrieve the pre-cached data instantly via [getCachedData].
 * 
 * This reduces jank during screen transitions by moving database queries
 * to before the animation starts.
 */
class ChatPreloadService(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository
) {
    companion object {
        private const val TAG = "ChatPreloadService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Cache for preloaded data
    private val _preloadedMessages = MutableStateFlow<PreloadedChatData?>(null)
    val preloadedData: StateFlow<PreloadedChatData?> = _preloadedMessages.asStateFlow()

    /**
     * Preload chat data for a specific peer.
     * Call this when user taps on a contact, before navigation starts.
     */
    fun preloadChatData(peerId: String) {
        scope.launch {
            try {
                Log.d(TAG, "Preloading chat data for peer: $peerId")
                
                // Start loading in parallel using coroutineScope
                coroutineScope {
                    val messagesDeferred = async {
                        messageRepository.getMessages(peerId).first()
                    }
                    
                    val usersDeferred = async {
                        userRepository.getAllUsers()
                    }
                    
                    val currentUserDeferred = async {
                        userRepository.getCurrentUser()
                    }
                    
                    // Await all results
                    val messages = messagesDeferred.await()
                    val users = usersDeferred.await()
                    val currentUser = currentUserDeferred.await()
                    val chatPartner = users.find { it.id == peerId }
                    
                    // Cache the results
                    _preloadedMessages.value = PreloadedChatData(
                        peerId = peerId,
                        messages = messages,
                        currentUser = currentUser,
                        chatPartner = chatPartner,
                        timestamp = System.currentTimeMillis()
                    )
                    
                    Log.d(TAG, "Preloaded ${messages.size} messages for peer: $peerId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to preload chat data", e)
                // Clear any stale cache on error
                _preloadedMessages.value = null
            }
        }
    }

    /**
     * Get cached data for a peer if available and fresh (within 10 seconds).
     * Returns null if no cached data or cache is stale.
     */
    fun getCachedData(peerId: String): PreloadedChatData? {
        val cached = _preloadedMessages.value
        return if (cached != null && 
                   cached.peerId == peerId && 
                   System.currentTimeMillis() - cached.timestamp < 10_000) {
            cached
        } else {
            null
        }
    }

    /**
     * Clear the cache after ChatViewModel has consumed it.
     */
    fun clearCache() {
        _preloadedMessages.value = null
    }
}

/**
 * Holds preloaded chat data for instant display.
 */
data class PreloadedChatData(
    val peerId: String,
    val messages: List<Message>,
    val currentUser: User?,
    val chatPartner: User?,
    val timestamp: Long
)
