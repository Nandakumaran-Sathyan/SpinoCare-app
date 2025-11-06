package com.example.modicanalyzer

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Remote model analyzer that sends images to FastAPI backend for inference
 */
class RemoteModelAnalyzer(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val TAG = "RemoteAnalyzer"
        private const val DEFAULT_SERVER_URL = "https://modic.onrender.com"
        private const val PREDICT_ENDPOINT = "/predict"
        private const val IMAGE_COMPRESSION_QUALITY = 85 // JPEG quality for faster upload
    }
    
    @Serializable
    data class DetailedScores(
        val no_modic: Float,
        val modic: Float
    )
    
    @Serializable
    data class PredictionResponse(
        val prediction: Float,
        val label: String,
        val confidence: Float? = null,
        val detailed_scores: DetailedScores? = null,
        val processing_time_ms: Int? = null,
        val model_version: String? = null
    )
    
    /**
     * Analyze T1 and T2 images using remote inference
     */
    suspend fun analyze(t1Image: Bitmap, t2Image: Bitmap): AnalysisResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting remote analysis")
            
            // Compress images for faster upload
            val t1Bytes = compressImage(t1Image)
            val t2Bytes = compressImage(t2Image)
            
            Log.d(TAG, "Images compressed: T1=${t1Bytes.size}KB, T2=${t2Bytes.size}KB")
            
            // Create multipart request
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file_t1", "t1_image.jpg",
                    t1Bytes.toRequestBody("image/jpeg".toMediaType())
                )
                .addFormDataPart(
                    "file_t2", "t2_image.jpg", 
                    t2Bytes.toRequestBody("image/jpeg".toMediaType())
                )
                .build()
            
            val request = Request.Builder()
                .url("$DEFAULT_SERVER_URL$PREDICT_ENDPOINT")
                .post(requestBody)
                .addHeader("Accept", "application/json")
                .build()
            
            Log.d(TAG, "Sending request to: $DEFAULT_SERVER_URL$PREDICT_ENDPOINT")
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("Server error: ${response.code} ${response.message}")
            }
            
            val responseBody = response.body?.string() 
                ?: throw IOException("Empty response from server")
            
            Log.d(TAG, "Server response: $responseBody")
            
            // Parse JSON response using a lenient parser that ignores unknown keys
            val json = Json { ignoreUnknownKeys = true }
            val jsonResponse = json.decodeFromString<PredictionResponse>(responseBody)
            
            // Convert to AnalysisResult
            // Use detailed_scores if available for maximum accuracy
            if (jsonResponse.detailed_scores != null) {
                AnalysisResult.fromDetailedScores(
                    noModicScore = jsonResponse.detailed_scores.no_modic,
                    modicScore = jsonResponse.detailed_scores.modic,
                    label = jsonResponse.label
                )
            } else {
                AnalysisResult.fromServerResponse(jsonResponse.prediction, jsonResponse.label)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Remote analysis failed", e)
            AnalysisResult.error(
                when (e) {
                    is IOException -> "Network error: ${e.message}"
                    is SerializationException -> "Server response error: ${e.message}"
                    else -> "Remote analysis failed: ${e.message}"
                },
                "online"
            )
        }
    }
    
    /**
     * Compress bitmap to JPEG bytes for faster upload
     */
    private fun compressImage(bitmap: Bitmap): ByteArray {
        // Resize to standard input size to reduce upload time
        val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        
        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, IMAGE_COMPRESSION_QUALITY, outputStream)
        
        // Clean up if we created a new bitmap
        if (resized != bitmap) {
            resized.recycle()
        }
        
        return outputStream.toByteArray()
    }
    
    /**
     * Test server connectivity
     */
    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$DEFAULT_SERVER_URL/status")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "Connected"
                Result.success("Server online: $body")
            } else {
                Result.failure(IOException("Server responded with: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        // OkHttp client cleanup is handled automatically
        Log.d(TAG, "RemoteModelAnalyzer cleaned up")
    }
}