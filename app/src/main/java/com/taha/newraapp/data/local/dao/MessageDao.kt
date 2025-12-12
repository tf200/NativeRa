package com.taha.newraapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.taha.newraapp.data.local.entities.ConversationEntity
import com.taha.newraapp.data.local.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :peerId ORDER BY timestamp ASC")
    fun getMessages(peerId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM conversations ORDER BY lastMessageTimestamp DESC")
    fun getConversations(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
    
    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()

    @Query("UPDATE conversations SET unreadCount = :count WHERE peerId = :peerId")
    suspend fun updateUnreadCount(peerId: String, count: Int)

    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE peerId = :peerId")
    suspend fun incrementUnreadCount(peerId: String)

    @Query("SELECT * FROM conversations WHERE peerId = :peerId LIMIT 1")
    suspend fun getConversation(peerId: String): ConversationEntity?

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    // ========================================
    // Message Queue Queries
    // ========================================
    
    /**
     * Observe all pending messages ready to send.
     * Returns messages where:
     * - status is PENDING
     * - nextRetryAt has passed (or is 0 for immediate)
     * Ordered by timestamp to preserve per-conversation order.
     */
    @Query("""
        SELECT * FROM messages 
        WHERE status = 'PENDING' 
        AND nextRetryAt <= :currentTime
        ORDER BY conversationId, timestamp ASC
    """)
    fun observePendingMessages(currentTime: Long): Flow<List<MessageEntity>>

    /**
     * Get the oldest pending message per conversation.
     * This is for head-of-line processing - only process the oldest message
     * in each conversation to maintain order.
     */
    @Query("""
        SELECT * FROM messages m1
        WHERE status = 'PENDING' 
        AND nextRetryAt <= :currentTime
        AND timestamp = (
            SELECT MIN(m2.timestamp) FROM messages m2 
            WHERE m2.conversationId = m1.conversationId 
            AND m2.status = 'PENDING'
        )
        ORDER BY timestamp ASC
    """)
    fun getOldestPendingPerConversation(currentTime: Long): Flow<List<MessageEntity>>

    /**
     * Update retry tracking for a message.
     */
    @Query("UPDATE messages SET retryCount = :retryCount, nextRetryAt = :nextRetryAt WHERE id = :messageId")
    suspend fun updateRetryInfo(messageId: String, retryCount: Int, nextRetryAt: Long)

    /**
     * Update message status and retry info together.
     */
    @Query("UPDATE messages SET status = :status, retryCount = :retryCount, nextRetryAt = :nextRetryAt WHERE id = :messageId")
    suspend fun updateMessageStatusAndRetry(messageId: String, status: String, retryCount: Int, nextRetryAt: Long)

    /**
     * Get unread/unseen messages from a specific sender (for marking as seen).
     * Returns messages where:
     * - senderId matches (messages FROM this user)
     * - status is not READ (we haven't marked them as seen yet)
     * - conversationId matches (in this chat)
     */
    @Query("""
        SELECT id FROM messages 
        WHERE conversationId = :peerId 
        AND senderId = :peerId 
        AND status != 'READ'
        ORDER BY timestamp ASC
    """)
    suspend fun getUnseenMessageIds(peerId: String): List<String>

    /**
     * Mark multiple messages as READ in a single transaction.
     */
    @Query("UPDATE messages SET status = 'READ' WHERE id IN (:messageIds)")
    suspend fun markMessagesAsRead(messageIds: List<String>)
}

