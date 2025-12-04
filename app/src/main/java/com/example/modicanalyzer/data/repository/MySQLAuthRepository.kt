package com.example.modicanalyzer.data.repository

import android.content.Context
import android.util.Log
import com.example.modicanalyzer.data.api.*
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for MySQL-based authentication
 * Also handles Firebase custom token authentication for Storage access
 */
@Singleton
class MySQLAuthRepository @Inject constructor(
    private val apiService: SpinoCareApiService,
    @ApplicationContext private val context: Context
) {
    
    private val firebaseAuth = FirebaseAuth.getInstance()
    
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
     * Register new user (Step 1 - Send OTP, DO NOT create user yet)
     * Returns registration data to be passed to OTP verification
     */
    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        phoneNumber: String? = null
    ): Result<RegisterResponse> {
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
                    Log.d(TAG, "✅ OTP sent to: ${data.email}")
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
     * Verify email with OTP (Step 2 - Complete registration and create user)
     */
    suspend fun verifyEmail(
        email: String,
        otp: String,
        passwordHash: String,
        displayName: String? = null,
        phoneNumber: String? = null
    ): Result<AuthResponse> {
        return try {
            val request = VerifyEmailRequest(
                email = email,
                otp = otp,
                passwordHash = passwordHash,
                displayName = displayName,
                phoneNumber = phoneNumber
            )
            
            val response = apiService.verifyEmail(request = request)
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                if (data != null) {
                    // Save session
                    saveSession(data)
                    Log.d(TAG, "✅ Email verified and user created: ${data.email}")
                    Result.success(data)
                } else {
                    Result.failure(Exception("No data in response"))
                }
            } else {
                val error = response.body()?.error ?: "Verification failed"
                Log.e(TAG, "❌ Verification failed: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Verification error", e)
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
     * Sign into Firebase with custom token
     * This enables Firebase Storage access for MySQL-authenticated users
     */
    suspend fun signIntoFirebase(): Result<Unit> {
        return try {
            val mysqlToken = getToken()
            if (mysqlToken == null) {
                return Result.failure(Exception("Not logged in"))
            }
            
            // Get Firebase custom token from backend
            val response = apiService.getFirebaseToken(authToken = "Bearer $mysqlToken")
            
            if (response.isSuccessful && response.body()?.success == true) {
                val firebaseToken = response.body()?.data?.firebaseToken
                
                if (firebaseToken != null) {
                    // Sign into Firebase with custom token
                    firebaseAuth.signInWithCustomToken(firebaseToken).await()
                    Log.d(TAG, "✅ Signed into Firebase with custom token")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("No Firebase token in response"))
                }
            } else {
                val error = response.body()?.error ?: "Failed to get Firebase token"
                Log.e(TAG, "❌ Firebase token request failed: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Firebase sign-in error", e)
            Result.failure(e)
        }
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
