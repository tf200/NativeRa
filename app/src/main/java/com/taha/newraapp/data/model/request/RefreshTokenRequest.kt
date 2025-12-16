package com.taha.newraapp.data.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RefreshTokenRequest(
    @SerialName("refreshToken")
    val refreshToken: String,
    @SerialName("deviceId")
    val deviceId: String
)
