package com.taha.newraapp.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InitiateCallResponse(
    @SerialName("success")
    val success: Boolean,
    @SerialName("callId")
    val callId: String,
    @SerialName("token")
    val token: String? = null,
    @SerialName("error")
    val error: String? = null
)


