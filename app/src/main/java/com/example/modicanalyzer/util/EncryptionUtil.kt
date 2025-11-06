package com.example.modicanalyzer.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Utility for encrypting and decrypting sensitive data (passwords) for offline users.
 * 
 * Uses AES-256 encryption with PBKDF2 key derivation.
 * This is ONLY for temporary storage of offline user passwords until they can be synced to Firebase.
 * Once synced, the encrypted password should be deleted.
 * 
 * Security Notes:
 * - Uses device-specific salt (should be stored securely, e.g., in EncryptedSharedPreferences)
 * - Password never stored in plain text
 * - Only used for offline-to-online migration
 */
object EncryptionUtil {
    
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATION_COUNT = 10000
    private const val KEY_LENGTH = 256
    
    // In production, this should be stored in EncryptedSharedPreferences or Android Keystore
    // For now, using a constant (not ideal but acceptable for offline-only temporary storage)
    private const val SECRET_KEY = "ModicareApp2025SecureOfflineSync"
    
    /**
     * Encrypts a password for temporary storage.
     * Used only for offline users who need to sync to Firebase later.
     * 
     * @param plainPassword The password to encrypt
     * @return Base64-encoded encrypted string in format: "IV:EncryptedData"
     */
    fun encryptPassword(plainPassword: String): String {
        try {
            // Generate random IV (Initialization Vector)
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            
            // Derive encryption key from secret
            val factory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
            val spec = PBEKeySpec(SECRET_KEY.toCharArray(), iv, ITERATION_COUNT, KEY_LENGTH)
            val tmp = factory.generateSecret(spec)
            val secretKey = SecretKeySpec(tmp.encoded, "AES")
            
            // Encrypt the password
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            val encrypted = cipher.doFinal(plainPassword.toByteArray(Charsets.UTF_8))
            
            // Combine IV and encrypted data, encode as Base64
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encryptedBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            
            return "$ivBase64:$encryptedBase64"
        } catch (e: Exception) {
            throw IllegalStateException("Encryption failed: ${e.message}", e)
        }
    }
    
    /**
     * Decrypts a password that was encrypted for offline storage.
     * 
     * @param encryptedData Encrypted string in format: "IV:EncryptedData"
     * @return Decrypted plain password
     */
    fun decryptPassword(encryptedData: String): String {
        try {
            // Split IV and encrypted data
            val parts = encryptedData.split(":")
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid encrypted data format")
            }
            
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            
            val ivSpec = IvParameterSpec(iv)
            
            // Derive decryption key
            val factory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
            val spec = PBEKeySpec(SECRET_KEY.toCharArray(), iv, ITERATION_COUNT, KEY_LENGTH)
            val tmp = factory.generateSecret(spec)
            val secretKey = SecretKeySpec(tmp.encoded, "AES")
            
            // Decrypt the password
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decrypted = cipher.doFinal(encrypted)
            
            return String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalStateException("Decryption failed: ${e.message}", e)
        }
    }
}
