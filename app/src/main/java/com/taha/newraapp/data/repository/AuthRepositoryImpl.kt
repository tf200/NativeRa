package com.taha.newraapp.data.repository

import com.taha.newraapp.data.model.request.DeviceInfo
import com.taha.newraapp.data.model.request.LoginRequest
import com.taha.newraapp.data.model.response.TokenResponse
import com.taha.newraapp.data.network.AuthApi
import com.taha.newraapp.domain.repository.AuthRepository

class AuthRepositoryImpl(private val authApi: AuthApi) : AuthRepository {
    override suspend fun login(officerId: String, password: String, deviceInfo: DeviceInfo): TokenResponse {
        return authApi.login(LoginRequest(officerId, password, deviceInfo))
    }
}
