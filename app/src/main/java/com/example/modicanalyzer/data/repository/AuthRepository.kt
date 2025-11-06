package com.example.modicanalyzer.data.repository

import com.example.modicanalyzer.data.local.dao.UserDao
import com.example.modicanalyzer.data.local.entity.UserEntity
import com.example.modicanalyzer.data.model.AuthState
import com.example.modicanalyzer.data.model.SyncStatus
import com.example.modicanalyzer.data.remote.FirestoreDataSource
import com.example.modicanalyzer.data.remote.FirestoreHelper
import com.example.modicanalyzer.util.EncryptionUtil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Authentication operations.
 * 
 * Implements offline-first authentication:
 * 1. Offline Mode: Store credentials locally in Room with synced=false
 * 2. Online Mode: Authenticate with Firebase and sync local user
 * 3. Hybrid Mode: Support both offline and online users simultaneously
 * 
 * Features:
 * - Offline signup/login using local SQLite
 * - Firebase authentication when online
 * - Automatic sync of offline users when network becomes available
 * - Password hashing for security
 */
@Singleton
class AuthRepository @Inject constructor(
    private val userDao: UserDao,
    private val firestoreDataSource: FirestoreDataSource,
    private val firestoreHelper: FirestoreHelper,
    private val firebaseAuth: FirebaseAuth
) {
    
    /**
     * Sign up a new user.
     * 
     * Offline Flow:
     * 1. Create user in local Room database with PENDING sync status
     * 2. Hash password for local authentication
     * 3. Generate temporary UUID as userId
     * 
     * Online Flow:
     * 1. Create user in Firebase Auth
     * 2. Store user in Room with Firebase UID
     * 3. Sync profile to Firestore
     * 4. Mark as SYNCED
     * 
     * @param email User's email
     * @param password User's password
     * @param displayName User's display name (optional)
     * @param isOnline Whether device has network connectivity
     * @return Flow emitting AuthState changes
     */
    fun signUp(
        email: String,
        password: String,
        displayName: String?,
        isOnline: Boolean
    ): Flow<AuthState> = flow {
        emit(AuthState.Loading)
        
        try {
            // Check if email already exists locally
            val existingUser = userDao.getUserByEmail(email)
            if (existingUser != null) {
                emit(AuthState.Error("User with this email already exists"))
                return@flow
            }
            
            if (isOnline) {
                // Online signup: Use Firebase Auth
                try {
                    val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                    val firebaseUser = authResult.user
                    
                    if (firebaseUser != null) {
                        // Update display name in Firebase
                        if (displayName != null) {
                            val profileUpdates = UserProfileChangeRequest.Builder()
                                .setDisplayName(displayName)
                                .build()
                            firebaseUser.updateProfile(profileUpdates).await()
                        }
                        
                        // Send email verification
                        try {
                            firebaseUser.sendEmailVerification().await()
                            android.util.Log.d("AuthRepository", "✅ Verification email sent to $email")
                        } catch (e: Exception) {
                            android.util.Log.w("AuthRepository", "⚠️ Failed to send verification email", e)
                            // Continue even if verification email fails
                        }
                        
                        // Create user entity for local database
                        val userEntity = UserEntity(
                            userId = firebaseUser.uid,
                            email = email,
                            passwordHash = hashPassword(password),
                            displayName = displayName,
                            isFirebaseAuth = true,
                            syncStatus = SyncStatus.SYNCED,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                        
                        // Save to local database
                        userDao.insertUser(userEntity)
                        
                        // Sync profile to Firestore
                        firestoreDataSource.syncUserProfile(
                            userId = firebaseUser.uid,
                            email = email,
                            displayName = displayName
                        )
                        
                        emit(AuthState.Success(
                            userId = firebaseUser.uid,
                            email = email,
                            isFirebaseAuth = true
                        ))
                    } else {
                        emit(AuthState.Error("Failed to create user"))
                    }
                } catch (e: Exception) {
                    // If Firebase auth fails, fall back to offline mode
                    emit(AuthState.Error("Online signup failed: ${e.message}. Try offline mode."))
                }
            } else {
                // Offline signup: Store in local database with encrypted password
                val userId = UUID.randomUUID().toString()
                val userEntity = UserEntity(
                    userId = userId,
                    email = email,
                    passwordHash = hashPassword(password),
                    encryptedPassword = EncryptionUtil.encryptPassword(password), // Store encrypted for later sync
                    displayName = displayName,
                    isFirebaseAuth = false,
                    syncStatus = SyncStatus.PENDING
                )
                
                userDao.insertUser(userEntity)
                
                android.util.Log.d("AuthRepository", "Offline signup successful: userId=$userId, email=$email, syncStatus=PENDING")
                
                emit(AuthState.Success(
                    userId = userId,
                    email = email,
                    isFirebaseAuth = false
                ))
            }
        } catch (e: Exception) {
            emit(AuthState.Error("Signup failed: ${e.message}"))
        }
    }
    
    /**
     * Log in an existing user.
     * 
     * Flow:
     * 1. Validate email and password are not empty
     * 2. Check if user exists in local database
     * 3. If online, try Firebase authentication FIRST
     * 4. If offline OR Firebase fails with network error, use local authentication
     * 5. ALWAYS validate password before granting access
     * 
     * @param email User's email
     * @param password User's password
     * @param isOnline Whether device has network connectivity
     * @return Flow emitting AuthState changes
     */
    fun login(
        email: String,
        password: String,
        isOnline: Boolean
    ): Flow<AuthState> = flow {
        emit(AuthState.Loading)
        
        try {
            // Input validation
            if (email.isBlank() || password.isBlank()) {
                emit(AuthState.Error("Email and password cannot be empty"))
                return@flow
            }
            
            // Check if user exists locally
            val localUser = userDao.getUserByEmail(email)
            
            if (isOnline) {
                // Online Mode: Try Firebase authentication FIRST
                try {
                    val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                    val firebaseUser = authResult.user
                    
                    if (firebaseUser != null) {
                        // Firebase authentication successful
                        // Update or create local user record
                        val userEntity = localUser?.copy(
                            userId = firebaseUser.uid,
                            isFirebaseAuth = true,
                            syncStatus = SyncStatus.SYNCED,
                            lastSyncedAt = System.currentTimeMillis()
                        ) ?: UserEntity(
                            userId = firebaseUser.uid,
                            email = email,
                            passwordHash = hashPassword(password),
                            displayName = firebaseUser.displayName,
                            isFirebaseAuth = true,
                            syncStatus = SyncStatus.SYNCED,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                        
                        userDao.insertUser(userEntity)
                        
                        emit(AuthState.Success(
                            userId = firebaseUser.uid,
                            email = email,
                            isFirebaseAuth = true
                        ))
                    } else {
                        emit(AuthState.Error("Firebase authentication failed"))
                    }
                } catch (e: Exception) {
                    // Firebase authentication failed
                    android.util.Log.e("AuthRepository", "Firebase login failed: ${e.message}", e)
                    
                    // Check if it's a network error or invalid credentials
                    val errorMessage = e.message?.lowercase() ?: ""
                    val isNetworkError = errorMessage.contains("network") ||
                            errorMessage.contains("connection") ||
                            errorMessage.contains("timeout")
                    
                    if (isNetworkError && localUser != null) {
                        // Network error - fallback to local auth if user exists
                        if (verifyPassword(password, localUser.passwordHash)) {
                            emit(AuthState.Success(
                                userId = localUser.userId,
                                email = localUser.email,
                                isFirebaseAuth = false
                            ))
                        } else {
                            emit(AuthState.Error("Invalid password"))
                        }
                    } else {
                        // Invalid credentials or user doesn't exist in Firebase
                        val message = when {
                            errorMessage.contains("password") -> "Invalid password"
                            errorMessage.contains("user") || errorMessage.contains("email") -> "User not found. Please sign up first."
                            else -> "Login failed: ${e.message}"
                        }
                        emit(AuthState.Error(message))
                    }
                }
            } else {
                // Offline Mode: MUST validate against local database
                if (localUser == null) {
                    emit(AuthState.Error("User not found. Please connect to internet to sign up or login for the first time."))
                    return@flow
                }
                
                // Verify password
                if (verifyPassword(password, localUser.passwordHash)) {
                    emit(AuthState.Success(
                        userId = localUser.userId,
                        email = localUser.email,
                        isFirebaseAuth = localUser.isFirebaseAuth
                    ))
                } else {
                    emit(AuthState.Error("Invalid password"))
                }
            }
        } catch (e: Exception) {
            emit(AuthState.Error("Login failed: ${e.message}"))
        }
    }
    
    /**
     * Sync offline users to Firebase when network becomes available.
     * Called by SyncWorker.
     * 
     * This method:
     * 1. Finds users with PENDING sync status
     * 2. Decrypts their stored password
     * 3. Creates Firebase account with their credentials
     * 4. Updates local user record with Firebase UID
     * 5. Syncs profile to Firestore
     * 6. Deletes encrypted password for security
     * 
     * @return List of user IDs that were successfully synced
     */
    suspend fun syncOfflineUsers(): List<String> {
        val unsyncedUsers = userDao.getUnsyncedUsers()
        
        android.util.Log.d("AuthRepository", "Starting sync for ${unsyncedUsers.size} unsynced users")
        
        val syncedUserIds = mutableListOf<String>()
        
        unsyncedUsers.forEach { user ->
            try {
                android.util.Log.d("AuthRepository", "Syncing user: ${user.email}, isFirebaseAuth=${user.isFirebaseAuth}, hasEncryptedPassword=${user.encryptedPassword != null}")
                
                // Update sync status to SYNCING
                userDao.updateSyncStatus(user.userId, SyncStatus.SYNCING, null)
                
                // Check if user has encrypted password (offline-created user)
                if (!user.isFirebaseAuth && user.encryptedPassword != null) {
                    try {
                        // Decrypt the password
                        val plainPassword = EncryptionUtil.decryptPassword(user.encryptedPassword)
                        
                        // Create Firebase account
                        val authResult = firebaseAuth.createUserWithEmailAndPassword(
                            user.email,
                            plainPassword
                        ).await()
                        
                        val firebaseUser = authResult.user
                        if (firebaseUser != null) {
                            android.util.Log.d("AuthRepository", "Firebase account created successfully: ${firebaseUser.uid}")
                            
                            // Update display name in Firebase
                            if (user.displayName != null) {
                                val profileUpdates = UserProfileChangeRequest.Builder()
                                    .setDisplayName(user.displayName)
                                    .build()
                                firebaseUser.updateProfile(profileUpdates).await()
                            }
                            
                            // Update local user with Firebase UID and remove encrypted password
                            val updatedUser = user.copy(
                                userId = firebaseUser.uid,
                                isFirebaseAuth = true,
                                syncStatus = SyncStatus.SYNCED,
                                lastSyncedAt = System.currentTimeMillis(),
                                encryptedPassword = null // Delete encrypted password for security
                            )
                            userDao.insertUser(updatedUser)
                            
                            val localUserId = user.userId
                            
                            // Delete old user record with temporary UUID
                            if (user.userId != firebaseUser.uid) {
                                userDao.deleteUser(user.userId)
                            }
                            
                            // Sync profile to Firestore
                            firestoreDataSource.syncUserProfile(
                                userId = firebaseUser.uid,
                                email = user.email,
                                displayName = user.displayName
                            )
                            
                            // Migrate all offline analyses to new Firebase UID
                            firestoreHelper.updateAnalysesUserId(localUserId, firebaseUser.uid)
                                .onSuccess {
                                    android.util.Log.d("AuthRepository", "Migrated analyses from $localUserId to ${firebaseUser.uid}")
                                }
                                .onFailure { e ->
                                    android.util.Log.e("AuthRepository", "Failed to migrate analyses: ${e.message}", e)
                                }
                            
                            syncedUserIds.add(firebaseUser.uid)
                            android.util.Log.d("AuthRepository", "Synced offline user: ${user.email} -> ${firebaseUser.uid}")
                        } else {
                            userDao.updateSyncStatus(user.userId, SyncStatus.FAILED, null)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AuthRepository", "Failed to sync offline user ${user.email}: ${e.message}", e)
                        userDao.updateSyncStatus(user.userId, SyncStatus.FAILED, null)
                    }
                } else {
                    // User is already Firebase-authenticated, just sync profile
                    val result = firestoreDataSource.syncUserProfile(
                        userId = user.userId,
                        email = user.email,
                        displayName = user.displayName
                    )
                    
                    if (result.isSuccess) {
                        userDao.updateSyncStatus(
                            user.userId,
                            SyncStatus.SYNCED,
                            System.currentTimeMillis()
                        )
                        syncedUserIds.add(user.userId)
                    } else {
                        userDao.updateSyncStatus(user.userId, SyncStatus.FAILED, null)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthRepository", "User sync failed: ${e.message}", e)
                userDao.updateSyncStatus(user.userId, SyncStatus.FAILED, null)
            }
        }
        
        return syncedUserIds
    }
    
    /**
     * Get current authenticated user from Firebase.
     */
    fun getCurrentFirebaseUser() = firebaseAuth.currentUser
    
    /**
     * Check if there are any pending users waiting to be synced.
     * Useful for debugging and testing.
     */
    suspend fun hasPendingUsers(): Boolean {
        val pendingUsers = userDao.getUnsyncedUsers()
        android.util.Log.d("AuthRepository", "Pending users count: ${pendingUsers.size}")
        pendingUsers.forEach { user ->
            android.util.Log.d("AuthRepository", "  - ${user.email} (userId=${user.userId}, syncStatus=${user.syncStatus})")
        }
        return pendingUsers.isNotEmpty()
    }
    
    /**
     * Sign out the current user.
     */
    fun signOut() {
        firebaseAuth.signOut()
    }
    
    /**
     * Get user by ID from local database.
     */
    suspend fun getUserById(userId: String): UserEntity? {
        return userDao.getUserById(userId)
    }
    
    /**
     * Observe user changes.
     */
    fun observeUser(userId: String): Flow<UserEntity?> {
        return userDao.observeUserById(userId)
    }
    
    /**
     * Hash password using SHA-256 for local storage.
     * Note: In production, use bcrypt or argon2 instead.
     */
    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(password.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Verify password against stored hash.
     */
    private fun verifyPassword(password: String, hash: String): Boolean {
        return hashPassword(password) == hash
    }
    
    /**
     * Send password reset email.
     * Uses Firebase Authentication's built-in password reset email.
     * 
     * @param email The email address to send the reset link to
     * @return Result indicating success or failure
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            if (email.isBlank()) {
                return Result.failure(Exception("Email cannot be empty"))
            }
            
            android.util.Log.d("AuthRepository", "Sending password reset email to: $email")
            
            firebaseAuth.sendPasswordResetEmail(email).await()
            
            android.util.Log.d("AuthRepository", "✅ Password reset email sent successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "❌ Failed to send password reset email", e)
            Result.failure(e)
        }
    }
    
    /**
     * Send email verification to the current Firebase user.
     * Should be called after signup or when user requests verification.
     * 
     * @return Result indicating success or failure
     */
    suspend fun sendEmailVerification(): Result<Unit> {
        return try {
            val user = firebaseAuth.currentUser
            if (user == null) {
                return Result.failure(Exception("No user is currently signed in"))
            }
            
            if (user.isEmailVerified) {
                android.util.Log.d("AuthRepository", "Email is already verified for: ${user.email}")
                return Result.success(Unit)
            }
            
            android.util.Log.d("AuthRepository", "Sending verification email to: ${user.email}")
            
            user.sendEmailVerification().await()
            
            android.util.Log.d("AuthRepository", "✅ Verification email sent successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "❌ Failed to send verification email", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if the current user's email is verified.
     * 
     * @return true if email is verified, false otherwise
     */
    suspend fun isEmailVerified(): Boolean {
        return try {
            val user = firebaseAuth.currentUser
            if (user == null) {
                false
            } else {
                // Reload user to get latest email verification status
                user.reload().await()
                user.isEmailVerified
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Error checking email verification", e)
            false
        }
    }
    
    /**
     * Reload the current Firebase user to get updated information (like email verification status).
     * 
     * @return Result indicating success or failure
     */
    suspend fun reloadUser(): Result<Unit> {
        return try {
            val user = firebaseAuth.currentUser
            if (user == null) {
                return Result.failure(Exception("No user is currently signed in"))
            }
            
            user.reload().await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Failed to reload user", e)
            Result.failure(e)
        }
    }
}
