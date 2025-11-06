package com.example.modicanalyzer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.example.modicanalyzer.data.model.AuthState
import com.example.modicanalyzer.data.repository.AuthRepository
import com.example.modicanalyzer.util.NetworkConnectivityObserver
import com.example.modicanalyzer.util.ValidationUtil
import com.example.modicanalyzer.worker.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Authentication operations.
 * 
 * Manages:
 * - User signup (online/offline) with validation
 * - User login (online/offline) with validation
 * - Authentication state
 * - Network connectivity status
 * - Automatic sync trigger on network availability
 * - Input validation and error handling
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val networkObserver: NetworkConnectivityObserver,
    private val workManager: WorkManager,
    private val application: android.app.Application
) : ViewModel() {
    
    companion object {
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_LAST_USER_ID = "last_user_id"
    }
    
    /**
     * Current authentication state.
     * UI observes this to react to auth changes.
     */
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    /**
     * Network connectivity status.
     * true = online, false = offline
     */
    val isOnline: StateFlow<Boolean> = networkObserver.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = networkObserver.isCurrentlyConnected()
        )
    
    init {
        android.util.Log.d("AuthViewModel", "Initializing AuthViewModel")
        
        // Monitor network changes and trigger sync when coming online
        observeNetworkChanges()
        
        // Check if user is already logged in
        checkCurrentUser()
    }
    
    /**
     * Sign up a new user with validation.
     * 
     * @param email User's email
     * @param password User's password
     * @param confirmPassword Password confirmation
     * @param displayName User's display name (optional)
     */
    fun signUp(
        email: String, 
        password: String, 
        confirmPassword: String,
        displayName: String? = null
    ) {
        android.util.Log.d("AuthViewModel", "signUp called: email=$email, isOnline=${isOnline.value}")
        
        viewModelScope.launch {
            // Validate all input fields
            val validationResults = ValidationUtil.validateSignupForm(
                email = email.trim(),
                password = password,
                confirmPassword = confirmPassword,
                displayName = displayName?.trim()
            )
            
            android.util.Log.d("AuthViewModel", "Validation results: ${validationResults.size} fields validated")
            
            // Check if any validation failed
            val firstError = validationResults.values.firstOrNull { !it.isValid }
            if (firstError != null) {
                _authState.value = AuthState.Error(firstError.errorMessage ?: "Invalid input")
                return@launch
            }
            
            // All validations passed, proceed with signup
            android.util.Log.d("AuthViewModel", "Validation passed, calling authRepository.signUp()")
            
            authRepository.signUp(
                email = email.trim(),
                password = password,
                displayName = displayName?.trim(),
                isOnline = isOnline.value
            ).collect { state ->
                android.util.Log.d("AuthViewModel", "signUp state received: $state")
                _authState.value = state
                
                // If signup successful, save user session and trigger sync if online
                if (state is AuthState.Success) {
                    android.util.Log.d("AuthViewModel", "Signup successful! userId=${state.userId}, isFirebaseAuth=${state.isFirebaseAuth}")
                    saveLastLoggedInUserId(state.userId)
                    if (isOnline.value) {
                        android.util.Log.d("AuthViewModel", "Device is online, triggering sync")
                        triggerSync(state.userId)
                    } else {
                        android.util.Log.d("AuthViewModel", "Device is offline, sync will happen when connected")
                    }
                }
            }
        }
    }
    
    /**
     * Log in an existing user with validation.
     * 
     * @param email User's email
     * @param password User's password
     */
    fun login(email: String, password: String) {
        android.util.Log.d("AuthViewModel", "login called: email=$email, isOnline=${isOnline.value}")
        
        viewModelScope.launch {
            // Validate input fields
            val validationResults = ValidationUtil.validateLoginForm(
                email = email.trim(),
                password = password
            )
            
            android.util.Log.d("AuthViewModel", "Login validation: ${validationResults.size} fields validated")
            
            // Check if any validation failed
            val firstError = validationResults.values.firstOrNull { !it.isValid }
            if (firstError != null) {
                _authState.value = AuthState.Error(firstError.errorMessage ?: "Invalid input")
                return@launch
            }
            
            // Validation passed, proceed with login
            android.util.Log.d("AuthViewModel", "Login validation passed, calling authRepository.login()")
            
            authRepository.login(
                email = email.trim(),
                password = password,
                isOnline = isOnline.value
            ).collect { state ->
                android.util.Log.d("AuthViewModel", "login state received: $state")
                _authState.value = state
                
                // If login successful, save user session and trigger sync if online
                if (state is AuthState.Success) {
                    android.util.Log.d("AuthViewModel", "Login successful! userId=${state.userId}, isFirebaseAuth=${state.isFirebaseAuth}")
                    saveLastLoggedInUserId(state.userId)
                    if (isOnline.value) {
                        android.util.Log.d("AuthViewModel", "Device is online, triggering sync")
                        triggerSync(state.userId)
                    } else {
                        android.util.Log.d("AuthViewModel", "Device is offline, no sync needed")
                    }
                }
            }
        }
    }
    
    /**
     * Sign out the current user.
     */
    fun signOut() {
        authRepository.signOut()
        clearLastLoggedInUserId()
        _authState.value = AuthState.Unauthenticated
        
        // Cancel any ongoing sync work
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME)
    }
    
    /**
     * Save the last logged in user ID for session persistence.
     */
    private fun saveLastLoggedInUserId(userId: String) {
        android.util.Log.d("AuthViewModel", "saveLastLoggedInUserId: Saving userId=$userId to SharedPreferences")
        val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_USER_ID, userId).apply()
    }
    
    /**
     * Get the last logged in user ID.
     */
    private fun getLastLoggedInUserId(): String? {
        val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_USER_ID, null)
    }
    
    /**
     * Clear the last logged in user ID.
     */
    private fun clearLastLoggedInUserId() {
        val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LAST_USER_ID).apply()
    }
    
    /**
     * Force sync now (manual trigger for testing/debugging).
     */
    fun forceSyncNow() {
        val currentState = _authState.value
        if (currentState is AuthState.Success) {
            triggerSync(currentState.userId)
        }
    }
    
    /**
     * Check if a user is currently authenticated.
     * Only checks Firebase Auth - removed automatic local user login to prevent demo account auto-login
     */
    private fun checkCurrentUser() {
        viewModelScope.launch {
            android.util.Log.d("AuthViewModel", "checkCurrentUser: Checking authentication status...")
            
            // Only check Firebase - no automatic local user login
            val firebaseUser = authRepository.getCurrentFirebaseUser()
            android.util.Log.d("AuthViewModel", "checkCurrentUser: Firebase user = ${firebaseUser?.email ?: "null"}")
            
            if (firebaseUser != null) {
                android.util.Log.d("AuthViewModel", "checkCurrentUser: Firebase user found, logging in with UID=${firebaseUser.uid}")
                _authState.value = AuthState.Success(
                    userId = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    isFirebaseAuth = true
                )
                saveLastLoggedInUserId(firebaseUser.uid)
            } else {
                android.util.Log.d("AuthViewModel", "checkCurrentUser: No Firebase user, setting Unauthenticated")
                // Clear any saved session since there's no Firebase user
                clearLastLoggedInUserId()
                _authState.value = AuthState.Unauthenticated
            }
        }
    }
    
    /**
     * Observe network connectivity changes.
     * Trigger sync when device comes online.
     */
    private fun observeNetworkChanges() {
        viewModelScope.launch {
            isOnline.collect { online ->
                if (online) {
                    // Device came online, trigger sync if user is authenticated
                    val currentState = _authState.value
                    if (currentState is AuthState.Success) {
                        triggerSync(currentState.userId)
                    }
                }
            }
        }
    }
    
    /**
     * Trigger background sync via WorkManager.
     * 
     * @param userId User's ID to sync
     */
    private fun triggerSync(userId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    SyncWorker.KEY_USER_ID to userId,
                    SyncWorker.KEY_SYNC_TYPE to SyncWorker.SYNC_TYPE_FULL
                )
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .build()
        
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
    
    /**
     * Reset auth state to idle.
     */
    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }
    
    /**
     * Send password reset email to the specified email address.
     * Uses Firebase's built-in password reset functionality.
     * 
     * @param email The email address to send the reset link to
     */
    fun sendPasswordResetEmail(email: String) {
        android.util.Log.d("AuthViewModel", "sendPasswordResetEmail called for: $email")
        
        viewModelScope.launch {
            // Validate email format
            val validationResult = ValidationUtil.validateEmail(email.trim())
            if (!validationResult.isValid) {
                _authState.value = AuthState.Error(validationResult.errorMessage ?: "Invalid email")
                return@launch
            }
            
            _authState.value = AuthState.Loading
            
            authRepository.sendPasswordResetEmail(email.trim()).fold(
                onSuccess = {
                    android.util.Log.d("AuthViewModel", "✅ Password reset email sent successfully")
                    _authState.value = AuthState.PasswordResetEmailSent
                },
                onFailure = { error ->
                    android.util.Log.e("AuthViewModel", "❌ Failed to send password reset email", error)
                    _authState.value = AuthState.Error(
                        error.message ?: "Failed to send password reset email. Please try again."
                    )
                }
            )
        }
    }
    
    /**
     * Send email verification to the current user.
     * Should be called after signup or when user manually requests verification.
     */
    fun sendEmailVerification() {
        android.util.Log.d("AuthViewModel", "sendEmailVerification called")
        
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            
            authRepository.sendEmailVerification().fold(
                onSuccess = {
                    android.util.Log.d("AuthViewModel", "✅ Verification email sent successfully")
                    _authState.value = AuthState.EmailVerificationSent
                },
                onFailure = { error ->
                    android.util.Log.e("AuthViewModel", "❌ Failed to send verification email", error)
                    _authState.value = AuthState.Error(
                        error.message ?: "Failed to send verification email. Please try again."
                    )
                }
            )
        }
    }
    
    /**
     * Check if the current user's email is verified.
     * Reloads user data from Firebase to get latest status.
     * 
     * @return true if verified, false otherwise
     */
    suspend fun checkEmailVerified(): Boolean {
        return authRepository.isEmailVerified()
    }
    
    /**
     * Reload the current user's data from Firebase.
     * Useful for checking updated email verification status.
     */
    fun reloadUser() {
        viewModelScope.launch {
            authRepository.reloadUser()
        }
    }
}
