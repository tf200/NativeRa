package com.taha.newraapp.domain.model

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val content: String,
    val type: MessageType,
    val status: MessageStatus,
    val timestamp: Long
)

enum class MessageType {
    TEXT, IMAGE, FILE
}

enum class MessageStatus {
    PENDING, SENT, DELIVERED, READ, FAILED
}
