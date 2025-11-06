package com.example.modicanalyzer.utils

import android.graphics.Bitmap
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Image Compression Utility
 * 
 * Compresses medical images to optimal sizes for:
 * 1. DL Model Input: 224x224x3 RGB (as per TFLite model requirements)
 * 2. Storage: Compressed JPEG for efficient Firebase Storage uploads
 * 
 * Features:
 * - Maintains aspect ratio with center cropping
 * - Configurable quality for storage optimization
 * - Efficient JPEG compression (60-80% quality range)
 */
object ImageCompressionUtil {
    
    private const val TAG = "ImageCompression"
    
    // Model input size as per TFLite requirements
    const val MODEL_INPUT_SIZE = 224
    
    // Storage compression settings
    const val STORAGE_JPEG_QUALITY = 75  // Balance between quality and file size
    const val STORAGE_MAX_DIMENSION = 512  // Max dimension for stored images
    
    /**
     * Compress image for model input (224x224)
     * 
     * @param bitmap Original bitmap
     * @return Bitmap resized to 224x224 with center cropping
     */
    fun compressForModel(bitmap: Bitmap): Bitmap {
        return resizeBitmapCenterCrop(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
    }
    
    /**
     * Compress image for Firebase Storage upload
     * 
     * @param bitmap Original bitmap
     * @param maxDimension Maximum width or height (default: 512px)
     * @param quality JPEG quality 0-100 (default: 75)
     * @return Compressed JPEG as ByteArray
     */
    fun compressForStorage(
        bitmap: Bitmap,
        maxDimension: Int = STORAGE_MAX_DIMENSION,
        quality: Int = STORAGE_JPEG_QUALITY
    ): ByteArray {
        // Resize to max dimension while maintaining aspect ratio
        val resized = resizeBitmapMaintainRatio(bitmap, maxDimension)
        
        // Compress to JPEG
        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val compressedBytes = outputStream.toByteArray()
        
        Log.d(TAG, "📦 Storage compression: ${bitmap.width}x${bitmap.height} → ${resized.width}x${resized.height}, ${compressedBytes.size / 1024}KB")
        
        return compressedBytes
    }
    
    /**
     * Resize bitmap to exact dimensions with center cropping
     * 
     * @param bitmap Original bitmap
     * @param targetWidth Target width
     * @param targetHeight Target height
     * @return Resized and cropped bitmap
     */
    private fun resizeBitmapCenterCrop(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        
        // Calculate scale to fill target dimensions (may crop)
        val scale = maxOf(
            targetWidth.toFloat() / originalWidth,
            targetHeight.toFloat() / originalHeight
        )
        
        // Calculate scaled dimensions
        val scaledWidth = (originalWidth * scale).toInt()
        val scaledHeight = (originalHeight * scale).toInt()
        
        // Scale bitmap
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        
        // Center crop to target dimensions
        val xOffset = (scaledWidth - targetWidth) / 2
        val yOffset = (scaledHeight - targetHeight) / 2
        
        val croppedBitmap = Bitmap.createBitmap(
            scaledBitmap,
            xOffset,
            yOffset,
            targetWidth,
            targetHeight
        )
        
        // Clean up intermediate bitmap if different from original
        if (scaledBitmap != bitmap && scaledBitmap != croppedBitmap) {
            scaledBitmap.recycle()
        }
        
        return croppedBitmap
    }
    
    /**
     * Resize bitmap maintaining aspect ratio (fit within max dimension)
     * 
     * @param bitmap Original bitmap
     * @param maxDimension Maximum width or height
     * @return Resized bitmap
     */
    private fun resizeBitmapMaintainRatio(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // Already smaller than max dimension
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        
        // Calculate scale to fit within max dimension
        val scale = minOf(
            maxDimension.toFloat() / width,
            maxDimension.toFloat() / height
        )
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Get estimated file size after compression
     * 
     * @param bitmap Original bitmap
     * @return Estimated compressed size in bytes
     */
    fun getEstimatedStorageSize(bitmap: Bitmap): Int {
        val resized = resizeBitmapMaintainRatio(bitmap, STORAGE_MAX_DIMENSION)
        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, STORAGE_JPEG_QUALITY, outputStream)
        return outputStream.size()
    }
    
    /**
     * Compress image with custom parameters
     * 
     * @param bitmap Original bitmap
     * @param width Target width
     * @param height Target height
     * @param quality JPEG quality 0-100
     * @return Compressed JPEG as ByteArray
     */
    fun compressCustom(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        quality: Int = 80
    ): ByteArray {
        val resized = resizeBitmapCenterCrop(bitmap, width, height)
        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }
}
