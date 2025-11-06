package com.example.modicanalyzer.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.modicanalyzer.data.local.dao.PendingImageUploadDao
import com.example.modicanalyzer.data.model.SyncStatus
import com.example.modicanalyzer.data.remote.FirebaseStorageHelper
import com.example.modicanalyzer.data.remote.FirestoreHelper
import com.example.modicanalyzer.utils.LocalImageStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker for uploading queued images to Firebase Storage.
 * 
 * Triggered when:
 * - Device connectivity changes (offline → online)
 * - Periodically via WorkManager
 * - Manually via "Sync Now" action
 * 
 * Process:
 * 1. Load pending upload jobs from Room
 * 2. For each job:
 *    - Load T1/T2 images from local storage
 *    - Upload to Firebase Storage
 *    - Update Firestore analysis entry with image URLs
 *    - Mark upload as SYNCED and delete local files
 * 3. Retry failed uploads with exponential backoff
 * 
 * @param context Application context
 * @param params Worker parameters
 * @param uploadDao DAO for pending uploads
 * @param storageHelper Firebase Storage helper
 * @param firestoreHelper Firestore helper for updating analysis entries
 */
@HiltWorker
class ImageUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val uploadDao: PendingImageUploadDao,
    private val storageHelper: FirebaseStorageHelper,
    private val firestoreHelper: FirestoreHelper
) : CoroutineWorker(context, params) {
    
    companion object {
        const val WORK_NAME = "image_upload_work"
        const val TAG = "ImageUploadWorker"
        const val MAX_RETRIES = 3
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 Starting image upload worker")
            
            val pendingUploads = uploadDao.getPendingUploads()
            
            if (pendingUploads.isEmpty()) {
                Log.d(TAG, "✅ No pending uploads")
                return@withContext Result.success()
            }
            
            Log.d(TAG, "📤 Processing ${pendingUploads.size} pending uploads")
            
            var successCount = 0
            var failCount = 0
            
            for (upload in pendingUploads) {
                try {
                    // Check retry limit
                    if (upload.retryCount >= MAX_RETRIES) {
                        Log.w(TAG, "⚠️ Upload ${upload.id} exceeded max retries, skipping")
                        failCount++
                        continue
                    }
                    
                    // Update status to SYNCING
                    uploadDao.updateStatus(upload.id, SyncStatus.SYNCING, null, null)
                    
                    // Load images from local storage
                    val t1Bitmap = LocalImageStorage.loadBitmap(upload.t1ImagePath)
                    val t2Bitmap = LocalImageStorage.loadBitmap(upload.t2ImagePath)
                    
                    if (t1Bitmap == null || t2Bitmap == null) {
                        val errorMsg = "Failed to load local images"
                        Log.e(TAG, "❌ $errorMsg for upload ${upload.id}")
                        uploadDao.updateStatus(upload.id, SyncStatus.FAILED, null, errorMsg)
                        uploadDao.incrementRetryCount(upload.id)
                        failCount++
                        continue
                    }
                    
                    // Upload images to Firebase Storage
                    val uploadResult = storageHelper.uploadMRIImages(upload.userId, t1Bitmap, t2Bitmap)
                    
                    if (uploadResult.isSuccess) {
                        val (t1Url, t2Url) = uploadResult.getOrThrow()
                        
                        // Update upload record with URLs
                        uploadDao.updateUploadUrls(
                            uploadId = upload.id,
                            t1Url = t1Url,
                            t2Url = t2Url,
                            uploadedAt = System.currentTimeMillis()
                        )
                        
                        // Update Firestore analysis entry with image URLs
                        try {
                            firestoreHelper.updateAnalysisImageUrls(
                                analysisId = upload.analysisId,
                                t1Url = t1Url,
                                t2Url = t2Url
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "⚠️ Failed to update Firestore with URLs, but upload succeeded", e)
                            // Don't fail the whole job if Firestore update fails
                        }
                        
                        // Delete local files to free space
                        LocalImageStorage.deleteImage(upload.t1ImagePath)
                        LocalImageStorage.deleteImage(upload.t2ImagePath)
                        
                        // Delete upload record from DB (or keep for history)
                        uploadDao.delete(upload)
                        
                        Log.d(TAG, "✅ Upload ${upload.id} completed successfully")
                        successCount++
                        
                    } else {
                        val error = uploadResult.exceptionOrNull()
                        val errorMsg = error?.message ?: "Upload failed"
                        
                        Log.e(TAG, "❌ Upload ${upload.id} failed: $errorMsg", error)
                        
                        uploadDao.updateStatus(upload.id, SyncStatus.FAILED, null, errorMsg)
                        uploadDao.incrementRetryCount(upload.id)
                        failCount++
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing upload ${upload.id}", e)
                    uploadDao.updateStatus(upload.id, SyncStatus.FAILED, null, e.message)
                    uploadDao.incrementRetryCount(upload.id)
                    failCount++
                }
            }
            
            Log.d(TAG, "📊 Upload worker finished: $successCount succeeded, $failCount failed")
            
            // Return success if at least some uploads succeeded, or retry if all failed
            return@withContext if (successCount > 0 || failCount == 0) {
                Result.success()
            } else {
                Result.retry()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Worker failed with exception", e)
            return@withContext Result.retry()
        }
    }
}
