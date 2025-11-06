package com.example.modicanalyzer.data.local.dao

import androidx.room.*
import com.example.modicanalyzer.data.local.entity.UserEntity
import com.example.modicanalyzer.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for User operations in Room database.
 * 
 * Provides methods for:
 * - Inserting/updating users (offline signup and Firebase sync)
 * - Querying users by email or ID
 * - Managing sync status
 * - Observing user changes with Flow for reactive UI
 */
@Dao
interface UserDao {
    
    /**
     * Insert a new user or replace if already exists (UPSERT operation).
     * Used for both offline signup and syncing Firebase users to local database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    
    /**
     * Update an existing user's information.
     */
    @Update
    suspend fun updateUser(user: UserEntity)
    
    /**
     * Get a user by their email address.
     * Used for login validation.
     * 
     * @param email The user's email
     * @return UserEntity if found, null otherwise
     */
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?
    
    /**
     * Get a user by their unique ID.
     * 
     * @param userId The user's unique identifier
     * @return UserEntity if found, null otherwise
     */
    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?
    
    /**
     * Observe a user by ID with Flow for reactive updates.
     * UI will automatically update when user data changes.
     */
    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    fun observeUserById(userId: String): Flow<UserEntity?>
    
    /**
     * Get all users that haven't been synced to Firebase yet.
     * Used by WorkManager to identify which users need syncing.
     * 
     * @return List of unsynced users
     */
    @Query("SELECT * FROM users WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    suspend fun getUnsyncedUsers(): List<UserEntity>
    
    /**
     * Get users by specific sync status.
     * Useful for monitoring sync progress.
     */
    @Query("SELECT * FROM users WHERE syncStatus = :status")
    suspend fun getUsersBySyncStatus(status: SyncStatus): List<UserEntity>
    
    /**
     * Update the sync status of a user.
     * Called after successful/failed sync attempts.
     */
    @Query("UPDATE users SET syncStatus = :status, lastSyncedAt = :syncedAt WHERE userId = :userId")
    suspend fun updateSyncStatus(userId: String, status: SyncStatus, syncedAt: Long?)
    
    /**
     * Delete a user from local database.
     */
    @Delete
    suspend fun deleteUser(user: UserEntity)
    
    /**
     * Delete a user by their userId.
     * Useful when migrating offline users to Firebase.
     */
    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUser(userId: String)
    
    /**
     * Get all users (for debugging or admin purposes).
     */
    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>
    
    /**
     * Check if a user with given email exists.
     * Useful for preventing duplicate signups.
     */
    @Query("SELECT COUNT(*) FROM users WHERE email = :email")
    suspend fun isEmailExists(email: String): Int
}
