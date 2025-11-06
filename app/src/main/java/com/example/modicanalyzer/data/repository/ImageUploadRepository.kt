package com.example.modicanalyzer.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.modicanalyzer.data.local.dao.PendingImageUploadDao
import com.example.modicanalyzer.data.local.entity.PendingImageUploadEntity
import com.example.modicanalyzer.data.model.SyncStatus
import com.example.modicanalyzer.utils.LocalImageStorage
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing offline image upload queue.
 * 
 * Handles:
 * - Queuing images when offline or upload fails
 * - Saving images to local storage
 * - Tracking upload status
 * - Cleanup after successful upload
 * 
 * Flow:
 * 1. User performs analysis → Queue images if offline
 * 2. Images saved to app's internal storage
 * 3. Upload job tracked in Room database
 * 4. ImageUploadWorker processes queue when online
 * 5. After success, local files deleted and job removed
 */
@Singleton
class ImageUploadRepository @Inject constructor(
    private val uploadDao: PendingImageUploadDao,
    private val context: Context
) {
    
    companion object {
        private const val TAG = "ImageUploadRepo"
    }
    
    /**
     * Queue images for upload.
     * Saves images to local storage and creates upload job.
     * 
     * @param userId User's Firebase UID
     * @param analysisId Analysis entry ID from Firestore
     * @param t1Image T1-weighted MRI bitmap
     * @param t2Image T2-weighted MRI bitmap
     * @return Upload job ID
     */
    suspend fun queueImageUpload(
        userId: String,
        analysisId: String,
        t1Image: Bitmap,
        t2Image: Bitmap
    ): Result<String> {
        return try {
            // Save images to local storage
            val t1Path = LocalImageStorage.saveBitmap(context, t1Image, "t1")
            val t2Path = LocalImageStorage.saveBitmap(context, t2Image, "t2")
            
            // Create upload job
            val uploadId = UUID.randomUUID().toString()
            val uploadEntity = PendingImageUploadEntity(
                id = uploadId,
                userId = userId,
                analysisId = analysisId,
                t1ImagePath = t1Path,
                t2ImagePath = t2Path,
                syncStatus = SyncStatus.PENDING
            )
            
            uploadDao.insert(uploadEntity)
            
            Log.d(TAG, "✅ Images queued for upload: $uploadId")
            Result.success(uploadId)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to queue image upload", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all pending uploads.
     */
    suspend fun getPendingUploads(): List<PendingImageUploadEntity> {
        return uploadDao.getPendingUploads()
    }
    
    /**
     * Get pending uploads for a specific user.
     */
    suspend fun getPendingUploadsByUser(userId: String): List<PendingImageUploadEntity> {
        return uploadDao.getPendingUploadsByUser(userId)
    }
    
    /**
     * Observe count of pending uploads for UI display.
     */
    fun observePendingCount(): Flow<Int> {
        return uploadDao.observePendingCount()
    }
    
    /**
     * Get upload by ID.
     */
    suspend fun getUploadById(uploadId: String): PendingImageUploadEntity? {
        return uploadDao.getUploadById(uploadId)
    }
    
    /**
     * Mark upload as synced and clean up local files.
     */
    suspend fun markUploadCompleted(uploadId: String, t1Url: String, t2Url: String) {
        uploadDao.updateUploadUrls(
            uploadId = uploadId,
            t1Url = t1Url,
            t2Url = t2Url,
            uploadedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Mark upload as failed.
     */
    suspend fun markUploadFailed(uploadId: String, errorMessage: String) {
        uploadDao.updateStatus(
            uploadId = uploadId,
            status = SyncStatus.FAILED,
            uploadedAt = null,
            errorMessage = errorMessage
        )
    }
    
    /**
     * Delete completed uploads and clean up old cached images.
     */
    suspend fun cleanupCompletedUploads() {
        try {
            uploadDao.deleteAllSynced()
            val deletedCount = LocalImageStorage.cleanupOldImages(context, olderThanDays = 7)
            Log.d(TAG, "🧹 Cleaned up synced uploads and $deletedCount old images")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Cleanup failed", e)
        }
    }
    
    /**
     * Get storage usage of pending uploads.
     */
    fun getPendingUploadsSize(): Long {
        return LocalImageStorage.getPendingUploadsDirSize(context)
    }
}
