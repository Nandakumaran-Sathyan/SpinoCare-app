package com.example.modicanalyzer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.modicanalyzer.utils.PasswordEncryption

/**
 * Entity for storing pending signup requests when offline
 * Password is encrypted using AES-256-GCM (can be decrypted for processing)
 */
@Entity(tableName = "pending_signups")
data class PendingSignupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val fullName: String,
    val email: String,
    val phone: String,
    val passwordHash: String, // Encrypted password (AES-256), NOT a hash
    val role: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: SignupStatus = SignupStatus.PENDING,
    val errorMessage: String? = null,
    val retryCount: Int = 0
) {
    enum class SignupStatus {
        PENDING,      // Waiting to be processed
        PROCESSING,   // Currently being processed
        COMPLETED,    // Successfully created account
        FAILED        // Failed (email already exists, etc.)
    }
    
    companion object {
        /**
         * Encrypt password using AES-256-GCM for secure local storage
         * Can be decrypted later when processing queue
         */
        fun encryptPassword(password: String): String {
            return PasswordEncryption.encrypt(password)
        }
        
        /**
         * Decrypt password when processing queued signup
         */
        fun decryptPassword(encryptedPassword: String): String {
            return PasswordEncryption.decrypt(encryptedPassword)
        }
        
        /**
         * Create entity from signup form data
         */
        fun fromSignupData(
            fullName: String,
            email: String,
            phone: String,
            password: String,
            role: String
        ): PendingSignupEntity {
            return PendingSignupEntity(
                fullName = fullName,
                email = email,
                phone = phone,
                passwordHash = encryptPassword(password), // Now encrypted, not hashed
                role = role
            )
        }
    }
}
