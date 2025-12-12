package com.taha.newraapp.data.network

import com.taha.newraapp.data.model.PresenceStatusResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Retrofit interface for chat-related API calls.
 */
interface ChatApi {
    /**
     * Get presence status for multiple users.
     * 
     * @param authorization Bearer token
     * @param userIds Comma-separated list of user IDs
     * @return Map of userId to UserPresenceStatus
     */
    @GET("chat/status")
    suspend fun getPresenceStatus(
        @Header("Authorization") authorization: String,
        @Query("userIds") userIds: String
    ): PresenceStatusResponse
}
