package com.example.modicanalyzer.utils

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secure password encryption/decryption for offline signup queue
 * Uses AES-256-GCM for secure symmetric encryption
 * 
 * WARNING: This stores encrypted passwords temporarily for offline queueing.
 * Passwords are deleted immediately after successful Firebase Auth creation.
 */
object PasswordEncryption {
    
    // In production, this should be stored in Android Keystore
    // For testing, using a hardcoded key (NOT RECOMMENDED FOR PRODUCTION)
    private const val SECRET_KEY = "ModicOfflineQueue2025SecretKey!!" // 32 chars for AES-256
    
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    
    /**
     * Encrypt password using AES-256-GCM
     */
    fun encrypt(password: String): String {
        try {
            val secretKey: SecretKey = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(password.toByteArray())
            
            // Combine IV and encrypted data
            val combined = iv + encryptedBytes
            
            return Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (e: Exception) {
            throw Exception("Encryption failed: ${e.message}", e)
        }
    }
    
    /**
     * Decrypt password using AES-256-GCM
     */
    fun decrypt(encryptedPassword: String): String {
        try {
            val combined = Base64.decode(encryptedPassword, Base64.DEFAULT)
            
            // Extract IV and encrypted data
            val iv = combined.copyOfRange(0, 12) // GCM standard IV size is 12 bytes
            val encryptedBytes = combined.copyOfRange(12, combined.size)
            
            val secretKey: SecretKey = SecretKeySpec(SECRET_KEY.toByteArray(), "AES")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes)
        } catch (e: Exception) {
            throw Exception("Decryption failed: ${e.message}", e)
        }
    }
}
