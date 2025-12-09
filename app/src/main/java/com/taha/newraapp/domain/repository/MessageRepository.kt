package com.taha.newraapp.domain.repository

import com.taha.newraapp.domain.model.Conversation
import com.taha.newraapp.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessages(peerId: String): Flow<List<Message>>
    fun getConversations(): Flow<List<Conversation>>
    suspend fun sendMessage(peerId: String, content: String)
    suspend fun markAsRead(peerId: String)
}
