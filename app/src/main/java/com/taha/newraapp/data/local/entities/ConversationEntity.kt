package com.taha.newraapp.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val peerId: String, // The database ID of the person we are chatting with (matches JWT sub)
    val lastMessageContent: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int,
    val lastMessageSenderId: String = "",  // To determine incoming/outgoing
    val lastMessageStatus: String = "",    // For status indicator (PENDING, SENT, DELIVERED, READ)
    val isPinned: Boolean = false          // Future: pinned conversations
)
