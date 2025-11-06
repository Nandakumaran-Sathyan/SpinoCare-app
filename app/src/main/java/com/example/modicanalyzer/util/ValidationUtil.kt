package com.example.modicanalyzer.util

import android.util.Patterns
import java.util.regex.Pattern

/**
 * Utility for validating user input fields.
 * Ensures data quality and security before processing.
 */
object ValidationUtil {
    
    // Password requirements
    private const val MIN_PASSWORD_LENGTH = 8
    private const val MAX_PASSWORD_LENGTH = 128
    
    // Name requirements
    private const val MIN_NAME_LENGTH = 2
    private const val MAX_NAME_LENGTH = 50
    
    /**
     * Validation result with success status and error message.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    ) {
        companion object {
            fun success() = ValidationResult(true, null)
            fun error(message: String) = ValidationResult(false, message)
        }
    }
    
    /**
     * Validate email address.
     * 
     * Rules:
     * - Not blank
     * - Valid email format (using Android's Patterns)
     * - Reasonable length
     * 
     * @param email Email to validate
     * @return ValidationResult with success/error
     */
    fun validateEmail(email: String): ValidationResult {
        return when {
            email.isBlank() -> ValidationResult.error("Email is required")
            email.length > 254 -> ValidationResult.error("Email is too long")
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> 
                ValidationResult.error("Please enter a valid email address")
            else -> ValidationResult.success()
        }
    }
    
    /**
     * Validate password.
     * 
     * Rules:
     * - Minimum 8 characters
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one digit
     * - At least one special character
     * - No whitespace
     * 
     * @param password Password to validate
     * @return ValidationResult with success/error
     */
    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isBlank() -> 
                ValidationResult.error("Password is required")
            
            password.length < MIN_PASSWORD_LENGTH -> 
                ValidationResult.error("Password must be at least $MIN_PASSWORD_LENGTH characters")
            
            password.length > MAX_PASSWORD_LENGTH -> 
                ValidationResult.error("Password is too long (max $MAX_PASSWORD_LENGTH characters)")
            
            !password.any { it.isUpperCase() } -> 
                ValidationResult.error("Password must contain at least one uppercase letter")
            
            !password.any { it.isLowerCase() } -> 
                ValidationResult.error("Password must contain at least one lowercase letter")
            
            !password.any { it.isDigit() } -> 
                ValidationResult.error("Password must contain at least one number")
            
            !password.any { !it.isLetterOrDigit() } -> 
                ValidationResult.error("Password must contain at least one special character")
            
            password.contains(" ") -> 
                ValidationResult.error("Password cannot contain spaces")
            
            else -> ValidationResult.success()
        }
    }
    
    /**
     * Validate password confirmation.
     * 
     * @param password Original password
     * @param confirmPassword Confirmation password
     * @return ValidationResult with success/error
     */
    fun validatePasswordConfirmation(password: String, confirmPassword: String): ValidationResult {
        return when {
            confirmPassword.isBlank() -> 
                ValidationResult.error("Please confirm your password")
            
            password != confirmPassword -> 
                ValidationResult.error("Passwords do not match")
            
            else -> ValidationResult.success()
        }
    }
    
    /**
     * Validate display name.
     * 
     * Rules:
     * - Not blank (optional field)
     * - Minimum 2 characters if provided
     * - Maximum 50 characters
     * - Only letters, spaces, hyphens, and apostrophes
     * 
     * @param name Display name to validate
     * @param required Whether the field is required
     * @return ValidationResult with success/error
     */
    fun validateDisplayName(name: String, required: Boolean = false): ValidationResult {
        if (name.isBlank()) {
            return if (required) {
                ValidationResult.error("Name is required")
            } else {
                ValidationResult.success()
            }
        }
        
        return when {
            name.length < MIN_NAME_LENGTH -> 
                ValidationResult.error("Name must be at least $MIN_NAME_LENGTH characters")
            
            name.length > MAX_NAME_LENGTH -> 
                ValidationResult.error("Name is too long (max $MAX_NAME_LENGTH characters)")
            
            !name.matches(Regex("^[a-zA-Z\\s'-]+$")) -> 
                ValidationResult.error("Name can only contain letters, spaces, hyphens, and apostrophes")
            
            else -> ValidationResult.success()
        }
    }
    
    /**
     * Get password strength.
     * 
     * @param password Password to check
     * @return Strength level: Weak, Medium, Strong, Very Strong
     */
    fun getPasswordStrength(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.WEAK
        
        var score = 0
        
        // Length
        when {
            password.length >= 16 -> score += 3
            password.length >= 12 -> score += 2
            password.length >= 8 -> score += 1
        }
        
        // Character variety
        if (password.any { it.isUpperCase() }) score += 1
        if (password.any { it.isLowerCase() }) score += 1
        if (password.any { it.isDigit() }) score += 1
        if (password.any { !it.isLetterOrDigit() }) score += 1
        
        // No repeated characters
        if (!password.windowed(3).any { it[0] == it[1] && it[1] == it[2] }) score += 1
        
        // No sequential characters (abc, 123)
        if (!containsSequential(password)) score += 1
        
        return when {
            score >= 9 -> PasswordStrength.VERY_STRONG
            score >= 7 -> PasswordStrength.STRONG
            score >= 5 -> PasswordStrength.MEDIUM
            else -> PasswordStrength.WEAK
        }
    }
    
    /**
     * Check if password contains sequential characters.
     */
    private fun containsSequential(password: String): Boolean {
        val lowerPassword = password.lowercase()
        for (i in 0 until lowerPassword.length - 2) {
            val first = lowerPassword[i].code
            val second = lowerPassword[i + 1].code
            val third = lowerPassword[i + 2].code
            
            if (second == first + 1 && third == second + 1) {
                return true
            }
        }
        return false
    }
    
    /**
     * Password strength levels.
     */
    enum class PasswordStrength(val label: String, val color: Long) {
        WEAK("Weak", 0xFFE53935),           // Red
        MEDIUM("Medium", 0xFFFB8C00),       // Orange
        STRONG("Strong", 0xFF43A047),       // Green
        VERY_STRONG("Very Strong", 0xFF2E7D32) // Dark Green
    }
    
    /**
     * Validate all signup fields at once.
     * 
     * @return Map of field names to validation results
     */
    fun validateSignupForm(
        email: String,
        password: String,
        confirmPassword: String,
        displayName: String?
    ): Map<String, ValidationResult> {
        return mapOf(
            "email" to validateEmail(email),
            "password" to validatePassword(password),
            "confirmPassword" to validatePasswordConfirmation(password, confirmPassword),
            "displayName" to validateDisplayName(displayName ?: "", required = false)
        )
    }
    
    /**
     * Validate all login fields at once.
     * 
     * @return Map of field names to validation results
     */
    fun validateLoginForm(
        email: String,
        password: String
    ): Map<String, ValidationResult> {
        return mapOf(
            "email" to validateEmail(email),
            "password" to if (password.isBlank()) {
                ValidationResult.error("Password is required")
            } else {
                ValidationResult.success()
            }
        )
    }
}
