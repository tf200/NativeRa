package com.taha.newraapp.data.repository

import android.util.Log
import com.taha.newraapp.data.model.request.InitiateCallRequest
import com.taha.newraapp.data.model.response.InitiateCallResponse
import com.taha.newraapp.data.network.AuthenticatedApiExecutor
import com.taha.newraapp.data.network.CallApi
import com.taha.newraapp.domain.repository.CallRepository

/**
 * Implementation of CallRepository.
 * Handles call-related API calls with automatic token refresh.
 */
class CallRepositoryImpl(
    private val callApi: CallApi,
    private val apiExecutor: AuthenticatedApiExecutor
) : CallRepository {
    
    companion object {
        private const val TAG = "CallRepository"
    }
    
    override suspend fun initiateCall(
        calleeId: String,
        roomId: String,
        callType: String
    ): Result<InitiateCallResponse> {
        return try {
            Log.d(TAG, "Initiating $callType call to $calleeId with room $roomId")
            
            val request = InitiateCallRequest(
                calleeId = calleeId,
                roomId = roomId,
                callType = callType
            )
            
            val response = apiExecutor.executeWithBearer { authorization ->
                callApi.initiateCall(authorization, request)
            }
            
            Log.d(TAG, "Call initiated successfully: callId=${response.callId}, status=${response.status}")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate call", e)
            Result.failure(e)
        }
    }
}
