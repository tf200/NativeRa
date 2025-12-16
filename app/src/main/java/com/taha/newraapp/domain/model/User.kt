package com.taha.newraapp.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class User(
    val id: String, // PowerSync database ID - must be used as senderId for socket
    val officerId: String,
    val firstName: String,
    val lastName: String,
    val center: String,
    val role: String,
    val phoneNumber: String,
    val isValid: Boolean,
    val isFrozen: Boolean,
    val contacts: String // Stored as JSON string
)
