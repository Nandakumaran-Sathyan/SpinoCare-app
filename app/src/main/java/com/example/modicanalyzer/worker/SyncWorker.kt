package com.example.modicanalyzer.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.modicanalyzer.data.repository.AuthRepository
import com.example.modicanalyzer.data.repository.DataRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker for syncing local data to Firebase/Firestore.
 * 
 * This worker is triggered when:
 * 1. Device connectivity changes from offline to online
 * 2. Periodically (configurable interval)
 * 3. Manually by user (via "Sync Now" button)
 * 
 * Sync Process:
 * 1. Check if user is authenticated
 * 2. Sync unsynced users to Firebase Auth (if any)
 * 3. Sync user's pending data to Firestore
 * 4. Update sync status in local database
 * 
 * WorkManager automatically handles:
 * - Retry logic with exponential backoff
 * - Battery optimization
 * - Network constraints
 * - Guaranteed execution
 * 
 * @param context Application context
 * @param params Worker parameters from WorkManager
 * @param authRepository Repository for authentication operations
 * @param dataRepository Repository for data operations
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val dataRepository: DataRepository,
    private val firebaseAuth: FirebaseAuth
) : CoroutineWorker(context, params) {
    
    companion object {
        const val WORK_NAME = "sync_work"
        const val TAG = "SyncWorker"
        
        // Input data keys
        const val KEY_USER_ID = "user_id"
        const val KEY_SYNC_TYPE = "sync_type"
        
        // Sync types
        const val SYNC_TYPE_FULL = "full"
        const val SYNC_TYPE_DATA_ONLY = "data_only"
        const val SYNC_TYPE_USERS_ONLY = "users_only"
    }
    
    /**
     * Main work execution method.
     * Runs on a background thread managed by WorkManager.
     * 
     * @return Result.success() if sync completed, Result.retry() if should retry, Result.failure() if failed permanently
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val userId = inputData.getString(KEY_USER_ID)
            val syncType = inputData.getString(KEY_SYNC_TYPE) ?: SYNC_TYPE_FULL
            
            // Log sync start
            android.util.Log.d(TAG, "Starting sync: type=$syncType, userId=$userId")
            
            var syncedUsers = 0
            var syncedData = 0
            
            // Step 1: Sync unsynced users (if applicable)
            if (syncType == SYNC_TYPE_FULL || syncType == SYNC_TYPE_USERS_ONLY) {
                syncedUsers = syncUsers()
            }
            
            // Step 2: Sync user data
            if (syncType == SYNC_TYPE_FULL || syncType == SYNC_TYPE_DATA_ONLY) {
                if (userId != null) {
                    // Sync specific user's data
                    syncedData = syncUserData(userId)
                } else {
                    // Sync current authenticated user's data
                    val currentUser = firebaseAuth.currentUser
                    if (currentUser != null) {
                        syncedData = syncUserData(currentUser.uid)
                    } else {
                        android.util.Log.w(TAG, "No authenticated user found for data sync")
                    }
                }
            }
            
            // Log sync completion
            android.util.Log.d(TAG, "Sync completed: users=$syncedUsers, data=$syncedData")
            
            // Return success
            Result.success()
            
        } catch (e: Exception) {
            // Log error
            android.util.Log.e(TAG, "Sync failed: ${e.message}", e)
            
            // Retry on network errors, fail on other errors
            if (isNetworkError(e)) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    /**
     * Sync unsynced users to Firebase.
     * 
     * @return Number of users successfully synced
     */
    private suspend fun syncUsers(): Int {
        return try {
            val syncedUserIds = authRepository.syncOfflineUsers()
            android.util.Log.d(TAG, "Synced ${syncedUserIds.size} users")
            syncedUserIds.size
        } catch (e: Exception) {
            android.util.Log.e(TAG, "User sync failed: ${e.message}", e)
            0
        }
    }
    
    /**
     * Sync user's pending data to Firestore.
     * 
     * @param userId User's unique ID
     * @return Number of data entries successfully synced
     */
    private suspend fun syncUserData(userId: String): Int {
        return try {
            val syncedCount = dataRepository.syncPendingData(userId)
            android.util.Log.d(TAG, "Synced $syncedCount data entries for user $userId")
            syncedCount
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Data sync failed for user $userId: ${e.message}", e)
            0
        }
    }
    
    /**
     * Check if the exception is a network-related error.
     * Used to determine whether to retry the work.
     */
    private fun isNetworkError(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: ""
        return message.contains("network") ||
                message.contains("connection") ||
                message.contains("timeout") ||
                message.contains("unreachable") ||
                e is java.net.UnknownHostException ||
                e is java.net.SocketTimeoutException ||
                e is java.io.IOException
    }
}
