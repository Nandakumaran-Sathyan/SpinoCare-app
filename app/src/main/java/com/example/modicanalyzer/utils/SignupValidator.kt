package com.example.modicanalyzer.utils

import android.util.Patterns

/**
 * Comprehensive validation utility for signup form
 */
object SignupValidator {
    
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )
    
    /**
     * Validates full name
     * Rules: Only alphabets and spaces, 2-50 characters
     */
    fun validateFullName(name: String): ValidationResult {
        return when {
            name.isBlank() -> ValidationResult(false, "Name is required")
            name.length < 2 -> ValidationResult(false, "Name must be at least 2 characters")
            name.length > 50 -> ValidationResult(false, "Name must not exceed 50 characters")
            !name.matches(Regex("^[a-zA-Z\\s]+$")) -> ValidationResult(false, "Name must contain only letters and spaces")
            else -> ValidationResult(true)
        }
    }
    
    /**
     * Validates email address
     * Rules: Must be a valid email format
     */
    fun validateEmail(email: String): ValidationResult {
        return when {
            email.isBlank() -> ValidationResult(false, "Email is required")
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> ValidationResult(false, "Please enter a valid email address")
            email.length > 100 -> ValidationResult(false, "Email is too long")
            else -> ValidationResult(true)
        }
    }
    
    /**
     * Validates phone number
     * Rules: Must be exactly 10 digits, only numbers
     */
    fun validatePhoneNumber(phone: String): ValidationResult {
        val digitsOnly = phone.replace(Regex("[^0-9]"), "")
        return when {
            phone.isBlank() -> ValidationResult(false, "Phone number is required")
            !phone.matches(Regex("^[0-9]+$")) -> ValidationResult(false, "Phone number must contain only digits")
            digitsOnly.length < 10 -> ValidationResult(false, "Phone number must be exactly 10 digits")
            digitsOnly.length > 10 -> ValidationResult(false, "Phone number must be exactly 10 digits")
            else -> ValidationResult(true)
        }
    }
    
    /**
     * Validates password
     * Rules: 
     * - At least 8 characters
     * - Must contain at least one uppercase letter
     * - Must contain at least one lowercase letter
     * - Must contain at least one digit
     * - Must contain at least one special character (@#$%^&+=!?*()_-.)
     */
    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isBlank() -> ValidationResult(false, "Password is required")
            password.length < 8 -> ValidationResult(false, "Password must be at least 8 characters")
            !password.matches(Regex(".*[A-Z].*")) -> ValidationResult(false, "Password must contain at least one uppercase letter")
            !password.matches(Regex(".*[a-z].*")) -> ValidationResult(false, "Password must contain at least one lowercase letter")
            !password.matches(Regex(".*[0-9].*")) -> ValidationResult(false, "Password must contain at least one number")
            !password.matches(Regex(".*[@#\$%^&+=!?*()_\\-.].*")) -> ValidationResult(false, "Password must contain at least one special character (@#\$%^&+=!?*()_-.)")
            password.length > 50 -> ValidationResult(false, "Password is too long (max 50 characters)")
            else -> ValidationResult(true)
        }
    }
    
    /**
     * Validates password confirmation
     */
    fun validateConfirmPassword(password: String, confirmPassword: String): ValidationResult {
        return when {
            confirmPassword.isBlank() -> ValidationResult(false, "Please confirm your password")
            password != confirmPassword -> ValidationResult(false, "Passwords do not match")
            else -> ValidationResult(true)
        }
    }
    
    /**
     * Validates entire signup form
     * Returns map of field names to validation results
     */
    fun validateSignupForm(
        fullName: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String,
        acceptedTerms: Boolean
    ): Map<String, ValidationResult> {
        val results = mutableMapOf<String, ValidationResult>()
        
        results["fullName"] = validateFullName(fullName)
        results["email"] = validateEmail(email)
        results["phone"] = validatePhoneNumber(phone)
        results["password"] = validatePassword(password)
        results["confirmPassword"] = validateConfirmPassword(password, confirmPassword)
        
        if (!acceptedTerms) {
            results["terms"] = ValidationResult(false, "You must accept the terms and conditions")
        } else {
            results["terms"] = ValidationResult(true)
        }
        
        return results
    }
    
    /**
     * Gets the first error message from validation results
     */
    fun getFirstError(validationResults: Map<String, ValidationResult>): String? {
        return validationResults.values.firstOrNull { !it.isValid }?.errorMessage
    }
    
    /**
     * Checks if all validations passed
     */
    fun isAllValid(validationResults: Map<String, ValidationResult>): Boolean {
        return validationResults.values.all { it.isValid }
    }
}
