package com.example.modicanalyzer.data.remote

import android.graphics.Bitmap
import android.util.Log
import com.example.modicanalyzer.utils.ImageCompressionUtil
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Storage Helper
 * 
 * Handles image uploads to Firebase Storage with compression
 * 
 * Storage Structure:
 * /users/{userId}/images/
 *   - t1_{uuid}.jpg (T1-weighted image)
 *   - t2_{uuid}.jpg (T2-weighted image)
 * 
 * Features:
 * - Automatic compression to model-compatible sizes (224x224)
 * - Efficient JPEG compression (75% quality)
 * - Unique filename generation (UUID)
 * - Public download URLs
 * - Error handling with Result<T>
 */
@Singleton
class FirebaseStorageHelper @Inject constructor(
    private val storage: FirebaseStorage
) {
    
    companion object {
        private const val TAG = "StorageHelper"
        private const val IMAGES_PATH = "images"
    }
    
    /**
     * Upload T1 and T2 images for a user
     * 
     * @param userId Firebase Auth UID
     * @param t1Image T1-weighted MRI bitmap
     * @param t2Image T2-weighted MRI bitmap
     * @return Pair of download URLs (t1Url, t2Url)
     */
    suspend fun uploadMRIImages(
        userId: String,
        t1Image: Bitmap,
        t2Image: Bitmap
    ): Result<Pair<String, String>> {
        return try {
            Log.d(TAG, "📤 Uploading MRI images for user: $userId")
            
            // Upload both images concurrently would be better, but sequential is safer
            val t1Url = uploadImage(userId, t1Image, "t1")
            val t2Url = uploadImage(userId, t2Image, "t2")
            
            Log.d(TAG, "✅ Both images uploaded successfully")
            Result.success(Pair(t1Url, t2Url))
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to upload MRI images", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload a single image to Firebase Storage
     * 
     * @param userId Firebase Auth UID
     * @param bitmap Image bitmap
     * @param prefix Filename prefix (e.g., "t1", "t2")
     * @return Download URL
     */
    suspend fun uploadImage(
        userId: String,
        bitmap: Bitmap,
        prefix: String = "image"
    ): String {
        // Compress image for storage
        val compressedBytes = ImageCompressionUtil.compressForStorage(bitmap)
        
        // Generate unique filename
        val filename = "${prefix}_${UUID.randomUUID()}.jpg"
        val path = "users/$userId/$IMAGES_PATH/$filename"
        
        Log.d(TAG, "📤 Uploading: $path (${compressedBytes.size / 1024}KB)")
        
        // Get storage reference
        val storageRef: StorageReference = storage.reference.child(path)
        
        try {
            // Upload compressed image
            val uploadTask = storageRef.putBytes(compressedBytes)
            uploadTask.await()
            
            // Get download URL
            val downloadUrl = storageRef.downloadUrl.await().toString()
            
            Log.d(TAG, "✅ Upload complete: $filename")
            return downloadUrl
            
        } catch (e: Exception) {
            // Provide helpful error messages for common issues
            val errorMessage = when {
                e.message?.contains("404") == true || e.message?.contains("Not Found") == true -> {
                    "Firebase Storage rules not configured. Please update Storage rules in Firebase Console to allow uploads."
                }
                e.message?.contains("permission", ignoreCase = true) == true -> {
                    "Permission denied. Check Firebase Storage rules."
                }
                e.message?.contains("network", ignoreCase = true) == true -> {
                    "Network error. Please check your internet connection."
                }
                else -> e.message ?: "Upload failed"
            }
            
            Log.e(TAG, "❌ Upload failed: $errorMessage", e)
            throw Exception(errorMessage, e)
        }
    }
    
    /**
     * Upload single image and return Result
     * 
     * @param userId Firebase Auth UID
     * @param bitmap Image bitmap
     * @param prefix Filename prefix
     * @return Result with download URL
     */
    suspend fun uploadImageSafe(
        userId: String,
        bitmap: Bitmap,
        prefix: String = "image"
    ): Result<String> {
        return try {
            val url = uploadImage(userId, bitmap, prefix)
            Result.success(url)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload failed for $prefix", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete an image from Firebase Storage
     * 
     * @param imageUrl Full download URL of the image
     * @return Success or failure
     */
    suspend fun deleteImage(imageUrl: String): Result<Unit> {
        return try {
            val storageRef = storage.getReferenceFromUrl(imageUrl)
            storageRef.delete().await()
            Log.d(TAG, "🗑️ Image deleted: $imageUrl")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to delete image", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete both T1 and T2 images
     * 
     * @param t1Url T1 image URL
     * @param t2Url T2 image URL
     * @return Success or failure
     */
    suspend fun deleteMRIImages(t1Url: String, t2Url: String): Result<Unit> {
        return try {
            deleteImage(t1Url)
            deleteImage(t2Url)
            Log.d(TAG, "🗑️ Both MRI images deleted")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to delete MRI images", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all images for a user
     * 
     * @param userId Firebase Auth UID
     * @return List of download URLs
     */
    suspend fun getUserImages(userId: String): Result<List<String>> {
        return try {
            val path = "users/$userId/$IMAGES_PATH"
            val storageRef = storage.reference.child(path)
            
            val listResult = storageRef.listAll().await()
            val urls = listResult.items.map { it.downloadUrl.await().toString() }
            
            Log.d(TAG, "📋 Found ${urls.size} images for user: $userId")
            Result.success(urls)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get user images", e)
            Result.failure(e)
        }
    }
}
