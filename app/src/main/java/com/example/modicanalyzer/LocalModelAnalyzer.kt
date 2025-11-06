package com.example.modicanalyzer

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.tensorflow.lite.Interpreter
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Local model analyzer for offline inference using downloaded TFLite model
 */
class LocalModelAnalyzer(private val context: Context) {
    
    private var interpreter: Interpreter? = null
    private val executorService = Executors.newCachedThreadPool()
    
    private var inputImageWidth: Int = 224
    private var inputImageHeight: Int = 224
    private var modelInputSize: Int = 0
    
    companion object {
        private const val TAG = "LocalAnalyzer"
        private const val MODEL_FILENAME = "modic_model_offline.tflite"
        private const val DOWNLOAD_URL = "https://modic.onrender.com/get_global_model"
        
        private const val FLOAT_TYPE_SIZE = 4
        private const val PIXEL_SIZE = 3 // RGB
        private const val OUTPUT_CLASSES_COUNT = 2
        
        // Medical imaging normalization constants
        private const val NORMALIZATION_MEAN = 127.5f
        private const val NORMALIZATION_STD = 127.5f
        
        /**
         * Check if model file exists in internal storage
         */
        fun isModelFileAvailable(context: Context): Boolean {
            val modelFile = File(context.filesDir, MODEL_FILENAME)
            return modelFile.exists() && modelFile.length() > 0
        }
        
        /**
         * Download model from server to internal storage
         */
        suspend fun downloadModel(
            context: Context,
            onProgress: (Int) -> Unit = {}
        ): Boolean = withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting model download")
                
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(300, TimeUnit.SECONDS) // Longer timeout for large file
                    .build()
                
                val request = Request.Builder()
                    .url(DOWNLOAD_URL)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw IOException("Download failed: ${response.code}")
                }
                
                val responseBody = response.body ?: throw IOException("Empty response")
                val contentLength = responseBody.contentLength()
                
                Log.d(TAG, "Downloading model: ${contentLength / 1024 / 1024}MB")
                
                val modelFile = File(context.filesDir, MODEL_FILENAME)
                val inputStream = responseBody.byteStream()
                val outputStream = FileOutputStream(modelFile)
                
                val buffer = ByteArray(8192)
                var downloadedBytes = 0L
                var bytes: Int
                
                while (inputStream.read(buffer).also { bytes = it } != -1) {
                    outputStream.write(buffer, 0, bytes)
                    downloadedBytes += bytes
                    
                    if (contentLength > 0) {
                        val progress = (downloadedBytes * 100 / contentLength).toInt()
                        onProgress(progress)
                    }
                }
                
                outputStream.close()
                inputStream.close()
                
                Log.d(TAG, "Model download completed: ${modelFile.length()} bytes")
                true
                
            } catch (e: Exception) {
                Log.e(TAG, "Model download failed", e)
                false
            }
        }
    }
    
    /**
     * Initialize the TensorFlow Lite interpreter
     */
    fun initialize(): Task<Void?> {
        val task = TaskCompletionSource<Void?>()
        executorService.execute {
            try {
                initializeInterpreter()
                task.setResult(null)
            } catch (e: IOException) {
                task.setException(e)
            }
        }
        return task.task
    }
    
    /**
     * Initialize interpreter from downloaded model file
     */
    @Throws(IOException::class)
    private fun initializeInterpreter() {
        val modelFile = File(context.filesDir, MODEL_FILENAME)
        
        if (!modelFile.exists()) {
            throw IOException("Local model file not found. Please download first.")
        }
        
        try {
            val model = loadModelFile(modelFile)
            
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(false)
                setUseXNNPACK(true)
            }
            
            interpreter = Interpreter(model, options)
            
            // Read input shape from model
            val inputShape = interpreter!!.getInputTensor(0).shape()
            inputImageWidth = inputShape[1]
            inputImageHeight = inputShape[2]
            modelInputSize = FLOAT_TYPE_SIZE * inputImageWidth * inputImageHeight * PIXEL_SIZE
            
            Log.d(TAG, "Local TFLite model initialized: ${inputShape.contentToString()}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize local model", e)
            throw IOException("Model initialization failed: ${e.message}", e)
        }
    }
    
    /**
     * Load model file into ByteBuffer
     */
    private fun loadModelFile(modelFile: File): ByteBuffer {
        val inputStream = FileInputStream(modelFile)
        val fileChannel = inputStream.channel
        val startOffset = 0L
        val declaredLength = modelFile.length()
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    /**
     * Analyze images using local TFLite model
     */
    suspend fun analyze(t1Image: Bitmap, t2Image: Bitmap): AnalysisResult = withContext(Dispatchers.IO) {
        try {
            if (interpreter == null) {
                // Try to initialize if not already done
                initialize().let { task ->
                    // Wait for initialization (simplified for coroutine context)
                    if (!isModelFileAvailable(context)) {
                        return@withContext AnalysisResult.error(
                            "Local model not available. Please download first.",
                            "offline"
                        )
                    }
                    initializeInterpreter()
                }
            }
            
            val result = classifyImages(t1Image, t2Image)
            AnalysisResult.fromPair(result, "offline")
            
        } catch (e: Exception) {
            Log.e(TAG, "Local analysis failed", e)
            AnalysisResult.error("Offline analysis failed: ${e.message}", "offline")
        }
    }
    
    /**
     * Classify the T1 and T2 images
     */
    private fun classifyImages(t1Image: Bitmap, t2Image: Bitmap): Pair<String, Float> {
        val interpreter = this.interpreter ?: throw IllegalStateException("Model not initialized")
        
        // Prepare input buffers
        val t1Buffer = convertBitmapToByteBuffer(t1Image)
        val t2Buffer = convertBitmapToByteBuffer(t2Image)
        
        // Prepare output buffer
        val outputArray = Array(1) { FloatArray(OUTPUT_CLASSES_COUNT) }
        
        // Run inference
        if (interpreter.inputTensorCount == 2) {
            // Dual input model
            val inputs = arrayOf(t1Buffer, t2Buffer)
            interpreter.runForMultipleInputsOutputs(inputs, mapOf(0 to outputArray))
        } else {
            // Single input model - use T1 image
            interpreter.run(t1Buffer, outputArray)
        }
        
        val output = outputArray[0]
        val noModicScore = output[0]
        val modicScore = output[1]
        
        Log.d(TAG, "Local inference result: No Modic=$noModicScore, Modic=$modicScore")
        
        return if (modicScore > noModicScore) {
            Pair("Modic Change Detected", modicScore)
        } else {
            Pair("No Modic Changes", noModicScore)
        }
    }
    
    /**
     * Convert bitmap to ByteBuffer for model input
     * Fixed for Android 12+ compatibility - explicit ByteOrder.LITTLE_ENDIAN
     */
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(modelInputSize)
        // CRITICAL FIX: Always use LITTLE_ENDIAN for consistent behavior across Android versions
        // Android 12 had different nativeOrder() behavior on some devices
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        
        // Scale bitmap to model input size with FILTER_BILINEAR for consistent quality
        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap, 
            inputImageWidth, 
            inputImageHeight, 
            true  // Use bilinear filtering for better quality
        )
        
        val intValues = IntArray(inputImageWidth * inputImageHeight)
        scaledBitmap.getPixels(intValues, 0, inputImageWidth, 0, 0, inputImageWidth, inputImageHeight)
        
        // Reset buffer position to ensure writing starts from beginning
        byteBuffer.rewind()
        
        var pixel = 0
        for (i in 0 until inputImageWidth) {
            for (j in 0 until inputImageHeight) {
                val pixelValue = intValues[pixel++]
                
                // Extract RGB values - consistent across Android versions
                val r = ((pixelValue shr 16) and 0xFF).toFloat()
                val g = ((pixelValue shr 8) and 0xFF).toFloat()
                val b = (pixelValue and 0xFF).toFloat()
                
                // Medical imaging normalization: (pixel - mean) / std
                // This maps [0, 255] to approximately [-1, 1]
                val normalizedR = (r - NORMALIZATION_MEAN) / NORMALIZATION_STD
                val normalizedG = (g - NORMALIZATION_MEAN) / NORMALIZATION_STD
                val normalizedB = (b - NORMALIZATION_MEAN) / NORMALIZATION_STD
                
                byteBuffer.putFloat(normalizedR)
                byteBuffer.putFloat(normalizedG)
                byteBuffer.putFloat(normalizedB)
            }
        }
        
        // Reset position to start for reading by interpreter
        byteBuffer.rewind()
        
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        
        return byteBuffer
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        interpreter?.close()
        interpreter = null
        Log.d(TAG, "LocalModelAnalyzer cleaned up")
    }
}