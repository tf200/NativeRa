package com.taha.newraapp.data.repository

import android.util.Log
import com.taha.newraapp.data.model.request.DeviceInfo
import com.taha.newraapp.data.model.request.LoginRequest
import com.taha.newraapp.data.model.response.TokenResponse
import com.taha.newraapp.data.network.AuthApi
import com.taha.newraapp.domain.repository.AuthRepository

import com.taha.newraapp.data.model.request.FcmTokenRequest
import com.taha.newraapp.data.network.AuthenticatedApiExecutor

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val apiExecutor: AuthenticatedApiExecutor
) : AuthRepository {
    
    companion object {
        private const val TAG = "FCM_DEBUG"
    }
    
    override suspend fun login(officerId: String, password: String, deviceInfo: DeviceInfo): TokenResponse {
        return authApi.login(LoginRequest(officerId, password, deviceInfo))
    }

    override suspend fun updateFcmToken(fcmToken: String): Result<Unit> {
        Log.w(TAG, "AuthRepositoryImpl.updateFcmToken: Starting API call...")
        Log.w(TAG, "AuthRepositoryImpl.updateFcmToken: Token prefix = ${fcmToken.take(20)}...")
        return try {
            Log.w(TAG, "AuthRepositoryImpl.updateFcmToken: Calling executeWithBearer...")
            apiExecutor.executeWithBearer { token ->
                Log.w(TAG, "AuthRepositoryImpl.updateFcmToken: Bearer token available, calling API...")
                authApi.updateFcmToken(token, FcmTokenRequest(fcmToken))
            }
            Log.w(TAG, "AuthRepositoryImpl.updateFcmToken: API SUCCESS!")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "AuthRepositoryImpl.updateFcmToken: API FAILED!", e)
            Result.failure(e)
        }
    }
}
