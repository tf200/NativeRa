package com.taha.newraapp.domain.repository

import com.taha.newraapp.data.model.request.DeviceInfo
import com.taha.newraapp.data.model.response.TokenResponse

interface AuthRepository {
    suspend fun login(officerId: String, password: String, deviceInfo: DeviceInfo): TokenResponse
    suspend fun updateFcmToken(fcmToken: String): Result<Unit>
}
