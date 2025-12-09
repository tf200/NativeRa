package com.taha.newraapp.domain.usecase

import com.taha.newraapp.domain.model.Conversation
import com.taha.newraapp.domain.repository.MessageRepository
import com.taha.newraapp.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class ContactUiModel(
    val officerId: String,
    val name: String,
    val center: String,
    val lastMessage: String?,
    val lastMessageTime: Long?,
    val unreadCount: Int,
    val isOnline: Boolean = false // Placeholder
)

class GetPrioritizedContactsUseCase(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository
) {
    operator fun invoke(): Flow<List<ContactUiModel>> {
        val usersFlow = userRepository.observeAllUsers()
        val conversationsFlow = messageRepository.getConversations()

        return combine(usersFlow, conversationsFlow) { users, conversations ->
            val conversationMap = conversations.associateBy { it.peerId }

            users.map { user ->
                val conversation = conversationMap[user.officerId]
                ContactUiModel(
                    officerId = user.officerId,
                    name = "${user.firstName} ${user.lastName}",
                    center = user.center ?: "",
                    lastMessage = conversation?.lastMessageContent,
                    lastMessageTime = conversation?.lastMessageTimestamp,
                    unreadCount = conversation?.unreadCount ?: 0
                )
            }.sortedWith(
                compareByDescending<ContactUiModel> { it.lastMessageTime ?: 0L }
                    .thenBy { it.name }
            )
        }
    }
}
