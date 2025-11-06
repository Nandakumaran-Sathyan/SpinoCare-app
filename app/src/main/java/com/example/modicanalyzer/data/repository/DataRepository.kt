package com.example.modicanalyzer.data.repository

import com.example.modicanalyzer.data.local.dao.LocalDataDao
import com.example.modicanalyzer.data.local.entity.LocalDataEntity
import com.example.modicanalyzer.data.model.SyncStatus
import com.example.modicanalyzer.data.remote.BatchDataEntry
import com.example.modicanalyzer.data.remote.FirestoreDataSource
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for user data operations with offline-first sync.
 * 
 * Manages user-generated data (medical scans, analysis results, etc.):
 * - Always save data locally first (offline-first approach)
 * - Sync to Firestore when online
 * - Handle conflicts and merge strategies
 * - Background sync via WorkManager
 * 
 * Data Flow:
 * 1. User creates data → Save to Room with PENDING status
 * 2. When online → SyncWorker uploads to Firestore → Mark as SYNCED
 * 3. On app start → Fetch latest from Firestore → Merge with local data
 */
@Singleton
class DataRepository @Inject constructor(
    private val localDataDao: LocalDataDao,
    private val firestoreDataSource: FirestoreDataSource
) {
    
    /**
     * Create a new data entry.
     * Always saves locally first, will be synced in background.
     * 
     * @param userId User's unique ID
     * @param dataType Type of data (e.g., "scan_result", "analysis")
     * @param dataContent JSON string of the actual content
     * @param metadata Optional metadata
     * @return Result with the created data ID
     */
    suspend fun createData(
        userId: String,
        dataType: String,
        dataContent: String,
        metadata: String? = null
    ): Result<String> {
        return try {
            val dataId = UUID.randomUUID().toString()
            val dataEntity = LocalDataEntity(
                id = dataId,
                userId = userId,
                dataType = dataType,
                dataContent = dataContent,
                metadata = metadata,
                syncStatus = SyncStatus.PENDING
            )
            
            localDataDao.insertData(dataEntity)
            Result.success(dataId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update an existing data entry.
     * Marks it as PENDING for re-sync.
     * 
     * @param dataId ID of the data to update
     * @param dataContent New content
     * @param metadata New metadata
     * @return Result indicating success or failure
     */
    suspend fun updateData(
        dataId: String,
        dataContent: String,
        metadata: String? = null
    ): Result<Unit> {
        return try {
            val existingData = localDataDao.getDataById(dataId)
            if (existingData != null) {
                val updatedData = existingData.copy(
                    dataContent = dataContent,
                    metadata = metadata,
                    modifiedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.PENDING
                )
                localDataDao.updateData(updatedData)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Data not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a data entry.
     * Removes from local database and Firestore.
     * 
     * @param dataId ID of the data to delete
     * @param userId User's ID (for Firestore deletion)
     * @param isOnline Whether device is online
     * @return Result indicating success or failure
     */
    suspend fun deleteData(
        dataId: String,
        userId: String,
        isOnline: Boolean
    ): Result<Unit> {
        return try {
            val data = localDataDao.getDataById(dataId)
            if (data != null) {
                // Delete from local database
                localDataDao.deleteData(data)
                
                // If online, also delete from Firestore
                if (isOnline) {
                    firestoreDataSource.deleteDataEntry(userId, dataId)
                }
                
                Result.success(Unit)
            } else {
                Result.failure(Exception("Data not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all data for a specific user.
     * Returns data from local database.
     */
    suspend fun getUserData(userId: String): List<LocalDataEntity> {
        return localDataDao.getDataByUserId(userId)
    }
    
    /**
     * Observe user data changes with Flow for reactive UI.
     */
    fun observeUserData(userId: String): Flow<List<LocalDataEntity>> {
        return localDataDao.observeDataByUserId(userId)
    }
    
    /**
     * Get data by specific type for a user.
     */
    suspend fun getDataByType(userId: String, dataType: String): List<LocalDataEntity> {
        return localDataDao.getDataByType(userId, dataType)
    }
    
    /**
     * Observe count of unsynced data entries.
     * Useful for showing sync status in UI.
     */
    fun observeUnsyncedDataCount(): Flow<Int> {
        return localDataDao.observeUnsyncedDataCount()
    }
    
    /**
     * Sync unsynced data to Firestore.
     * Called by SyncWorker when network is available.
     * 
     * @param userId User's ID
     * @return Number of successfully synced entries
     */
    suspend fun syncPendingData(userId: String): Int {
        val unsyncedData = localDataDao.getUnsyncedDataByUserId(userId)
        var syncedCount = 0
        
        if (unsyncedData.isEmpty()) {
            return 0
        }
        
        // Try batch sync first (more efficient)
        try {
            val batchEntries = unsyncedData.map { data ->
                BatchDataEntry(
                    id = data.id,
                    dataType = data.dataType,
                    dataContent = data.dataContent,
                    metadata = data.metadata,
                    createdAt = data.createdAt
                )
            }
            
            val result = firestoreDataSource.batchSyncData(userId, batchEntries)
            
            if (result.isSuccess) {
                // Mark all as synced
                val dataIds = unsyncedData.map { it.id }
                localDataDao.updateSyncStatusBatch(
                    dataIds,
                    SyncStatus.SYNCED,
                    System.currentTimeMillis()
                )
                syncedCount = result.getOrDefault(0)
            }
        } catch (e: Exception) {
            // Batch failed, try individual sync
            unsyncedData.forEach { data ->
                try {
                    localDataDao.updateSyncStatus(data.id, SyncStatus.SYNCING, null)
                    
                    val result = firestoreDataSource.syncDataEntry(
                        userId = userId,
                        dataId = data.id,
                        dataType = data.dataType,
                        dataContent = data.dataContent,
                        metadata = data.metadata,
                        createdAt = data.createdAt
                    )
                    
                    if (result.isSuccess) {
                        localDataDao.updateSyncStatus(
                            data.id,
                            SyncStatus.SYNCED,
                            System.currentTimeMillis()
                        )
                        syncedCount++
                    } else {
                        localDataDao.updateSyncStatus(data.id, SyncStatus.FAILED, null)
                    }
                } catch (e: Exception) {
                    localDataDao.updateSyncStatus(data.id, SyncStatus.FAILED, null)
                }
            }
        }
        
        return syncedCount
    }
    
    /**
     * Fetch data from Firestore and merge with local database.
     * Used for initial sync or restoring data on new device.
     * 
     * @param userId User's ID
     * @return Number of entries fetched and merged
     */
    suspend fun fetchAndMergeFromFirestore(userId: String): Result<Int> {
        return try {
            val result = firestoreDataSource.fetchUserData(userId)
            
            if (result.isSuccess) {
                val firestoreData = result.getOrDefault(emptyList())
                
                // Convert to LocalDataEntity and insert
                val localEntities = firestoreData.map { entry ->
                    LocalDataEntity(
                        id = entry.id,
                        userId = userId,
                        dataType = entry.dataType,
                        dataContent = entry.dataContent,
                        metadata = entry.metadata,
                        syncStatus = SyncStatus.SYNCED,
                        createdAt = entry.createdAt,
                        modifiedAt = entry.createdAt,
                        syncedAt = entry.syncedAt
                    )
                }
                
                localDataDao.insertAll(localEntities)
                Result.success(localEntities.size)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to fetch data"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get sync statistics for a user.
     */
    suspend fun getSyncStats(userId: String): SyncStats {
        val allData = localDataDao.getDataByUserId(userId)
        val unsyncedData = localDataDao.getUnsyncedDataByUserId(userId)
        val syncedCount = localDataDao.getSyncedDataCount(userId)
        
        return SyncStats(
            totalEntries = allData.size,
            syncedEntries = syncedCount,
            pendingEntries = unsyncedData.count { it.syncStatus == SyncStatus.PENDING },
            failedEntries = unsyncedData.count { it.syncStatus == SyncStatus.FAILED }
        )
    }
}

/**
 * Data class for sync statistics.
 */
data class SyncStats(
    val totalEntries: Int,
    val syncedEntries: Int,
    val pendingEntries: Int,
    val failedEntries: Int
)
