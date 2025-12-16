package com.taha.newraapp.data.repository

import com.taha.newraapp.data.local.dao.MessageDao
import com.taha.newraapp.data.local.dao.UnreadCountTuple
import com.taha.newraapp.data.local.entities.ConversationEntity
import com.taha.newraapp.data.local.entities.MessageEntity
import com.taha.newraapp.domain.model.Conversation
import com.taha.newraapp.domain.model.Message
import com.taha.newraapp.domain.model.MessageStatus
import com.taha.newraapp.domain.model.MessageType
import com.taha.newraapp.domain.repository.MessageRepository
import com.taha.newraapp.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
        return combine(
            messageDao.getConversations(),
            messageDao.getAllUnreadCounts()
        ) { conversations, unreadCounts ->
            val unreadMap = unreadCounts.associate { it.conversationId to it.count }
            
            conversations.map { entity ->
                entity.toDomain().copy(
                    unreadCount = unreadMap[entity.peerId] ?: 0
                )
            }
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
            unreadCount = 0, // Ignored, using dynamic count
            lastMessageSenderId = currentUserId,
            lastMessageStatus = MessageStatus.PENDING.name
        )
        messageDao.insertConversation(conversation)
    }

    /**
     * Handle incoming message from socket and save to local DB.
     * This will be connected to ChatSocketService later.
     */
    /**
     * Handle incoming message from socket and save to local DB.
     * This will be connected to ChatSocketService later.
     */
    suspend fun receiveMessage(
        senderId: String,
        content: String,
        timestamp: Long,
        messageId: String,
        type: String = MessageType.text.name,
        attachmentId: String? = null,
        attachmentFileType: String? = null,
        attachmentMimeType: String? = null,
        attachmentFileName: String? = null,
        attachmentSize: Long? = null
    ) {
        val currentUserId = getCurrentUserId()
        
        val message = MessageEntity(
            id = messageId,
            conversationId = senderId,  // Group by sender for conversation view
            senderId = senderId,
            recipientId = currentUserId,
            content = content,
            type = type, // Use provided type
            status = MessageStatus.DELIVERED.name,
            timestamp = timestamp,
            attachmentId = attachmentId,
            attachmentFileType = attachmentFileType,
            attachmentMimeType = attachmentMimeType,
            attachmentFileName = attachmentFileName,
            attachmentSize = attachmentSize
        )
        messageDao.insertMessage(message)
        
        val conversation = ConversationEntity(
            peerId = senderId,
            lastMessageContent = if (type == "media") "[Attachment]" else content,
            lastMessageTimestamp = timestamp,
            unreadCount = 0, // Ignored, using dynamic count
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
        // No-op in repository, now handled by marking messages as READ in DAO
        // messageDao.updateUnreadCount(peerId, 0)
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
            timestamp = timestamp,
            attachmentId = attachmentId,
            attachmentLocalPath = attachmentLocalPath,
            attachmentFileType = attachmentFileType,
            attachmentMimeType = attachmentMimeType,
            attachmentFileName = attachmentFileName,
            attachmentSize = attachmentSize,
            downloadStatus = downloadStatus?.let { 
                try { 
                    com.taha.newraapp.domain.model.DownloadStatus.valueOf(it) 
                } catch (e: Exception) { 
                    null 
                } 
            }
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

