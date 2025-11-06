package com.example.modicanalyzer.data.model

/**
 * Sealed class representing different authentication states in the app.
 * This provides type-safe state management for the authentication flow.
 */
sealed class AuthState {
    /**
     * Initial idle state before any authentication action
     */
    object Idle : AuthState()
    
    /**
     * Authentication is in progress (login/signup)
     */
    object Loading : AuthState()
    
    /**
     * User successfully authenticated
     * @param userId The Firebase UID of the authenticated user
     * @param email User's email address
     * @param isFirebaseAuth True if authenticated via Firebase, false if offline-only
     */
    data class Success(
        val userId: String,
        val email: String,
        val isFirebaseAuth: Boolean
    ) : AuthState()
    
    /**
     * Authentication failed
     * @param message Error message describing the failure
     */
    data class Error(val message: String) : AuthState()
    
    /**
     * User is not authenticated (logged out state)
     */
    object Unauthenticated : AuthState()
    
    /**
     * Password reset email has been sent successfully
     */
    object PasswordResetEmailSent : AuthState()
    
    /**
     * Email verification has been sent successfully
     */
    object EmailVerificationSent : AuthState()
}
