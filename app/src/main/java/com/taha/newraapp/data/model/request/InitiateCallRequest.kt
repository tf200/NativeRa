package com.taha.newraapp.data.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InitiateCallRequest(
    @SerialName("calleeId")
    val calleeId: String,
    @SerialName("roomId")
    val roomId: String,
    @SerialName("callType")
    val callType: String  // "audio" or "video"
)
