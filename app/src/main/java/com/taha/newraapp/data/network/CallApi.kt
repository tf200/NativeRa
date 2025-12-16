package com.taha.newraapp.data.network

import com.taha.newraapp.data.model.request.InitiateCallRequest
import com.taha.newraapp.data.model.response.InitiateCallResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface CallApi {
    @POST("calls/initiate")
    suspend fun initiateCall(
        @Header("Authorization") authorization: String,
        @Body request: InitiateCallRequest
    ): InitiateCallResponse
}
