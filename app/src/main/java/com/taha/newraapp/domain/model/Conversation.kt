package com.taha.newraapp.domain.model

data class Conversation(
    val peerId: String,
    val lastMessageContent: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int
)
