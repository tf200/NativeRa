package com.taha.newraapp.data.repository

import com.taha.newraapp.data.local.dao.MessageDao
import com.taha.newraapp.data.local.entities.ConversationEntity
import com.taha.newraapp.data.local.entities.MessageEntity
import com.taha.newraapp.domain.model.Conversation
import com.taha.newraapp.domain.model.Message
import com.taha.newraapp.domain.model.MessageStatus
import com.taha.newraapp.domain.model.MessageType
import com.taha.newraapp.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class MessageRepositoryImpl(
    private val messageDao: MessageDao,
    // Inject UserPreferences or similar to get currentUserId
) : MessageRepository {

    // TODO: Get real current user ID
    private val currentUserId = "CURRENT_USER_ID_PLACEHOLDER"

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
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = peerId,
            senderId = currentUserId,
            recipientId = peerId,
            content = content,
            type = MessageType.TEXT.name,
            status = MessageStatus.PENDING.name,
            timestamp = System.currentTimeMillis()
        )
        messageDao.insertMessage(message)
        
        // Update Conversation
        val conversation = ConversationEntity(
            peerId = peerId,
            lastMessageContent = content,
            lastMessageTimestamp = message.timestamp,
            unreadCount = 0 // Sent by us, so 0 unread
        )
        messageDao.insertConversation(conversation)

        // TODO: Trigger Network Call / WebSocket
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
