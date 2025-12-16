package com.taha.newraapp.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InitiateCallResponse(
    @SerialName("callId")
    val callId: String,
    @SerialName("status")
    val status: String  // "ringing"
)
