package com.example.modicanalyzer.data.local.dao

import androidx.room.*
import com.example.modicanalyzer.data.local.entity.LocalDataEntity
import com.example.modicanalyzer.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for LocalData operations in Room database.
 * 
 * Manages user-generated data that needs to be synced to Firestore:
 * - Medical scan results
 * - Analysis data
 * - User preferences
 * - Any other user-specific data
 */
@Dao
interface LocalDataDao {
    
    /**
     * Insert new data entry or replace if already exists.
     * Used for creating new data entries and updating synced data from Firestore.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertData(data: LocalDataEntity)
    
    /**
     * Insert multiple data entries at once.
     * Useful for batch operations and initial sync from Firestore.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(dataList: List<LocalDataEntity>)
    
    /**
     * Update an existing data entry.
     */
    @Update
    suspend fun updateData(data: LocalDataEntity)
    
    /**
     * Delete a data entry.
     */
    @Delete
    suspend fun deleteData(data: LocalDataEntity)
    
    /**
     * Get a specific data entry by ID.
     */
    @Query("SELECT * FROM local_data WHERE id = :dataId LIMIT 1")
    suspend fun getDataById(dataId: String): LocalDataEntity?
    
    /**
     * Get all data entries for a specific user.
     * Used to display user's data in the dashboard.
     */
    @Query("SELECT * FROM local_data WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getDataByUserId(userId: String): List<LocalDataEntity>
    
    /**
     * Observe all data for a user with Flow for reactive UI updates.
     */
    @Query("SELECT * FROM local_data WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeDataByUserId(userId: String): Flow<List<LocalDataEntity>>
    
    /**
     * Get all unsynced data entries (for background sync).
     * This includes both PENDING and FAILED status.
     */
    @Query("SELECT * FROM local_data WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    suspend fun getUnsyncedData(): List<LocalDataEntity>
    
    /**
     * Get unsynced data for a specific user.
     */
    @Query("SELECT * FROM local_data WHERE userId = :userId AND (syncStatus = 'PENDING' OR syncStatus = 'FAILED')")
    suspend fun getUnsyncedDataByUserId(userId: String): List<LocalDataEntity>
    
    /**
     * Observe unsynced data count for displaying sync status.
     */
    @Query("SELECT COUNT(*) FROM local_data WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    fun observeUnsyncedDataCount(): Flow<Int>
    
    /**
     * Update sync status of a data entry.
     * Called after successful/failed sync attempts.
     */
    @Query("UPDATE local_data SET syncStatus = :status, syncedAt = :syncedAt WHERE id = :dataId")
    suspend fun updateSyncStatus(dataId: String, status: SyncStatus, syncedAt: Long?)
    
    /**
     * Update sync status for multiple entries at once.
     * Useful for batch sync operations.
     */
    @Query("UPDATE local_data SET syncStatus = :status, syncedAt = :syncedAt WHERE id IN (:dataIds)")
    suspend fun updateSyncStatusBatch(dataIds: List<String>, status: SyncStatus, syncedAt: Long?)
    
    /**
     * Get data by type for a user.
     * E.g., get all "scan_result" entries.
     */
    @Query("SELECT * FROM local_data WHERE userId = :userId AND dataType = :dataType ORDER BY createdAt DESC")
    suspend fun getDataByType(userId: String, dataType: String): List<LocalDataEntity>
    
    /**
     * Delete all data for a user (useful for logout or account deletion).
     */
    @Query("DELETE FROM local_data WHERE userId = :userId")
    suspend fun deleteAllDataForUser(userId: String)
    
    /**
     * Get synced data count for a user.
     */
    @Query("SELECT COUNT(*) FROM local_data WHERE userId = :userId AND syncStatus = 'SYNCED'")
    suspend fun getSyncedDataCount(userId: String): Int
    
    /**
     * Get all data (for debugging purposes).
     */
    @Query("SELECT * FROM local_data ORDER BY createdAt DESC")
    suspend fun getAllData(): List<LocalDataEntity>
}
