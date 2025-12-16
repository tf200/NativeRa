package com.taha.newraapp.data.network

import android.util.Log
import com.taha.newraapp.data.local.TokenManager
import com.taha.newraapp.data.model.request.RefreshTokenRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

/**
 * Authenticated API Executor
 * 
 * A wrapper that handles authentication and automatic token refresh for API calls.
 * Use this for any API call that requires authentication.
 * 
 * Usage:
 * ```
 * val result = apiExecutor.execute { token ->
 *     api.someAuthenticatedCall("Bearer $token")
 * }
 * ```
 */
class AuthenticatedApiExecutor(
    private val tokenManager: TokenManager,
    private val authApi: AuthApi
) {
    companion object {
        private const val TAG = "SESSION_DEBUG"
    }
    
    // Mutex to prevent concurrent token refresh attempts
    // This prevents race conditions where multiple 401s could cause token loss
    private val refreshMutex = Mutex()
    
    // Track the token that was used before refresh to detect if refresh already happened
    @Volatile
    private var lastRefreshedFromToken: String? = null
    
    /**
     * Execute an authenticated API call with automatic token refresh on 401 errors.
     * 
     * @param apiCall A lambda that takes the access token and makes the API call
     * @return The result of the API call
     * @throws IllegalStateException if no tokens are available
     * @throws HttpException if the API call fails after retry
     */
    suspend fun <T> execute(apiCall: suspend (accessToken: String) -> T): T {
        var accessToken = tokenManager.accessToken.first()
            ?: throw IllegalStateException("No access token available")
        
        return try {
            apiCall(accessToken)
        } catch (e: HttpException) {
            if (e.code() == 401) {
                // Token expired, try to refresh (with synchronization)
                accessToken = refreshAccessTokenSafely(accessToken)
                // Retry with new access token
                apiCall(accessToken)
            } else {
                throw e
            }
        }
    }
    
    /**
     * Execute an authenticated API call that requires Bearer format.
     * Automatically prepends "Bearer " to the token.
     * 
     * @param apiCall A lambda that takes the formatted "Bearer {token}" and makes the API call
     * @return The result of the API call
     */
    suspend fun <T> executeWithBearer(apiCall: suspend (authorization: String) -> T): T {
        return execute { token ->
            apiCall("Bearer $token")
        }
    }
    
    /**
     * Safely refresh the access token with synchronization to prevent race conditions.
     * If another coroutine is already refreshing, this will wait and reuse the new token.
     * 
     * @param expiredToken The token that caused the 401 error
     * @return The new access token
     */
    private suspend fun refreshAccessTokenSafely(expiredToken: String): String {
        return refreshMutex.withLock {
            // Check if another coroutine already refreshed while we were waiting
            val currentToken = tokenManager.accessToken.first()
            if (currentToken != null && currentToken != expiredToken) {
                // Token was already refreshed by another coroutine, use the new one
                return@withLock currentToken
            }
            
            // Also check if we already refreshed from this exact token
            if (lastRefreshedFromToken == expiredToken && currentToken != null) {
                return@withLock currentToken
            }
            
            // We need to perform the refresh
            refreshAccessToken(expiredToken)
        }
    }
    
    /**
     * Refresh the access token using the refresh token.
     * Saves the new tokens and returns the new access token.
     * 
     * MUST be called within refreshMutex.withLock to ensure thread safety.
     */
    private suspend fun refreshAccessToken(expiredToken: String): String {
        Log.d(TAG, "=== TOKEN REFRESH ATTEMPT ===")
        Log.d(TAG, "Expired access token (last 10 chars): ...${expiredToken.takeLast(10)}")
        
        val refreshToken = tokenManager.refreshToken.first()
        if (refreshToken == null) {
            Log.e(TAG, "❌ REFRESH FAILED: No refresh token in storage!")
            throw IllegalStateException("No refresh token available - please login again")
        }
        Log.d(TAG, "Refresh token found (last 10 chars): ...${refreshToken.takeLast(10)}")
        
        val deviceId = tokenManager.deviceId.first()
        if (deviceId == null) {
            Log.e(TAG, "❌ REFRESH FAILED: No device ID in storage!")
            throw IllegalStateException("No device ID available - please login again")
        }
        Log.d(TAG, "Device ID: $deviceId")
        
        return try {
            val request = RefreshTokenRequest(
                refreshToken = refreshToken,
                deviceId = deviceId
            )
            Log.d(TAG, "Calling /auth/refresh API...")
            val response = authApi.refreshToken(request)
            Log.d(TAG, "✅ REFRESH SUCCESS! New access token received")
            
            // Save new tokens
            tokenManager.saveTokens(response.accessToken, response.refreshToken)
            Log.d(TAG, "New tokens saved to storage")
            
            // Track that we refreshed from this token
            lastRefreshedFromToken = expiredToken
            
            response.accessToken
        } catch (e: HttpException) {
            Log.e(TAG, "❌ REFRESH API ERROR: HTTP ${e.code()}")
            Log.e(TAG, "Response: ${e.response()?.errorBody()?.string()}")
            if (e.code() == 401) {
                Log.e(TAG, "🚨 CLEARING TOKENS - Refresh token rejected by server!")
                // Refresh token also expired - user needs to login again
                tokenManager.clearTokens()
                throw TokenExpiredException("Session expired. Please login again.")
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ REFRESH EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }
}

/**
 * Exception thrown when both access and refresh tokens have expired.
 * The user needs to login again.
 */
class TokenExpiredException(message: String) : Exception(message)
