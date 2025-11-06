package com.example.modicanalyzer.data.local.dao

import androidx.room.*
import com.example.modicanalyzer.data.local.entity.PendingImageUploadEntity
import com.example.modicanalyzer.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO for pending image upload operations.
 * 
 * Manages the queue of images waiting to be uploaded to Firebase Storage.
 */
@Dao
interface PendingImageUploadDao {
    
    /**
     * Insert a new pending upload job.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(upload: PendingImageUploadEntity)
    
    /**
     * Get all pending uploads (PENDING or FAILED status).
     */
    @Query("SELECT * FROM pending_image_uploads WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED' ORDER BY createdAt ASC")
    suspend fun getPendingUploads(): List<PendingImageUploadEntity>
    
    /**
     * Get pending uploads for a specific user.
     */
    @Query("SELECT * FROM pending_image_uploads WHERE userId = :userId AND (syncStatus = 'PENDING' OR syncStatus = 'FAILED') ORDER BY createdAt ASC")
    suspend fun getPendingUploadsByUser(userId: String): List<PendingImageUploadEntity>
    
    /**
     * Observe count of pending uploads for UI display.
     */
    @Query("SELECT COUNT(*) FROM pending_image_uploads WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    fun observePendingCount(): Flow<Int>
    
    /**
     * Get upload by ID.
     */
    @Query("SELECT * FROM pending_image_uploads WHERE id = :uploadId")
    suspend fun getUploadById(uploadId: String): PendingImageUploadEntity?
    
    /**
     * Update upload status.
     */
    @Query("UPDATE pending_image_uploads SET syncStatus = :status, uploadedAt = :uploadedAt, errorMessage = :errorMessage WHERE id = :uploadId")
    suspend fun updateStatus(uploadId: String, status: SyncStatus, uploadedAt: Long?, errorMessage: String?)
    
    /**
     * Update upload URLs after successful upload.
     */
    @Query("UPDATE pending_image_uploads SET t1UploadUrl = :t1Url, t2UploadUrl = :t2Url, syncStatus = 'SYNCED', uploadedAt = :uploadedAt WHERE id = :uploadId")
    suspend fun updateUploadUrls(uploadId: String, t1Url: String, t2Url: String, uploadedAt: Long)
    
    /**
     * Increment retry count.
     */
    @Query("UPDATE pending_image_uploads SET retryCount = retryCount + 1 WHERE id = :uploadId")
    suspend fun incrementRetryCount(uploadId: String)
    
    /**
     * Delete upload job (after successful upload).
     */
    @Delete
    suspend fun delete(upload: PendingImageUploadEntity)
    
    /**
     * Delete all synced uploads (cleanup).
     */
    @Query("DELETE FROM pending_image_uploads WHERE syncStatus = 'SYNCED'")
    suspend fun deleteAllSynced()
    
    /**
     * Get all uploads for a user (for debugging/history).
     */
    @Query("SELECT * FROM pending_image_uploads WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getAllUploadsByUser(userId: String): List<PendingImageUploadEntity>
}
