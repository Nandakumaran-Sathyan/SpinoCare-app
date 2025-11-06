package com.example.modicanalyzer.model

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Automatic Model Update Manager
 * Handles automatic model downloading when network is available
 * and checking for model updates based on hash comparison
 */
class ModelUpdateManager(
    private val context: Context,
    private val serverUrl: String = "https://modic.onrender.com"
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("model_update_prefs", Context.MODE_PRIVATE)
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private var updateJob: Job? = null
    
    companion object {
        private const val TAG = "ModelUpdateManager"
        private const val MODEL_FILENAME = "modic_model_offline.tflite"
        private const val PREF_LAST_MODEL_HASH = "last_model_hash"
        private const val PREF_LAST_CHECK_TIME = "last_check_time"
        private const val PREF_AUTO_UPDATE_ENABLED = "auto_update_enabled"
        private const val CHECK_INTERVAL_HOURS = 6 // Check every 6 hours
        private const val CHECK_INTERVAL_MS = CHECK_INTERVAL_HOURS * 60 * 60 * 1000L
    }
    
    @Serializable
    data class ModelInfo(
        val model_hash: String,
        val model_version: String,
        val model_size_bytes: Long,
        val model_size_mb: Double,
        val last_modified: Double,
        val download_url: String,
        val server_time: Double
    )
    
    interface ModelUpdateListener {
        fun onUpdateCheckStarted()
        fun onUpdateAvailable(newVersion: String, sizeMB: Double)
        fun onDownloadStarted()
        fun onDownloadProgress(progress: Int)
        fun onDownloadCompleted(success: Boolean)
        fun onNoUpdateNeeded()
        fun onUpdateError(error: String)
    }
    
    private var updateListener: ModelUpdateListener? = null
    
    /**
     * Set update listener for callbacks
     */
    fun setUpdateListener(listener: ModelUpdateListener?) {
        this.updateListener = listener
    }
    
    /**
     * Enable or disable automatic updates
     */
    fun setAutoUpdateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_UPDATE_ENABLED, enabled).apply()
        Log.d(TAG, "Auto-update ${if (enabled) "enabled" else "disabled"}")
        
        if (enabled) {
            startPeriodicUpdateCheck()
        } else {
            stopPeriodicUpdateCheck()
        }
    }
    
    /**
     * Check if auto-update is enabled
     */
    fun isAutoUpdateEnabled(): Boolean {
        return prefs.getBoolean(PREF_AUTO_UPDATE_ENABLED, true) // Default enabled
    }
    
    /**
     * Start periodic update checking
     */
    fun startPeriodicUpdateCheck() {
        if (!isAutoUpdateEnabled()) return
        
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                try {
                    if (isNetworkAvailable()) {
                        checkForUpdatesIfNeeded()
                    } else {
                        Log.d(TAG, "Network not available, skipping update check")
                    }
                    
                    delay(CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic update check", e)
                    delay(60000) // Wait 1 minute before retrying on error
                }
            }
        }
        
        Log.d(TAG, "Started periodic update checking (every $CHECK_INTERVAL_HOURS hours)")
    }
    
    /**
     * Stop periodic update checking
     */
    fun stopPeriodicUpdateCheck() {
        updateJob?.cancel()
        updateJob = null
        Log.d(TAG, "Stopped periodic update checking")
    }
    
    /**
     * Check if network is available and suitable for downloading
     */
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for network access: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network availability: ${e.message}")
            false
        }
    }
    
    /**
     * Check for updates only if enough time has passed since last check
     */
    private suspend fun checkForUpdatesIfNeeded() {
        val lastCheckTime = prefs.getLong(PREF_LAST_CHECK_TIME, 0)
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastCheckTime < CHECK_INTERVAL_MS) {
            Log.d(TAG, "Too soon to check for updates, skipping")
            return
        }
        
        checkForUpdates()
    }
    
    /**
     * Force check for model updates
     */
    suspend fun checkForUpdates(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for model updates...")
            updateListener?.onUpdateCheckStarted()
            
            val modelInfo = getModelInfo()
            val currentHash = getCurrentModelHash()
            
            // Update last check time
            prefs.edit().putLong(PREF_LAST_CHECK_TIME, System.currentTimeMillis()).apply()
            
            if (currentHash != modelInfo.model_hash) {
                Log.d(TAG, "Model update available: ${modelInfo.model_version} (${modelInfo.model_size_mb}MB)")
                updateListener?.onUpdateAvailable(modelInfo.model_version, modelInfo.model_size_mb)
                
                // Automatically download if enabled
                if (isAutoUpdateEnabled()) {
                    downloadModelUpdate(modelInfo)
                }
                return@withContext true
            } else {
                Log.d(TAG, "Model is up to date (hash: ${currentHash.take(8)}...)")
                updateListener?.onNoUpdateNeeded()
                return@withContext false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            updateListener?.onUpdateError("Update check failed: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * Get model information from server
     */
    private suspend fun getModelInfo(): ModelInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$serverUrl/model_info")
            .get()
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("Server error: ${response.code}")
        }
        
        val responseBody = response.body?.string()
            ?: throw IOException("Empty response from server")
        
        Json.decodeFromString<ModelInfo>(responseBody)
    }
    
    /**
     * Get hash of current local model file
     */
    private fun getCurrentModelHash(): String? {
        val modelFile = File(context.filesDir, MODEL_FILENAME)
        if (!modelFile.exists()) return null
        
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            modelFile.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating model hash", e)
            null
        }
    }
    
    /**
     * Download model update
     */
    private suspend fun downloadModelUpdate(modelInfo: ModelInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting model download...")
            updateListener?.onDownloadStarted()
            
            val request = Request.Builder()
                .url("$serverUrl${modelInfo.download_url}")
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("Download failed: ${response.code}")
            }
            
            val responseBody = response.body ?: throw IOException("Empty response")
            val contentLength = modelInfo.model_size_bytes
            
            val modelFile = File(context.filesDir, MODEL_FILENAME)
            val tempFile = File(context.filesDir, "$MODEL_FILENAME.tmp")
            
            val inputStream = responseBody.byteStream()
            val outputStream = FileOutputStream(tempFile)
            
            val buffer = ByteArray(8192)
            var downloadedBytes = 0L
            var bytes: Int
            
            while (inputStream.read(buffer).also { bytes = it } != -1) {
                outputStream.write(buffer, 0, bytes)
                downloadedBytes += bytes
                
                if (contentLength > 0) {
                    val progress = (downloadedBytes * 100 / contentLength).toInt()
                    updateListener?.onDownloadProgress(progress)
                }
            }
            
            outputStream.close()
            inputStream.close()
            
            // Verify downloaded file hash
            val downloadedHash = calculateFileHash(tempFile)
            if (downloadedHash == modelInfo.model_hash) {
                // Move temp file to final location
                if (modelFile.exists()) {
                    modelFile.delete()
                }
                tempFile.renameTo(modelFile)
                
                // Update stored hash
                prefs.edit().putString(PREF_LAST_MODEL_HASH, modelInfo.model_hash).apply()
                
                Log.d(TAG, "Model download completed and verified")
                updateListener?.onDownloadCompleted(true)
                return@withContext true
            } else {
                Log.e(TAG, "Downloaded model hash verification failed")
                tempFile.delete()
                updateListener?.onUpdateError("Model verification failed")
                return@withContext false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            updateListener?.onDownloadCompleted(false)
            updateListener?.onUpdateError("Download failed: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * Calculate SHA-256 hash of a file
     */
    private fun calculateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Manually trigger model download
     */
    suspend fun downloadModelManually(onProgress: (Int) -> Unit = {}): Boolean {
        return try {
            setUpdateListener(object : ModelUpdateListener {
                override fun onUpdateCheckStarted() {}
                override fun onUpdateAvailable(newVersion: String, sizeMB: Double) {}
                override fun onDownloadStarted() {}
                override fun onDownloadProgress(progress: Int) {
                    onProgress(progress)
                }
                override fun onDownloadCompleted(success: Boolean) {}
                override fun onNoUpdateNeeded() {}
                override fun onUpdateError(error: String) {}
            })
            
            val modelInfo = getModelInfo()
            downloadModelUpdate(modelInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Manual download failed", e)
            false
        }
    }
    
    /**
     * Get last update info
     */
    fun getLastUpdateInfo(): String {
        val lastCheckTime = prefs.getLong(PREF_LAST_CHECK_TIME, 0)
        val lastHash = prefs.getString(PREF_LAST_MODEL_HASH, null)
        
        return if (lastCheckTime > 0) {
            val timeAgo = (System.currentTimeMillis() - lastCheckTime) / (1000 * 60) // minutes ago
            "Last checked: ${timeAgo}m ago" + 
            if (lastHash != null) " | Hash: ${lastHash.take(8)}..." else ""
        } else {
            "Never checked"
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopPeriodicUpdateCheck()
        updateListener = null
    }
}