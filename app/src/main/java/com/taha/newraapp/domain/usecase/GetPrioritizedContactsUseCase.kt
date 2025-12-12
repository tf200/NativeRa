package com.taha.newraapp.domain.usecase

import com.taha.newraapp.domain.model.Conversation
import com.taha.newraapp.domain.repository.MessageRepository
import com.taha.newraapp.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * UI model for displaying a contact in the contacts list.
 */
data class ContactUiModel(
    val id: String, // PowerSync database ID - must be used for navigation and socket operations
    val officerId: String, // Display identifier (optional, for display purposes)
    val name: String,
    val center: String,
    val lastMessage: String?,
    val lastMessageTime: Long?,
    val unreadCount: Int,
    val isOnline: Boolean = false // Placeholder - will be connected to socket presence later
)

/**
 * Result containing partitioned contact lists for WhatsApp-style UI.
 */
data class ContactsResult(
    val recentConversations: List<ContactUiModel>,  // Contacts with messages (sorted by last message time)
    val allContacts: List<ContactUiModel>           // Contacts without messages (sorted alphabetically)
)

/**
 * Use case to get contacts partitioned into recent conversations and all contacts.
 * Recent conversations are prioritized at the top, sorted by last message time.
 * All other contacts are shown below, sorted alphabetically.
 */
class GetPrioritizedContactsUseCase(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository
) {
    /**
     * Returns a Flow of ContactsResult with two partitioned lists.
     */
    operator fun invoke(): Flow<ContactsResult> {
        val usersFlow = userRepository.observeAllUsers()
        val conversationsFlow = messageRepository.getConversations()

        return combine(usersFlow, conversationsFlow) { users, conversations ->
            val conversationMap = conversations.associateBy { it.peerId }

            // Map all users to ContactUiModel
            val allContactModels = users.map { user ->
                val conversation = conversationMap[user.id]
                ContactUiModel(
                    id = user.id,
                    officerId = user.officerId,
                    name = "${user.firstName} ${user.lastName}",
                    center = user.center ?: "",
                    lastMessage = conversation?.lastMessageContent,
                    lastMessageTime = conversation?.lastMessageTimestamp,
                    unreadCount = conversation?.unreadCount ?: 0
                )
            }

            // Partition into contacts with conversations and without
            val (withConversations, withoutConversations) = allContactModels.partition { 
                it.lastMessageTime != null 
            }

            ContactsResult(
                // Recent conversations: sorted by last message time (newest first)
                recentConversations = withConversations.sortedByDescending { it.lastMessageTime },
                // All contacts: sorted alphabetically by name
                allContacts = withoutConversations.sortedBy { it.name.lowercase() }
            )
        }
    }
}

