package com.taha.newraapp.data.network

import com.taha.newraapp.data.model.request.LoginRequest
import com.taha.newraapp.data.model.request.RefreshTokenRequest
import com.taha.newraapp.data.model.response.PowerSyncTokenResponse
import com.taha.newraapp.data.model.response.TokenResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/signin")
    suspend fun login(@Body request: LoginRequest): TokenResponse
    
    @POST("auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): TokenResponse
    
    @POST("auth/powersync/authenticate")
    suspend fun getPowerSyncToken(
        @Header("Authorization") authorization: String
    ): PowerSyncTokenResponse
    
    @POST("auth/signout")
    suspend fun signout(
        @Header("Authorization") authorization: String
    )
    
    @retrofit2.http.PUT("auth/device/fcm-token")
    suspend fun updateFcmToken(
        @Header("Authorization") authorization: String,
        @Body request: com.taha.newraapp.data.model.request.FcmTokenRequest
    ): com.taha.newraapp.data.model.response.MessageResponse
}
