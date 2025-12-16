package com.taha.newraapp.data.service

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Tracks which chat conversation is currently visible on screen.
 * Used to intelligently suppress notifications for the active chat.
 * 
 * Usage:
 * - Call setActiveChat(userId) when user opens a chat
 * - Call clearActiveChat() when user leaves the chat
 * - Check isActiveChatWith(userId) before showing notifications
 */
object ActiveChatTracker {
    private val _activeChatUserId = MutableStateFlow<String?>(null)

    /**
     * Set the currently active chat conversation.
     * @param userId The peer user ID of the active chat
     */
    fun setActiveChat(userId: String?) {
        _activeChatUserId.value = userId
    }
    
    /**
     * Clear the active chat when user navigates away.
     */
    fun clearActiveChat() {
        _activeChatUserId.value = null
    }
    
    /**
     * Check if a specific chat is currently active on screen.
     * @param userId The peer user ID to check
     * @return true if this chat is currently visible
     */
    fun isActiveChatWith(userId: String): Boolean {
        return _activeChatUserId.value == userId
    }
}
