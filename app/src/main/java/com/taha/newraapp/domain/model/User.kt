package com.taha.newraapp.domain.model

data class User(
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
