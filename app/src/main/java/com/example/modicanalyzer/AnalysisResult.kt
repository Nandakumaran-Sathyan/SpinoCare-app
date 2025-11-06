package com.example.modicanalyzer

data class AnalysisResult(
    val hasModicChange: Boolean,
    val confidence: Float,
    val changeType: String? = null,
    val details: String? = null,
    val error: String? = null,
    val noModicScore: Float = 0f,
    val modicScore: Float = 0f,
    val analysisMode: String = "online", // "online" or "offline"
    val timestamp: Long = System.currentTimeMillis()
) {
    
    fun getDisplayText(): String {
        val mode = if (analysisMode == "online") "🌐 Online" else "📱 Offline"
        val result = if (hasModicChange) "Modic Changes Detected" else "No Modic Changes"
        val confidencePercent = (confidence * 100).toInt()
        
        return """
            $mode Analysis Results
            
            🔍 $result
            📊 Confidence: $confidencePercent%
            
            Detailed Scores:
            • No Modic: ${(noModicScore * 100).toInt()}%
            • Modic Change: ${(modicScore * 100).toInt()}%
            
            ⏰ Analyzed: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))}
        """.trimIndent()
    }
    companion object {
        fun fromPair(result: Pair<String, Float>, mode: String = "online"): AnalysisResult {
            val hasModic = result.first.contains("Modic Change Detected", ignoreCase = true)
            val confidence = result.second.coerceIn(0f, 1f) // Ensure confidence is between 0 and 1
            
            return AnalysisResult(
                hasModicChange = hasModic,
                confidence = confidence,
                noModicScore = if (hasModic) 1f - confidence else confidence,
                modicScore = if (hasModic) confidence else 1f - confidence,
                analysisMode = mode
            )
        }
        
        fun error(message: String, mode: String = "online"): AnalysisResult {
            return AnalysisResult(
                hasModicChange = false,
                confidence = 0f,
                error = message,
                analysisMode = mode
            )
        }
        
        fun fromServerResponse(prediction: Float, label: String): AnalysisResult {
            // Server label can be "Modic Change Detected" or "No Modic"
            // The prediction value represents the confidence for the stated label
            val hasModic = label.contains("Modic Change", ignoreCase = true) || 
                          (label.contains("Modic", ignoreCase = true) && !label.contains("No", ignoreCase = true))
            
            return AnalysisResult(
                hasModicChange = hasModic,
                confidence = prediction,
                // If label says "No Modic", prediction is for "no_modic" score
                // If label says "Modic Change", prediction is for "modic" score
                noModicScore = if (hasModic) 1f - prediction else prediction,
                modicScore = if (hasModic) prediction else 1f - prediction,
                analysisMode = "online"
            )
        }
        
        /**
         * Create AnalysisResult from detailed server scores (most accurate)
         */
        fun fromDetailedScores(noModicScore: Float, modicScore: Float, label: String): AnalysisResult {
            val hasModic = modicScore > noModicScore
            val confidence = if (hasModic) modicScore else noModicScore
            
            return AnalysisResult(
                hasModicChange = hasModic,
                confidence = confidence,
                noModicScore = noModicScore,
                modicScore = modicScore,
                analysisMode = "online"
            )
        }
    }
}