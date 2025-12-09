package com.taha.newraapp.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String, // UUID
    val conversationId: String, // Usually officerId of the other person (peerId)
    val senderId: String,
    val recipientId: String,
    val content: String,
    val type: String, // "TEXT", "IMAGE", etc.
    val status: String, // "PENDING", "SENT", "DELIVERED", "READ"
    val timestamp: Long
)
