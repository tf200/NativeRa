package com.taha.newraapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the online presence status of a user.
 * Stored in-memory only (not persisted to database).
 */
@Serializable
data class UserPresenceStatus(
    @SerialName("online")
    val online: Boolean,
    @SerialName("lastSeen")
    val lastSeen: Long? = null // null if currently online
)

/**
 * Response from the REST API for fetching presence status of multiple users.
 * GET /api/v2/chat/status?userIds=user1,user2,user3
 */
@Serializable
data class PresenceStatusResponse(
    @SerialName("statuses")
    val statuses: Map<String, UserPresenceStatus>
)

/**
 * Socket event payload for real-time presence updates.
 * Event: action:presence:update
 */
@Serializable
data class PresenceUpdateEvent(
    @SerialName("userId")
    val userId: String,
    @SerialName("status")
    val status: String, // "online" or "offline"
    @SerialName("timestamp")
    val timestamp: Long
)
