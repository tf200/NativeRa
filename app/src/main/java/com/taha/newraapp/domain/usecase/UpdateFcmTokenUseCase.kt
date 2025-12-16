package com.taha.newraapp.domain.usecase

import android.util.Log
import com.taha.newraapp.data.local.FcmTokenManager
import com.taha.newraapp.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first

class UpdateFcmTokenUseCase(
    private val fcmTokenManager: FcmTokenManager,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val TAG = "FCM_DEBUG"
    }
    
    suspend operator fun invoke(): Result<Unit> {
        Log.w(TAG, "UpdateFcmTokenUseCase: invoke() started")
        
        // 1. Try to get token from Firebase directly
        Log.w(TAG, "UpdateFcmTokenUseCase: Getting current FCM token from Firebase...")
        val currentToken = fcmTokenManager.getCurrentFcmToken()
        Log.w(TAG, "UpdateFcmTokenUseCase: currentToken from Firebase = ${currentToken?.take(20) ?: "NULL"}...")
        
        // 2. Fallback to stored token if direct retrieval fails
        val tokenToUpdate = if (currentToken != null) {
            currentToken
        } else {
            Log.w(TAG, "UpdateFcmTokenUseCase: Firebase token null, trying stored token...")
            val storedToken = fcmTokenManager.getStoredToken()
            Log.w(TAG, "UpdateFcmTokenUseCase: storedToken = ${storedToken?.take(20) ?: "NULL"}...")
            storedToken
        }
        
        return if (tokenToUpdate != null) {
            Log.w(TAG, "UpdateFcmTokenUseCase: Token available, saving locally...")
            // 3. Save purely to ensure we have the latest one stored locally
            fcmTokenManager.saveToken(tokenToUpdate)
            
            // 4. Send to backend
            Log.w(TAG, "UpdateFcmTokenUseCase: Calling authRepository.updateFcmToken()...")
            val result = authRepository.updateFcmToken(tokenToUpdate)
            Log.w(TAG, "UpdateFcmTokenUseCase: API result isSuccess=${result.isSuccess}, error=${result.exceptionOrNull()?.message}")
            result
        } else {
            Log.e(TAG, "UpdateFcmTokenUseCase: NO FCM TOKEN AVAILABLE - API NOT CALLED!")
            Result.failure(Exception("No FCM token available"))
        }
    }
}
