package com.taha.newraapp.domain.usecase

import com.taha.newraapp.data.local.TokenManager
import com.taha.newraapp.data.model.request.DeviceInfo
import com.taha.newraapp.data.socket.SocketConnectionManager
import com.taha.newraapp.data.socket.SocketManager
import com.taha.newraapp.data.sync.PowerSyncManager
import com.taha.newraapp.domain.repository.AuthRepository

class LoginUseCase(
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
    private val powerSyncManager: PowerSyncManager,
    private val socketManager: SocketManager,
    private val socketConnectionManager: SocketConnectionManager
) {
    suspend operator fun invoke(officerId: String, password: String, deviceInfo: DeviceInfo): Result<Unit> {
        return try {
            // Authenticate with backend
            val response = authRepository.login(officerId, password, deviceInfo)
            
            // Save tokens for offline access
            tokenManager.saveTokens(response.accessToken, response.refreshToken)
            // Save deviceId for refresh token requests
            tokenManager.saveDeviceId(deviceInfo.deviceId)
            
            // Initialize PowerSync after successful login
            // initialize() internally calls waitForFirstSync() to ensure user data is synced
            // before this method returns, guaranteeing data is available before socket connects
            try {
                powerSyncManager.initialize()
            } catch (e: Exception) {
                // Log PowerSync initialization error but don't fail login
                // User can still use the app, sync will retry later
                e.printStackTrace()
            }
            
            // Connect to Socket.IO for real-time messaging
            // Now that PowerSync has synced, GlobalMessageHandler will have user data ready
            try {
                socketManager.connect()
                // Start network monitoring for automatic reconnection
                socketConnectionManager.startMonitoring()
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

