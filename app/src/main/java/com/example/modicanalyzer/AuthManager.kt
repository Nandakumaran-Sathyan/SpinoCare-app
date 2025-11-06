package com.example.modicanalyzer

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

/**
 * AuthManager - DEPRECATED
 * 
 * This class is kept for backward compatibility only.
 * New code should use AuthRepository with Hilt dependency injection.
 * 
 * SECURITY NOTE: Demo mode and localLogin() have been removed.
 * All authentication now goes through Firebase Auth or Room database.
 */
@Deprecated(
    message = "Use AuthRepository with Hilt dependency injection instead",
    replaceWith = ReplaceWith("AuthRepository", "com.example.modicanalyzer.data.repository.AuthRepository")
)
class AuthManager(private val context: Context) {
    companion object {
        private const val PREF_NAME = "modic_auth"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_ROLE = "user_role"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val firebaseAuth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * DEPRECATED - No longer supports demo mode.
     * Use AuthRepository.login() instead.
     */
    @Deprecated("Use AuthRepository.login() instead")
    fun localLogin(email: String, name: String, role: String) {
        // REMOVED: Demo mode is no longer supported
        throw UnsupportedOperationException(
            "Demo mode has been removed for security. Please use proper authentication via AuthRepository."
        )
    }

    /**
     * Logout from both Firebase and local session.
     */
    fun logout() {
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            // ignore
        }
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_NAME)
            remove(KEY_USER_ROLE)
            apply()
        }
    }

    /**
     * Returns true if Firebase has a current user.
     * Local session check removed for security.
     */
    fun isLoggedIn(): Boolean {
        val firebaseUser: FirebaseUser? = try { 
            firebaseAuth?.currentUser 
        } catch (e: Exception) { 
            null 
        }
        return firebaseUser != null
    }

    fun getUserEmail(): String? = try { 
        firebaseAuth?.currentUser?.email
    } catch (e: Exception) { 
        null
    }

    fun getUserName(): String? = try {
        firebaseAuth?.currentUser?.displayName
    } catch (e: Exception) {
        null
    }

    fun getUserRole(): String? = "Patient" // Default role

    /**
     * Sign in with Firebase using email and password.
     */
    fun signInWithFirebase(
        email: String, 
        password: String, 
        onSuccess: (FirebaseUser) -> Unit, 
        onFailure: (Exception) -> Unit
    ) {
        val auth = firebaseAuth
        if (auth == null) {
            onFailure(IllegalStateException("FirebaseAuth not available"))
            return
        }
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                result.user?.let { onSuccess(it) } ?: onFailure(IllegalStateException("No user returned"))
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    /**
     * Create a new user with Firebase.
     */
    fun createUserWithFirebase(
        email: String, 
        password: String, 
        onSuccess: (FirebaseUser) -> Unit, 
        onFailure: (Exception) -> Unit
    ) {
        val auth = firebaseAuth
        if (auth == null) {
            onFailure(IllegalStateException("FirebaseAuth not available"))
            return
        }
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                result.user?.let { onSuccess(it) } ?: onFailure(IllegalStateException("No user returned"))
            }
            .addOnFailureListener { ex -> 
                onFailure(ex) 
            }
    }

    /**
     * Save user profile to SharedPreferences (for display purposes only).
     * Does NOT grant authentication - user must be authenticated via Firebase.
     */
    fun saveUserProfileIfNeeded(email: String, displayName: String, role: String) {
        // Only save if user is actually logged in via Firebase
        if (firebaseAuth?.currentUser != null) {
            prefs.edit().apply {
                putBoolean(KEY_IS_LOGGED_IN, true)
                putString(KEY_USER_EMAIL, email)
                putString(KEY_USER_NAME, displayName)
                putString(KEY_USER_ROLE, role)
                apply()
            }
        }
    }

    /**
     * DEPRECATED - Demo mode removed.
     */
    @Deprecated("Demo mode has been removed for security")
    fun getDemoUserInfo(): DemoUserInfo {
        throw UnsupportedOperationException("Demo mode has been removed for security")
    }

    /**
     * Check if the user is authenticated with Firebase (real account).
     */
    fun isFirebaseAuthenticated(): Boolean = try {
        firebaseAuth?.currentUser != null
    } catch (e: Exception) {
        false
    }

    @Deprecated("Demo mode has been removed")
    data class DemoUserInfo(
        val email: String = "",
        val name: String = "",
        val role: String = ""
    )
}
