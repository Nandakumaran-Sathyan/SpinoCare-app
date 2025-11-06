package com.example.modicanalyzer.data.remote

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore Helper - Restructured for offline-first support
 * 
 * Database: (default) - FREE Spark Plan compatible
 * 
 * New Structure (supports offline users):
 * /users/{userId}
 *   - name, email, createdAt, profileImage, role
 * 
 * /mri_analyses/{analysisId}
 *   - userId, localUserId (for offline users)
 *   - t1ImageUrl, t2ImageUrl
 *   - analysisResult, confidence, metadata
 *   - createdAt, updatedAt
 * 
 * Benefits:
 * - Works with offline signup (uses localUserId until Firebase sync)
 * - Image uploads can be queued independently of user sync
 * - Easier querying and management
 */
@Singleton
class FirestoreHelper @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    
    companion object {
        private const val TAG = "FirestoreHelper"
        private const val USERS_COLLECTION = "users"
        private const val DATA_ENTRIES_COLLECTION = "data_entries"
        private const val MRI_ANALYSES_COLLECTION = "mri_analyses"
    }
    
    // ============================================
    // USER PROFILE OPERATIONS
    // ============================================
    
    /**
     * Create or update user profile in Firestore
     * 
     * @param userId Firebase Auth UID
     * @param name User's full name
     * @param email User's email
     * @param role Optional role (e.g., "student", "admin", "patient", "doctor")
     * @param profileImageUrl Optional profile image URL from Firebase Storage
     */
    suspend fun createOrUpdateUserProfile(
        userId: String,
        name: String,
        email: String,
        role: String? = null,
        profileImageUrl: String? = null
    ): Result<Unit> {
        return try {
            val userProfile = buildMap {
                put("name", name)
                put("email", email)
                if (role != null) put("role", role)
                if (profileImageUrl != null) put("profileImage", profileImageUrl)
                put("updatedAt", FieldValue.serverTimestamp())
                
                // Only set createdAt on first creation (won't overwrite if exists)
                put("createdAt", FieldValue.serverTimestamp())
            }
            
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .set(userProfile, SetOptions.merge()) // merge = update existing fields
                .await()
            
            Log.d(TAG, "✅ User profile created/updated: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating/updating user profile", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get user profile from Firestore
     * 
     * @param userId Firebase Auth UID
     * @return User data as Map, or null if not found
     */
    suspend fun getUserProfile(userId: String): Result<Map<String, Any>?> {
        return try {
            val document = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()
            
            if (document.exists()) {
                Log.d(TAG, "✅ User profile retrieved: $userId")
                Result.success(document.data)
            } else {
                Log.w(TAG, "⚠️ User profile not found: $userId")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting user profile", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update user's profile image URL
     * 
     * @param userId Firebase Auth UID
     * @param imageUrl URL from Firebase Storage
     */
    suspend fun updateProfileImage(userId: String, imageUrl: String): Result<Unit> {
        return try {
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .update(
                    mapOf(
                        "profileImage" to imageUrl,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            
            Log.d(TAG, "✅ Profile image updated: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating profile image", e)
            Result.failure(e)
        }
    }
    
    // ============================================
    // DATA ENTRIES (Images/Scans/Uploads)
    // ============================================
    
    /**
     * Add a new data entry (image upload with metadata)
     * 
     * @param userId Firebase Auth UID
     * @param imageUrl URL from Firebase Storage
     * @param caption Short description
     * @param metadata Additional data (resolution, type, analysis results, etc.)
     * @return Entry ID if successful
     */
    suspend fun addDataEntry(
        userId: String,
        imageUrl: String,
        caption: String?,
        metadata: Map<String, Any>? = null
    ): Result<String> {
        return try {
            val entry = buildMap {
                put("imageUrl", imageUrl)
                if (caption != null) put("caption", caption)
                if (metadata != null) put("metadata", metadata)
                put("createdAt", FieldValue.serverTimestamp())
            }
            
            val documentRef = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(DATA_ENTRIES_COLLECTION)
                .add(entry) // auto-generate ID
                .await()
            
            Log.d(TAG, "✅ Data entry added: ${documentRef.id}")
            Result.success(documentRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding data entry", e)
            Result.failure(e)
        }
    }
    
    /**
     * Add MRI analysis entry with T1 and T2 images (LEGACY - uses subcollection)
     * 
     * @deprecated Use addMRIAnalysis() instead for offline-first support
     */
    suspend fun addMRIAnalysisEntry(
        userId: String,
        t1ImageUrl: String,
        t2ImageUrl: String,
        analysisResult: String,
        confidence: Float,
        metadata: Map<String, Any>? = null
    ): Result<String> {
        return try {
            val entry = buildMap {
                put("type", "mri_analysis")
                put("userId", userId)
                put("t1ImageUrl", t1ImageUrl)
                put("t2ImageUrl", t2ImageUrl)
                put("analysisResult", analysisResult)
                put("confidence", confidence)
                if (metadata != null) put("metadata", metadata)
                put("createdAt", FieldValue.serverTimestamp())
            }
            
            val documentRef = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(DATA_ENTRIES_COLLECTION)
                .add(entry)
                .await()
            
            Log.d(TAG, "✅ MRI analysis entry added (legacy): ${documentRef.id}")
            Result.success(documentRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding MRI analysis entry", e)
            Result.failure(e)
        }
    }
    
    /**
     * Add MRI analysis to top-level collection (NEW - supports offline users)
     * 
     * This method works with both online and offline users:
     * - Online users: uses Firebase Auth UID
     * - Offline users: uses local UUID, updated later when user syncs
     * 
     * @param userId Firebase Auth UID or local UUID (for offline users)
     * @param localUserId Local UUID (always set, helps track offline entries)
     * @param isOfflineUser True if user is not yet synced to Firebase
     * @param t1ImageUrl T1-weighted image URL (empty if queued for upload)
     * @param t2ImageUrl T2-weighted image URL (empty if queued for upload)
     * @param analysisResult Analysis result string
     * @param confidence Confidence score (0.0 - 1.0)
     * @param metadata Additional metadata
     * @return Analysis ID if successful
     */
    suspend fun addMRIAnalysis(
        userId: String,
        localUserId: String,
        isOfflineUser: Boolean = false,
        t1ImageUrl: String,
        t2ImageUrl: String,
        analysisResult: String,
        confidence: Float,
        metadata: Map<String, Any>? = null
    ): Result<String> {
        return try {
            val entry = buildMap {
                put("userId", userId)
                put("localUserId", localUserId)
                put("isOfflineUser", isOfflineUser)
                put("t1ImageUrl", t1ImageUrl)
                put("t2ImageUrl", t2ImageUrl)
                put("analysisResult", analysisResult)
                put("confidence", confidence)
                if (metadata != null) put("metadata", metadata)
                put("createdAt", FieldValue.serverTimestamp())
            }
            
            val documentRef = firestore.collection(MRI_ANALYSES_COLLECTION)
                .add(entry)
                .await()
            
            Log.d(TAG, "✅ MRI analysis added: ${documentRef.id} (userId=$userId, offline=$isOfflineUser)")
            Result.success(documentRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding MRI analysis", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update userId for offline analyses when user syncs to Firebase.
     * Called by AuthRepository.syncOfflineUsers() after successful Firebase migration.
     * 
     * @param localUserId Local UUID of the offline user
     * @param firebaseUserId New Firebase Auth UID
     * @return Number of updated analyses
     */
    suspend fun updateAnalysesUserId(localUserId: String, firebaseUserId: String): Result<Int> {
        return try {
            val snapshot = firestore.collection(MRI_ANALYSES_COLLECTION)
                .whereEqualTo("localUserId", localUserId)
                .whereEqualTo("isOfflineUser", true)
                .get()
                .await()
            
            var updateCount = 0
            for (doc in snapshot.documents) {
                doc.reference.update(
                    mapOf(
                        "userId" to firebaseUserId,
                        "isOfflineUser" to false,
                        "syncedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                updateCount++
            }
            
            Log.d(TAG, "✅ Updated $updateCount analyses from localUserId=$localUserId to userId=$firebaseUserId")
            Result.success(updateCount)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating analyses userId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all data entries for a user
     * 
     * @param userId Firebase Auth UID
     * @return List of entries (newest first)
     */
    suspend fun getUserDataEntries(userId: String): Result<List<DataEntry>> {
        return try {
            val snapshot = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(DATA_ENTRIES_COLLECTION)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            
            val entries = snapshot.documents.mapNotNull { doc ->
                try {
                    DataEntry(
                        id = doc.id,
                        imageUrl = doc.getString("imageUrl") ?: "",
                        caption = doc.getString("caption"),
                        metadata = doc.get("metadata") as? Map<String, Any>,
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Skipping invalid entry: ${doc.id}")
                    null
                }
            }
            
            Log.d(TAG, "✅ Retrieved ${entries.size} data entries for user: $userId")
            Result.success(entries)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting data entries", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get a specific data entry
     * 
     * @param userId Firebase Auth UID
     * @param entryId Entry document ID
     */
    suspend fun getDataEntry(userId: String, entryId: String): Result<DataEntry?> {
        return try {
            val document = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(DATA_ENTRIES_COLLECTION)
                .document(entryId)
                .get()
                .await()
            
            if (document.exists()) {
                val entry = DataEntry(
                    id = document.id,
                    imageUrl = document.getString("imageUrl") ?: "",
                    caption = document.getString("caption"),
                    metadata = document.get("metadata") as? Map<String, Any>,
                    createdAt = document.getTimestamp("createdAt")?.toDate()?.time
                )
                Result.success(entry)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting data entry", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update a data entry
     * 
     * @param userId Firebase Auth UID
     * @param entryId Entry document ID
     * @param updates Fields to update
     */
    suspend fun updateDataEntry(
        userId: String,
        entryId: String,
        updates: Map<String, Any>
    ): Result<Unit> {
        return try {
            val updateData = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(DATA_ENTRIES_COLLECTION)
                .document(entryId)
                .update(updateData)
                .await()
            
            Log.d(TAG, "✅ Data entry updated: $entryId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating data entry", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete a data entry
     * 
     * @param userId Firebase Auth UID
     * @param entryId Entry document ID
     */
    suspend fun deleteDataEntry(userId: String, entryId: String): Result<Unit> {
        return try {
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(DATA_ENTRIES_COLLECTION)
                .document(entryId)
                .delete()
                .await()
            
            Log.d(TAG, "✅ Data entry deleted: $entryId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting data entry", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update analysis entry with uploaded image URLs.
     * Called by ImageUploadWorker after successful image upload.
     * Works with new top-level mri_analyses collection.
     * 
     * @param analysisId Analysis document ID
     * @param t1Url T1-weighted image URL from Firebase Storage
     * @param t2Url T2-weighted image URL from Firebase Storage
     */
    suspend fun updateAnalysisImageUrls(
        analysisId: String,
        t1Url: String,
        t2Url: String
    ): Result<Unit> {
        return try {
            val updates = mapOf(
                "t1ImageUrl" to t1Url,
                "t2ImageUrl" to t2Url,
                "imagesUploaded" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            firestore.collection(MRI_ANALYSES_COLLECTION)
                .document(analysisId)
                .update(updates)
                .await()
            
            Log.d(TAG, "✅ Analysis image URLs updated: $analysisId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating analysis image URLs", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update analysis entry with uploaded image URLs (LEGACY - subcollection method)
     * 
     * @deprecated Use updateAnalysisImageUrls() instead
     */
    suspend fun updateAnalysisImageUrlsLegacy(
        userId: String,
        analysisId: String,
        t1Url: String,
        t2Url: String
    ): Result<Unit> {
        return try {
            val updates = mapOf(
                "t1ImageUrl" to t1Url,
                "t2ImageUrl" to t2Url,
                "imagesUploaded" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(DATA_ENTRIES_COLLECTION)
                .document(analysisId)
                .update(updates)
                .await()
            
            Log.d(TAG, "✅ Analysis image URLs updated (legacy): $analysisId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating analysis image URLs", e)
            Result.failure(e)
        }
    }
}

/**
 * Data class representing a user's data entry
 */
data class DataEntry(
    val id: String,
    val imageUrl: String = "",  // For single images (legacy)
    val caption: String? = null,
    val metadata: Map<String, Any>? = null,
    val createdAt: Long? = null,
    // MRI-specific fields
    val type: String? = null,  // "mri_analysis", etc.
    val t1ImageUrl: String? = null,
    val t2ImageUrl: String? = null,
    val analysisResult: String? = null,
    val confidence: Float? = null
)
