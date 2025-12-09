package com.taha.newraapp.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val peerId: String, // The officerId of the person we are chatting with
    val lastMessageContent: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int
)
