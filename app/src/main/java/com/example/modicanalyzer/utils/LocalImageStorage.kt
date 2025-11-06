package com.example.modicanalyzer.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Utility for managing local image file storage.
 * 
 * Handles:
 * - Saving bitmaps to app's internal storage
 * - Loading bitmaps from storage
 * - Deleting cached images
 * - Compression for efficient storage
 * 
 * Storage location: /data/data/com.example.modicanalyzer/files/pending_uploads/
 */
object LocalImageStorage {
    
    private const val TAG = "LocalImageStorage"
    private const val PENDING_UPLOADS_DIR = "pending_uploads"
    
    /**
     * Save a bitmap to local storage.
     * 
     * @param context Application context
     * @param bitmap Image to save
     * @param prefix Filename prefix (e.g., "t1", "t2")
     * @return File path of saved image
     */
    fun saveBitmap(context: Context, bitmap: Bitmap, prefix: String = "image"): String {
        val dir = File(context.filesDir, PENDING_UPLOADS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        
        val filename = "${prefix}_${UUID.randomUUID()}.jpg"
        val file = File(dir, filename)
        
        try {
            FileOutputStream(file).use { out ->
                // Compress to JPEG with 85% quality (balance between quality and size)
                val compressedBytes = ImageCompressionUtil.compressForStorage(bitmap)
                out.write(compressedBytes)
            }
            
            Log.d(TAG, "✅ Saved image: ${file.absolutePath} (${file.length() / 1024}KB)")
            return file.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save bitmap", e)
            throw e
        }
    }
    
    /**
     * Load a bitmap from local storage.
     * 
     * @param filePath Absolute file path
     * @return Bitmap or null if file doesn't exist/failed to load
     */
    fun loadBitmap(filePath: String): Bitmap? {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(TAG, "File not found: $filePath")
                return null
            }
            
            val bitmap = BitmapFactory.decodeFile(filePath)
            Log.d(TAG, "✅ Loaded bitmap: $filePath")
            bitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load bitmap from $filePath", e)
            null
        }
    }
    
    /**
     * Delete a local image file.
     * 
     * @param filePath Absolute file path
     * @return True if deleted successfully
     */
    fun deleteImage(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            val deleted = file.delete()
            
            if (deleted) {
                Log.d(TAG, "✅ Deleted image: $filePath")
            } else {
                Log.w(TAG, "⚠️ Failed to delete or file not found: $filePath")
            }
            
            deleted
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting image: $filePath", e)
            false
        }
    }
    
    /**
     * Get total size of pending uploads directory.
     * 
     * @param context Application context
     * @return Size in bytes
     */
    fun getPendingUploadsDirSize(context: Context): Long {
        val dir = File(context.filesDir, PENDING_UPLOADS_DIR)
        if (!dir.exists()) return 0L
        
        return dir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }
    
    /**
     * Clean up old cached images (optional maintenance).
     * Deletes images older than specified days.
     * 
     * @param context Application context
     * @param olderThanDays Delete files older than this many days
     * @return Number of files deleted
     */
    fun cleanupOldImages(context: Context, olderThanDays: Int = 7): Int {
        val dir = File(context.filesDir, PENDING_UPLOADS_DIR)
        if (!dir.exists()) return 0
        
        val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
        var deletedCount = 0
        
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    deletedCount++
                }
            }
        }
        
        Log.d(TAG, "🧹 Cleaned up $deletedCount old images")
        return deletedCount
    }
}
