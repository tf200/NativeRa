package com.taha.newraapp.data.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    @SerialName("deviceId")
    val deviceId: String,
    @SerialName("name")
    val name: String? = null,
    @SerialName("model")
    val model: String? = null,
    @SerialName("operatingSystem")
    val operatingSystem: String? = null,
    @SerialName("osVersion")
    val osVersion: String? = null,
    @SerialName("manufacturer")
    val manufacturer: String? = null
)

@Serializable
data class LoginRequest(
    @SerialName("officerId")
    val officerId: String,
    @SerialName("password")
    val password: String,
    @SerialName("deviceInfo")
    val deviceInfo: DeviceInfo
)
