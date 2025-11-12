package com.example.modicanalyzer.data.api

import retrofit2.Response
import retrofit2.http.*

/**
 * SpinoCare REST API Service
 * Base URL: http://localhost/spinocare-api/index.php
 */
interface SpinoCareApiService {
    
    /**
     * Register or update user
     * POST /users/register
     */
    @POST("index.php")
    suspend fun registerUser(
        @Query("path") path: String = "users/register",
        @Body request: UserRequest
    ): Response<ApiResponse<UserRegistrationData>>
    
    /**
     * Get user by Firebase UID
     * GET /users/{firebase_uid}
     */
    @GET("index.php")
    suspend fun getUser(
        @Query("path") path: String,  // Will be "users/{firebase_uid}"
    ): Response<ApiResponse<UserData>>
    
    /**
     * Save MRI analysis entry
     * POST /analysis/save
     */
    @POST("index.php")
    suspend fun saveAnalysis(
        @Query("path") path: String = "analysis/save",
        @Body request: AnalysisRequest
    ): Response<ApiResponse<AnalysisSaveData>>
    
    /**
     * Get analysis list for user
     * GET /analysis/list/{firebase_uid}
     */
    @GET("index.php")
    suspend fun getAnalysisList(
        @Query("path") path: String,  // Will be "analysis/list/{firebase_uid}"
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0
    ): Response<ApiResponse<AnalysisListData>>
    
    /**
     * Get specific analysis by entry ID
     * GET /analysis/{entry_id}
     */
    @GET("index.php")
    suspend fun getAnalysis(
        @Query("path") path: String  // Will be "analysis/{entry_id}"
    ): Response<ApiResponse<AnalysisEntry>>
    
    /**
     * Delete analysis entry
     * DELETE /analysis/{entry_id}
     */
    @HTTP(method = "DELETE", path = "index.php", hasBody = false)
    suspend fun deleteAnalysis(
        @Query("path") path: String  // Will be "analysis/{entry_id}"
    ): Response<ApiResponse<Map<String, String>>>
}
