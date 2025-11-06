package com.example.modicanalyzer.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote data source for Firestore operations.
 * 
 * Handles all communication with Firebase Firestore for:
 * - User data synchronization
 * - User-generated data (scans, analysis results, etc.)
 * 
 * Structure in Firestore:
 * /users/{userId}/
 *   - profile: {email, displayName, createdAt, etc.}
 *   - data/{dataId}: {dataType, content, metadata, createdAt, etc.}
 */
@Singleton
class FirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    
    companion object {
        private const val USERS_COLLECTION = "users"
        private const val USER_DATA_COLLECTION = "data"
        private const val USER_PROFILE_DOC = "profile"
    }
    
    /**
     * Sync user profile data to Firestore.
     * Creates or updates the user's profile document.
     * 
     * @param userId Firebase UID
     * @param email User's email
     * @param displayName User's display name (optional)
     * @return Result indicating success or failure
     */
    suspend fun syncUserProfile(
        userId: String,
        email: String,
        displayName: String? = null
    ): Result<Unit> {
        return try {
            val userProfile = hashMapOf(
                "email" to email,
                "displayName" to displayName,
                "lastSyncedAt" to System.currentTimeMillis()
            )
            
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection("profile_data")
                .document(USER_PROFILE_DOC)
                .set(userProfile, SetOptions.merge())
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sync a data entry to Firestore.
     * Stores user-generated data under the user's document.
     * 
     * @param userId Firebase UID
     * @param dataId Unique identifier for the data entry
     * @param dataType Type of data (e.g., "scan_result", "analysis")
     * @param dataContent JSON string containing the actual data
     * @param metadata Additional metadata
     * @param createdAt Timestamp when the data was created
     * @return Result indicating success or failure
     */
    suspend fun syncDataEntry(
        userId: String,
        dataId: String,
        dataType: String,
        dataContent: String,
        metadata: String?,
        createdAt: Long
    ): Result<Unit> {
        return try {
            val dataEntry = hashMapOf(
                "dataType" to dataType,
                "dataContent" to dataContent,
                "metadata" to metadata,
                "createdAt" to createdAt,
                "syncedAt" to System.currentTimeMillis()
            )
            
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(USER_DATA_COLLECTION)
                .document(dataId)
                .set(dataEntry, SetOptions.merge())
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Fetch all data entries for a user from Firestore.
     * Used for initial sync or restoring data on a new device.
     * 
     * @param userId Firebase UID
     * @return List of data entries or empty list if none found
     */
    suspend fun fetchUserData(userId: String): Result<List<FirestoreDataEntry>> {
        return try {
            val snapshot = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(USER_DATA_COLLECTION)
                .get()
                .await()
            
            val dataEntries = snapshot.documents.mapNotNull { doc ->
                try {
                    FirestoreDataEntry(
                        id = doc.id,
                        dataType = doc.getString("dataType") ?: "",
                        dataContent = doc.getString("dataContent") ?: "",
                        metadata = doc.getString("metadata"),
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        syncedAt = doc.getLong("syncedAt")
                    )
                } catch (e: Exception) {
                    null
                }
            }
            
            Result.success(dataEntries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a data entry from Firestore.
     * 
     * @param userId Firebase UID
     * @param dataId ID of the data entry to delete
     * @return Result indicating success or failure
     */
    suspend fun deleteDataEntry(userId: String, dataId: String): Result<Unit> {
        return try {
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(USER_DATA_COLLECTION)
                .document(dataId)
                .delete()
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Batch sync multiple data entries at once.
     * More efficient than syncing one by one.
     * 
     * @param userId Firebase UID
     * @param dataEntries List of data entries to sync
     * @return Result with count of successfully synced entries
     */
    suspend fun batchSyncData(
        userId: String,
        dataEntries: List<BatchDataEntry>
    ): Result<Int> {
        return try {
            val batch = firestore.batch()
            val userDataRef = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(USER_DATA_COLLECTION)
            
            dataEntries.forEach { entry ->
                val docRef = userDataRef.document(entry.id)
                val data = hashMapOf(
                    "dataType" to entry.dataType,
                    "dataContent" to entry.dataContent,
                    "metadata" to entry.metadata,
                    "createdAt" to entry.createdAt,
                    "syncedAt" to System.currentTimeMillis()
                )
                batch.set(docRef, data, SetOptions.merge())
            }
            
            batch.commit().await()
            Result.success(dataEntries.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Data class representing a data entry fetched from Firestore.
 */
data class FirestoreDataEntry(
    val id: String,
    val dataType: String,
    val dataContent: String,
    val metadata: String?,
    val createdAt: Long,
    val syncedAt: Long?
)

/**
 * Data class for batch sync operations.
 */
data class BatchDataEntry(
    val id: String,
    val dataType: String,
    val dataContent: String,
    val metadata: String?,
    val createdAt: Long
)
