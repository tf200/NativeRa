package com.taha.newraapp.data.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface for attachment-related API calls.
 * 
 * Endpoints:
 * - POST /api/v2/attachments/request-upload: Get presigned S3 URL for upload
 * - POST /api/v2/attachments/confirm-upload: Confirm upload and create attachment record
 * - GET /api/v2/attachments/{attachmentId}: Get presigned download URL
 */
interface AttachmentApi {
    
    /**
     * Request a presigned URL for direct S3 upload.
     * 
     * @param authorization Bearer token
     * @param request Upload request with file metadata
     * @return Presigned URL and file key
     */
    @POST("attachments/request-upload")
    suspend fun requestUpload(
        @Header("Authorization") authorization: String,
        @Body request: RequestUploadBody
    ): RequestUploadResponse
    
    /**
     * Confirm upload is complete and create attachment record.
     * 
     * @param authorization Bearer token
     * @param request Confirmation with file key and metadata
     * @return Created attachment with ID
     */
    @POST("attachments/confirm-upload")
    suspend fun confirmUpload(
        @Header("Authorization") authorization: String,
        @Body request: ConfirmUploadBody
    ): ConfirmUploadResponse
    
    /**
     * Get presigned download URL for an attachment.
     * 
     * @param authorization Bearer token
     * @param attachmentId The attachment ID
     * @return Presigned download URL and file metadata
     */
    @GET("attachments/{attachmentId}")
    suspend fun getDownloadUrl(
        @Header("Authorization") authorization: String,
        @Path("attachmentId") attachmentId: String
    ): DownloadResponse
}

// ================================
// Request/Response DTOs
// ================================

/**
 * Request body for presigned URL.
 * Matches server REQUEST_UPLOAD_BODY_SCHEMA.
 */
@Serializable
data class RequestUploadBody(
    val filename: String,
    val mimeType: String,
    val fileType: String,  // IMAGE, VIDEO, AUDIO, FILE
    val size: Long         // Max 50MB
)

/**
 * Response from request-upload endpoint.
 * Matches server REQUEST_UPLOAD_RESPONSE_SCHEMA.
 */
@Serializable
data class RequestUploadResponse(
    val uploadUrl: String,
    val key: String,
    val expiresAt: String  // ISO 8601 datetime
)

/**
 * Request body for confirming upload.
 * Matches server CONFIRM_UPLOAD_BODY_SCHEMA.
 */
@Serializable
data class ConfirmUploadBody(
    val key: String,
    val filename: String,
    val mimeType: String,
    val fileType: String,  // IMAGE, VIDEO, AUDIO, FILE
    val size: Long
)

/**
 * Response from confirm-upload endpoint.
 */
@Serializable
data class ConfirmUploadResponse(
    val id: String,        // The attachment ID to use in messages
    val url: String? = null // Optional: CDN URL for the file
)

/**
 * Response from download endpoint.
 * Matches server DOWNLOAD_RESPONSE_SCHEMA.
 */
@Serializable
data class DownloadResponse(
    val downloadUrl: String,
    val filename: String,
    val mimeType: String,
    val fileType: String,  // IMAGE, VIDEO, AUDIO, FILE
    val size: Long,
    val expiresAt: String  // ISO 8601 datetime
)
