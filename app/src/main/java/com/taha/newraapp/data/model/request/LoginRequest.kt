package com.taha.newraapp.data.model.request

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val deviceId: String,
    val name: String? = null,
    val model: String? = null,
    val operatingSystem: String? = null,
    val osVersion: String? = null,
    val manufacturer: String? = null
)

@Serializable
data class LoginRequest(
    val officerId: String,
    val password: String,
    val deviceInfo: DeviceInfo
)
