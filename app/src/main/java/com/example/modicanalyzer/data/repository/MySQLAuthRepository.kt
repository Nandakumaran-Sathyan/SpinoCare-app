package com.example.modicanalyzer.data.repository

import android.content.Context
import android.util.Log
import com.example.modicanalyzer.data.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for MySQL-based authentication
 * Replaces Firebase Authentication
 */
@Singleton
class MySQLAuthRepository @Inject constructor(
    private val apiService: SpinoCareApiService,
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "MySQLAuthRepository"
        private const val PREFS_NAME = "spinocare_auth"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_UID = "user_uid"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Register new user
     */
    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        phoneNumber: String? = null
    ): Result<AuthResponse> {
        return try {
            val request = RegisterRequest(
                email = email,
                password = password,
                displayName = displayName,
                phoneNumber = phoneNumber
            )
            
            val response = apiService.register(request = request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                if (data != null) {
                    // Save session
                    saveSession(data)
                    Log.d(TAG, "✅ User registered: ${data.email}")
                    Result.success(data)
                } else {
                    Result.failure(Exception("No data in response"))
                }
            } else {
                val error = response.body()?.error ?: "Registration failed"
                Log.e(TAG, "❌ Registration failed: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Registration error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Login user
     */
    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            val request = LoginRequest(email = email, password = password)
            
            val response = apiService.login(request = request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                if (data != null) {
                    // Save session
                    saveSession(data)
                    Log.d(TAG, "✅ User logged in: ${data.email}")
                    Result.success(data)
                } else {
                    Result.failure(Exception("No data in response"))
                }
            } else {
                val error = response.body()?.error ?: "Login failed"
                Log.e(TAG, "❌ Login failed: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Login error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Logout user
     */
    fun logout() {
        prefs.edit().clear().apply()
        Log.d(TAG, "✅ User logged out")
    }
    
    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean {
        return getToken() != null
    }
    
    /**
     * Get current user UID
     */
    fun getCurrentUserUid(): String? {
        return prefs.getString(KEY_UID, null)
    }
    
    /**
     * Get current user email
     */
    fun getCurrentUserEmail(): String? {
        return prefs.getString(KEY_EMAIL, null)
    }
    
    /**
     * Get current user display name
     */
    fun getCurrentUserDisplayName(): String? {
        return prefs.getString(KEY_DISPLAY_NAME, null)
    }
    
    /**
     * Get auth token
     */
    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }
    
    /**
     * Save user session
     */
    private fun saveSession(authResponse: AuthResponse) {
        prefs.edit().apply {
            putString(KEY_TOKEN, authResponse.token)
            putString(KEY_UID, authResponse.uid)
            putString(KEY_EMAIL, authResponse.email)
            putString(KEY_DISPLAY_NAME, authResponse.displayName)
            apply()
        }
    }
}
