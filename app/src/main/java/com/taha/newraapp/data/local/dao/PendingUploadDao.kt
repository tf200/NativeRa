package com.taha.newraapp.data.local.dao

import androidx.room.*
import com.taha.newraapp.data.local.entities.PendingUploadEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for managing pending file uploads.
 */
@Dao
interface PendingUploadDao {
    
    /**
     * Get all uploads that need processing (pending or failed).
     */
    @Query("SELECT * FROM pending_uploads WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    fun getPendingUploads(): Flow<List<PendingUploadEntity>>
    
    /**
     * Get upload by message ID.
     */
    @Query("SELECT * FROM pending_uploads WHERE messageId = :messageId")
    suspend fun getByMessageId(messageId: String): PendingUploadEntity?
    
    /**
     * Get upload by message ID as Flow (for observing).
     */
    @Query("SELECT * FROM pending_uploads WHERE messageId = :messageId")
    fun observeByMessageId(messageId: String): Flow<PendingUploadEntity?>
    
    /**
     * Insert a new pending upload.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(upload: PendingUploadEntity)
    
    /**
     * Update an existing upload.
     */
    @Update
    suspend fun update(upload: PendingUploadEntity)
    
    /**
     * Update upload progress.
     */
    @Query("UPDATE pending_uploads SET status = :status, uploadedBytes = :bytes WHERE messageId = :messageId")
    suspend fun updateProgress(messageId: String, status: String, bytes: Long)
    
    /**
     * Update with presigned URL info from request-upload.
     */
    @Query("UPDATE pending_uploads SET uploadUrl = :uploadUrl, fileKey = :fileKey, expiresAt = :expiresAt, status = 'UPLOADING' WHERE messageId = :messageId")
    suspend fun updateWithUploadUrl(messageId: String, uploadUrl: String, fileKey: String, expiresAt: String)
    
    /**
     * Mark upload as complete with attachment ID.
     */
    @Query("UPDATE pending_uploads SET attachmentId = :attachmentId, status = 'DONE' WHERE messageId = :messageId")
    suspend fun markComplete(messageId: String, attachmentId: String)
    
    /**
     * Mark upload as failed with error.
     */
    @Query("UPDATE pending_uploads SET status = 'FAILED', error = :error WHERE messageId = :messageId")
    suspend fun markFailed(messageId: String, error: String)
    
    /**
     * Delete a completed or cancelled upload.
     */
    @Query("DELETE FROM pending_uploads WHERE messageId = :messageId")
    suspend fun deleteByMessageId(messageId: String)
    
    /**
     * Delete upload entity.
     */
    @Delete
    suspend fun delete(upload: PendingUploadEntity)
    
    /**
     * Get all completed uploads (for cleanup).
     */
    @Query("SELECT * FROM pending_uploads WHERE status = 'DONE'")
    suspend fun getCompletedUploads(): List<PendingUploadEntity>
    
    /**
     * Clear all pending uploads.
     * Call this on sign-out.
     */
    @Query("DELETE FROM pending_uploads")
    suspend fun clearAll()
}
