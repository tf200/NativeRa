package com.taha.newraapp.data.network

import com.taha.newraapp.data.local.TokenManager
import kotlinx.coroutines.flow.first
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
                // Token expired, try to refresh
                accessToken = refreshAccessToken()
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
     * Refresh the access token using the refresh token.
     * Saves the new tokens and returns the new access token.
     */
    private suspend fun refreshAccessToken(): String {
        val refreshToken = tokenManager.refreshToken.first()
            ?: throw IllegalStateException("No refresh token available - please login again")
        
        return try {
            val response = authApi.refreshToken("Bearer $refreshToken")
            
            // Save new tokens
            tokenManager.saveTokens(response.accessToken, response.refreshToken)
            
            response.accessToken
        } catch (e: HttpException) {
            if (e.code() == 401) {
                // Refresh token also expired - user needs to login again
                tokenManager.clearTokens()
                throw TokenExpiredException("Session expired. Please login again.")
            }
            throw e
        }
    }
}

/**
 * Exception thrown when both access and refresh tokens have expired.
 * The user needs to login again.
 */
class TokenExpiredException(message: String) : Exception(message)
