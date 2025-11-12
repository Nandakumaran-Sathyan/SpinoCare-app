package com.example.modicanalyzer.data.api

import com.google.gson.annotations.SerializedName

/**
 * Base API response wrapper
 */
data class ApiResponse<T>(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("data")
    val data: T? = null,
    
    @SerializedName("error")
    val error: String? = null,
    
    @SerializedName("timestamp")
    val timestamp: String? = null
)

/**
 * User registration/update request
 */
data class UserRequest(
    @SerializedName("firebase_uid")
    val firebaseUid: String,
    
    @SerializedName("email")
    val email: String,
    
    @SerializedName("display_name")
    val displayName: String? = null,
    
    @SerializedName("phone_number")
    val phoneNumber: String? = null
)

/**
 * User response data
 */
data class UserData(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("firebase_uid")
    val firebaseUid: String,
    
    @SerializedName("email")
    val email: String,
    
    @SerializedName("display_name")
    val displayName: String? = null,
    
    @SerializedName("phone_number")
    val phoneNumber: String? = null,
    
    @SerializedName("created_at")
    val createdAt: String? = null,
    
    @SerializedName("last_login")
    val lastLogin: String? = null,
    
    @SerializedName("is_active")
    val isActive: Boolean = true
)

/**
 * User registration response
 */
data class UserRegistrationData(
    @SerializedName("user_id")
    val userId: Int,
    
    @SerializedName("message")
    val message: String
)

/**
 * MRI Analysis save request
 */
data class AnalysisRequest(
    @SerializedName("firebase_uid")
    val firebaseUid: String,
    
    @SerializedName("entry_id")
    val entryId: String,
    
    @SerializedName("t1_image_url")
    val t1ImageUrl: String,
    
    @SerializedName("t2_image_url")
    val t2ImageUrl: String,
    
    @SerializedName("analysis_result")
    val analysisResult: String,
    
    @SerializedName("confidence")
    val confidence: Float,
    
    @SerializedName("analysis_mode")
    val analysisMode: String = "online",
    
    @SerializedName("model_version")
    val modelVersion: String = "v1.0",
    
    @SerializedName("processing_time_ms")
    val processingTimeMs: Int? = null
)

/**
 * Analysis save response
 */
data class AnalysisSaveData(
    @SerializedName("analysis_id")
    val analysisId: Int,
    
    @SerializedName("entry_id")
    val entryId: String,
    
    @SerializedName("message")
    val message: String
)

/**
 * Single analysis entry
 */
data class AnalysisEntry(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("entry_id")
    val entryId: String,
    
    @SerializedName("t1_image_url")
    val t1ImageUrl: String,
    
    @SerializedName("t2_image_url")
    val t2ImageUrl: String,
    
    @SerializedName("analysis_result")
    val analysisResult: String,
    
    @SerializedName("confidence")
    val confidence: String,  // Comes as string from DB
    
    @SerializedName("analysis_mode")
    val analysisMode: String,
    
    @SerializedName("model_version")
    val modelVersion: String,
    
    @SerializedName("processing_time_ms")
    val processingTimeMs: Int? = null,
    
    @SerializedName("created_at")
    val createdAt: String
)

/**
 * Analysis list response
 */
data class AnalysisListData(
    @SerializedName("analyses")
    val analyses: List<AnalysisEntry>,
    
    @SerializedName("total")
    val total: Int,
    
    @SerializedName("limit")
    val limit: Int,
    
    @SerializedName("offset")
    val offset: Int
)

// ============================================
// AUTHENTICATION MODELS
// ============================================

/**
 * Register request (with password)
 */
data class RegisterRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("phone_number") val phoneNumber: String? = null
)

/**
 * Login request
 */
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

/**
 * Auth response (register or login)
 */
data class AuthResponse(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("uid") val uid: String,
    @SerializedName("email") val email: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("token") val token: String,
    @SerializedName("message") val message: String
)

/**
 * Firebase custom token response
 */
data class FirebaseTokenResponse(
    @SerializedName("firebase_token") val firebaseToken: String,
    @SerializedName("expires_in") val expiresIn: Int
)
