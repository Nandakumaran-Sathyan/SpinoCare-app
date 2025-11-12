package com.example.modicanalyzer.data.repository

import android.util.Log
import com.example.modicanalyzer.data.api.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for MySQL database operations via REST API
 * Replaces direct Firestore calls with REST API calls
 */
@Singleton
class MySQLRepository @Inject constructor(
    private val apiService: SpinoCareApiService
) {
    
    companion object {
        private const val TAG = "MySQLRepository"
    }
    
    /**
     * Register or update user in MySQL database
     */
    suspend fun registerUser(
        firebaseUid: String,
        email: String,
        displayName: String? = null,
        phoneNumber: String? = null
    ): Result<UserRegistrationData> {
        return try {
            val request = UserRequest(
                firebaseUid = firebaseUid,
                email = email,
                displayName = displayName,
                phoneNumber = phoneNumber
            )
            
            val response = apiService.registerUser(request = request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                if (data != null) {
                    Log.d(TAG, "User registered successfully: ${data.userId}")
                    Result.success(data)
                } else {
                    Result.failure(Exception("No data in response"))
                }
            } else {
                val error = response.body()?.error ?: "Unknown error"
                Log.e(TAG, "User registration failed: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "User registration error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get user details from MySQL database
     */
    suspend fun getUser(firebaseUid: String): Result<UserData> {
        return try {
            val response = apiService.getUser(path = "users/$firebaseUid")
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                if (data != null) {
                    Log.d(TAG, "User fetched successfully: ${data.email}")
                    Result.success(data)
                } else {
                    Result.failure(Exception("No data in response"))
                }
            } else {
                val error = response.body()?.error ?: "User not found"
                Log.e(TAG, "Get user failed: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get user error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Save MRI analysis entry to MySQL database
     */
    suspend fun saveAnalysis(
        firebaseUid: String,
        entryId: String,
        t1ImageUrl: String,
        t2ImageUrl: String,
        analysisResult: String,
        confidence: Float,
        analysisMode: String = "online",
        modelVersion: String = "v1.0",
        processingTimeMs: Int? = null
    ): Result<AnalysisSaveData> {
        return try {
            val request = AnalysisRequest(
                firebaseUid = firebaseUid,
                entryId = entryId,
                t1ImageUrl = t1ImageUrl,
                t2ImageUrl = t2ImageUrl,
                analysisResult = analysisResult,
                confidence = confidence,
                analysisMode = analysisMode,
                modelVersion = modelVersion,
                processingTimeMs = processingTimeMs
            )
            
            val response = apiService.saveAnalysis(request = request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                if (data != null) {
                    Log.d(TAG, "Analysis saved successfully: ${data.entryId}")
                    Result.success(data)
                } else {
                    Result.failure(Exception("No data in response"))
                }
            } else {
                val error = response.body()?.error ?: "Save failed"
                Log.e(TAG, "Save analysis failed: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Save analysis error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all analyses for a user
     */
    suspend fun getAnalysisList(
        firebaseUid: String,
        limit: Int = 10,
        offset: Int = 0
    ): Result<AnalysisListData> {
        return try {
            val response = apiService.getAnalysisList(
                path = "analysis/list/$firebaseUid",
                limit = limit,
                offset = offset
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                if (data != null) {
                    Log.d(TAG, "Fetched ${data.analyses.size} analyses (total: ${data.total})")
                    Result.success(data)
                } else {
                    Result.failure(Exception("No data in response"))
                }
            } else {
                val error = response.body()?.error ?: "Fetch failed"
                Log.e(TAG, "Get analysis list failed: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get analysis list error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get specific analysis by entry ID
     */
    suspend fun getAnalysis(entryId: String): Result<AnalysisEntry> {
        return try {
            val response = apiService.getAnalysis(path = "analysis/$entryId")
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                if (data != null) {
                    Log.d(TAG, "Fetched analysis: $entryId")
                    Result.success(data)
                } else {
                    Result.failure(Exception("No data in response"))
                }
            } else {
                val error = response.body()?.error ?: "Analysis not found"
                Log.e(TAG, "Get analysis failed: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get analysis error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete analysis entry
     */
    suspend fun deleteAnalysis(entryId: String): Result<Boolean> {
        return try {
            val response = apiService.deleteAnalysis(path = "analysis/$entryId")
            
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "Analysis deleted: $entryId")
                Result.success(true)
            } else {
                val error = response.body()?.error ?: "Delete failed"
                Log.e(TAG, "Delete analysis failed: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete analysis error", e)
            Result.failure(e)
        }
    }
}
