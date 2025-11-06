package com.example.modicanalyzer

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.modicanalyzer.model.ModelUpdateManager

/**
 * Unified analyzer that switches between online (remote) and offline (local) inference
 * based on user preference and model availability
 */
class ModicAnalyzer(private val context: Context) {
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("modic_analyzer_prefs", Context.MODE_PRIVATE)
    }
    
    private val remoteAnalyzer by lazy { RemoteModelAnalyzer(context) }
    private var localAnalyzer: LocalModelAnalyzer? = null
    private val modelUpdateManager by lazy { ModelUpdateManager(context) }
    
    companion object {
        private const val TAG = "ModicAnalyzer"
        private const val PREF_OFFLINE_MODE = "offline_mode_enabled"
        private const val PREF_MODEL_DOWNLOADED = "local_model_downloaded"
    }
    
    /**
     * Initialize automatic model updates when network is available
     */
    fun initializeAutoUpdates() {
        // Check if model file already exists but preference isn't set
        if (LocalModelAnalyzer.isModelFileAvailable(context) && !prefs.getBoolean(PREF_MODEL_DOWNLOADED, false)) {
            markModelDownloaded()
            Log.d(TAG, "Found existing model file, marked as downloaded")
        }
        
        // Initialize with null listener to set up the internal tracking
        setModelUpdateListener(null)
        modelUpdateManager.startPeriodicUpdateCheck()
        Log.d(TAG, "Automatic model updates initialized")
    }
    
    private var externalListener: ModelUpdateManager.ModelUpdateListener? = null
    
    /**
     * Set model update listener
     */
    fun setModelUpdateListener(listener: ModelUpdateManager.ModelUpdateListener?) {
        externalListener = listener
        // Create a composite listener that handles both internal and external callbacks
        val compositeListener = object : ModelUpdateManager.ModelUpdateListener {
            override fun onUpdateCheckStarted() {
                externalListener?.onUpdateCheckStarted()
            }
            override fun onUpdateAvailable(newVersion: String, sizeMB: Double) {
                externalListener?.onUpdateAvailable(newVersion, sizeMB)
            }
            override fun onDownloadStarted() {
                externalListener?.onDownloadStarted()
            }
            override fun onDownloadProgress(progress: Int) {
                externalListener?.onDownloadProgress(progress)
            }
            override fun onDownloadCompleted(success: Boolean) {
                if (success) {
                    markModelDownloaded()
                    Log.d(TAG, "Model marked as downloaded after successful update")
                }
                externalListener?.onDownloadCompleted(success)
            }
            override fun onNoUpdateNeeded() {
                externalListener?.onNoUpdateNeeded()
            }
            override fun onUpdateError(error: String) {
                externalListener?.onUpdateError(error)
            }
        }
        modelUpdateManager.setUpdateListener(compositeListener)
    }
    
    /**
     * Enable/disable automatic model updates
     */
    fun setAutoUpdateEnabled(enabled: Boolean) {
        modelUpdateManager.setAutoUpdateEnabled(enabled)
    }
    
    /**
     * Check if auto-update is enabled
     */
    fun isAutoUpdateEnabled(): Boolean {
        return modelUpdateManager.isAutoUpdateEnabled()
    }
    
    /**
     * Manually check for model updates
     */
    suspend fun checkForModelUpdates(): Boolean {
        return modelUpdateManager.checkForUpdates()
    }
    
    /**
     * Get model update info
     */
    fun getModelUpdateInfo(): String {
        return modelUpdateManager.getLastUpdateInfo()
    }
    
    /**
     * Check if offline mode is enabled
     */
    fun isOfflineModeEnabled(): Boolean {
        return prefs.getBoolean(PREF_OFFLINE_MODE, false)
    }
    
    /**
     * Check if local model is downloaded and available
     */
    fun isLocalModelAvailable(): Boolean {
        return prefs.getBoolean(PREF_MODEL_DOWNLOADED, false) && 
                LocalModelAnalyzer.isModelFileAvailable(context)
    }
    
    /**
     * Enable or disable offline mode
     */
    fun setOfflineMode(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_OFFLINE_MODE, enabled).apply()
        Log.d(TAG, "Offline mode ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Mark local model as downloaded
     */
    internal fun markModelDownloaded() {
        prefs.edit().putBoolean(PREF_MODEL_DOWNLOADED, true).apply()
    }
    
    /**
     * Main analysis method that routes to appropriate analyzer
     */
    suspend fun analyze(t1Image: Bitmap, t2Image: Bitmap): AnalysisResult = withContext(Dispatchers.IO) {
        try {
            val useOfflineMode = isOfflineModeEnabled() && isLocalModelAvailable()
            
            Log.d(TAG, "Analysis mode: ${if (useOfflineMode) "offline" else "online"}")
            
            if (useOfflineMode) {
                // Use local inference
                if (localAnalyzer == null) {
                    localAnalyzer = LocalModelAnalyzer(context)
                }
                localAnalyzer!!.analyze(t1Image, t2Image)
            } else {
                // Use remote inference
                remoteAnalyzer.analyze(t1Image, t2Image)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Analysis failed", e)
            AnalysisResult.error(
                "Analysis failed: ${e.message}",
                if (isOfflineModeEnabled()) "offline" else "online"
            )
        }
    }
    
    /**
     * Download model for offline use
     */
    suspend fun downloadModelForOfflineUse(
        onProgress: (Int) -> Unit = {},
        onComplete: (Boolean) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting model download for offline use")
            
            val downloadSuccess = LocalModelAnalyzer.downloadModel(context) { progress ->
                onProgress(progress)
            }
            
            if (downloadSuccess) {
                markModelDownloaded()
                Log.d(TAG, "Model download completed successfully")
            } else {
                Log.e(TAG, "Model download failed")
            }
            
            withContext(Dispatchers.Main) {
                onComplete(downloadSuccess)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model", e)
            withContext(Dispatchers.Main) {
                onComplete(false)
            }
        }
    }
    
    /**
     * Get current analysis mode info
     */
    fun getAnalysisModeInfo(): String {
        return when {
            !isOfflineModeEnabled() -> "🌐 Online Mode - Using server inference"
            isLocalModelAvailable() -> "📱 Offline Mode - Using local model"
            else -> "⚠️ Offline Mode Enabled - Model not downloaded"
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        localAnalyzer?.cleanup()
        remoteAnalyzer.cleanup()
        modelUpdateManager.cleanup()
    }
}