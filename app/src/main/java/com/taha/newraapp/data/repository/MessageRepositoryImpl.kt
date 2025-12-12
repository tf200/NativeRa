package com.taha.newraapp.data.repository

import com.taha.newraapp.data.local.dao.MessageDao
import com.taha.newraapp.data.local.entities.ConversationEntity
import com.taha.newraapp.data.local.entities.MessageEntity
import com.taha.newraapp.domain.model.Conversation
import com.taha.newraapp.domain.model.Message
import com.taha.newraapp.domain.model.MessageStatus
import com.taha.newraapp.domain.model.MessageType
import com.taha.newraapp.domain.repository.MessageRepository
import com.taha.newraapp.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class MessageRepositoryImpl(
    private val messageDao: MessageDao,
    private val userRepository: UserRepository
) : MessageRepository {

    /**
     * Get current user's database ID from UserRepository.
     * This ID must be used as senderId for socket messages because
     * the server validates it against the JWT token's 'sub' claim.
     */
    private suspend fun getCurrentUserId(): String {
        return userRepository.getCurrentUser()?.id ?: "UNKNOWN"
    }

    override fun getMessages(peerId: String): Flow<List<Message>> {
        return messageDao.getMessages(peerId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getConversations(): Flow<List<Conversation>> {
        return messageDao.getConversations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun sendMessage(peerId: String, content: String) {
        val currentUserId = getCurrentUserId()
        val timestamp = System.currentTimeMillis()
        
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = peerId,
            senderId = currentUserId,
            recipientId = peerId,
            content = content,
            type = "text", // Must be lowercase: 'text' or 'media' per server schema
            status = MessageStatus.PENDING.name,
            timestamp = timestamp
        )
        messageDao.insertMessage(message)
        
        // Update Conversation with new fields
        val conversation = ConversationEntity(
            peerId = peerId,
            lastMessageContent = content,
            lastMessageTimestamp = timestamp,
            unreadCount = 0, // Sent by us, so 0 unread
            lastMessageSenderId = currentUserId,
            lastMessageStatus = MessageStatus.PENDING.name
        )
        messageDao.insertConversation(conversation)
    }

    /**
     * Handle incoming message from socket and save to local DB.
     * This will be connected to ChatSocketService later.
     */
    suspend fun receiveMessage(
        senderId: String,
        content: String,
        timestamp: Long,
        messageId: String
    ) {
        val currentUserId = getCurrentUserId()
        
        val message = MessageEntity(
            id = messageId,
            conversationId = senderId,  // Group by sender for conversation view
            senderId = senderId,
            recipientId = currentUserId,
            content = content,
            type = MessageType.text.name,
            status = MessageStatus.DELIVERED.name,
            timestamp = timestamp
        )
        messageDao.insertMessage(message)
        
        // Update conversation with unread count increment
        val existingConvo = messageDao.getConversation(senderId)
        val newUnreadCount = (existingConvo?.unreadCount ?: 0) + 1
        
        val conversation = ConversationEntity(
            peerId = senderId,
            lastMessageContent = content,
            lastMessageTimestamp = timestamp,
            unreadCount = newUnreadCount,
            lastMessageSenderId = senderId,
            lastMessageStatus = MessageStatus.DELIVERED.name
        )
        messageDao.insertConversation(conversation)
    }

    /**
     * Update message status (called when socket acknowledges delivery/read).
     */
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus) {
        messageDao.updateMessageStatus(messageId, status.name)
    }

    override suspend fun markAsRead(peerId: String) {
        messageDao.updateUnreadCount(peerId, 0)
    }

    private fun MessageEntity.toDomain(): Message {
        return Message(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            recipientId = recipientId,
            content = content,
            type = MessageType.valueOf(type),
            status = MessageStatus.valueOf(status),
            timestamp = timestamp
        )
    }

    private fun ConversationEntity.toDomain(): Conversation {
        return Conversation(
            peerId = peerId,
            lastMessageContent = lastMessageContent,
            lastMessageTimestamp = lastMessageTimestamp,
            unreadCount = unreadCount
        )
    }
}

