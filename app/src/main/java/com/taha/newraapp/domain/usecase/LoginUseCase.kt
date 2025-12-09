package com.taha.newraapp.domain.usecase

import com.taha.newraapp.data.local.TokenManager
import com.taha.newraapp.data.model.request.DeviceInfo
import com.taha.newraapp.data.socket.SocketManager
import com.taha.newraapp.data.sync.PowerSyncManager
import com.taha.newraapp.domain.repository.AuthRepository

class LoginUseCase(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val powerSyncManager: PowerSyncManager,
    private val socketManager: SocketManager
) {
    suspend operator fun invoke(officerId: String, password: String, deviceInfo: DeviceInfo): Result<Unit> {
        return try {
            // Authenticate with backend
            val response = authRepository.login(officerId, password, deviceInfo)
            
            // Save tokens for offline access
            tokenManager.saveTokens(response.accessToken, response.refreshToken)
            
            // Initialize PowerSync after successful login
            try {
                powerSyncManager.initialize()
            } catch (e: Exception) {
                // Log PowerSync initialization error but don't fail login
                // User can still use the app, sync will retry later
                e.printStackTrace()
            }
            
            // Connect to Socket.IO for real-time messaging
            try {
                socketManager.connect()
            } catch (e: Exception) {
                // Log Socket.IO connection error but don't fail login
                // Real-time features will be unavailable, but app can still work
                e.printStackTrace()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
