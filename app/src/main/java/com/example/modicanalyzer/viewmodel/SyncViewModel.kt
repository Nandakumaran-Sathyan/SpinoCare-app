package com.example.modicanalyzer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.example.modicanalyzer.data.local.entity.LocalDataEntity
import com.example.modicanalyzer.data.model.SyncState
import com.example.modicanalyzer.data.repository.DataRepository
import com.example.modicanalyzer.data.repository.SyncStats
import com.example.modicanalyzer.util.NetworkConnectivityObserver
import com.example.modicanalyzer.worker.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Data Synchronization operations.
 * 
 * Manages:
 * - User data CRUD operations
 * - Sync status monitoring
 * - Manual sync triggers
 * - Network connectivity awareness
 * - WorkManager sync job monitoring
 * 
 * Features:
 * - Reactive data observation with Flow
 * - Offline-first data operations
 * - Automatic background sync
 * - Sync statistics and progress
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val dataRepository: DataRepository,
    private val networkObserver: NetworkConnectivityObserver,
    private val workManager: WorkManager
) : ViewModel() {
    
    /**
     * Current sync state for UI display.
     */
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Offline)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    /**
     * Network connectivity status.
     */
    val isOnline: StateFlow<Boolean> = networkObserver.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = networkObserver.isCurrentlyConnected()
        )
    
    /**
     * Count of unsynced data entries.
     */
    val unsyncedCount: StateFlow<Int> = dataRepository.observeUnsyncedDataCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    
    /**
     * Current user's data (to be set by UI).
     */
    private val _currentUserId = MutableStateFlow<String?>(null)
    
    /**
     * User's data as observable Flow.
     */
    val userData: StateFlow<List<LocalDataEntity>> = _currentUserId
        .filterNotNull()
        .flatMapLatest { userId ->
            dataRepository.observeUserData(userId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    init {
        // Update sync state based on network connectivity
        observeNetworkAndSyncState()
    }
    
    /**
     * Set the current user ID to observe their data.
     * Call this after user logs in.
     * 
     * @param userId User's unique ID
     */
    fun setCurrentUser(userId: String) {
        _currentUserId.value = userId
    }
    
    /**
     * Create a new data entry.
     * Saves locally first, will be synced in background.
     * 
     * @param dataType Type of data
     * @param dataContent JSON content
     * @param metadata Optional metadata
     * @return Result with created data ID
     */
    suspend fun createData(
        dataType: String,
        dataContent: String,
        metadata: String? = null
    ): Result<String> {
        val userId = _currentUserId.value ?: return Result.failure(
            Exception("No user logged in")
        )
        
        return dataRepository.createData(
            userId = userId,
            dataType = dataType,
            dataContent = dataContent,
            metadata = metadata
        )
    }
    
    /**
     * Update an existing data entry.
     * 
     * @param dataId ID of data to update
     * @param dataContent New content
     * @param metadata New metadata
     * @return Result indicating success/failure
     */
    suspend fun updateData(
        dataId: String,
        dataContent: String,
        metadata: String? = null
    ): Result<Unit> {
        return dataRepository.updateData(dataId, dataContent, metadata)
    }
    
    /**
     * Delete a data entry.
     * 
     * @param dataId ID of data to delete
     * @return Result indicating success/failure
     */
    suspend fun deleteData(dataId: String): Result<Unit> {
        val userId = _currentUserId.value ?: return Result.failure(
            Exception("No user logged in")
        )
        
        return dataRepository.deleteData(
            dataId = dataId,
            userId = userId,
            isOnline = isOnline.value
        )
    }
    
    /**
     * Get data by specific type.
     * 
     * @param dataType Type of data to filter by
     * @return List of matching data entries
     */
    suspend fun getDataByType(dataType: String): List<LocalDataEntity> {
        val userId = _currentUserId.value ?: return emptyList()
        return dataRepository.getDataByType(userId, dataType)
    }
    
    /**
     * Manually trigger sync now.
     * Called when user presses "Sync Now" button.
     */
    fun syncNow() {
        if (!isOnline.value) {
            _syncState.value = SyncState.Error("Device is offline")
            return
        }
        
        val userId = _currentUserId.value
        if (userId == null) {
            _syncState.value = SyncState.Error("No user logged in")
            return
        }
        
        _syncState.value = SyncState.Syncing()
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    SyncWorker.KEY_USER_ID to userId,
                    SyncWorker.KEY_SYNC_TYPE to SyncWorker.SYNC_TYPE_DATA_ONLY
                )
            )
            .addTag("manual_sync")
            .build()
        
        workManager.enqueueUniqueWork(
            "manual_sync_${System.currentTimeMillis()}",
            ExistingWorkPolicy.KEEP,
            syncRequest
        )
        
        // Observe work status
        observeWorkStatus(syncRequest.id)
    }
    
    /**
     * Fetch data from Firestore and merge with local.
     * Useful for initial sync or restoring data.
     */
    fun fetchFromFirestore() {
        viewModelScope.launch {
            if (!isOnline.value) {
                _syncState.value = SyncState.Error("Device is offline")
                return@launch
            }
            
            val userId = _currentUserId.value
            if (userId == null) {
                _syncState.value = SyncState.Error("No user logged in")
                return@launch
            }
            
            _syncState.value = SyncState.Syncing()
            
            val result = dataRepository.fetchAndMergeFromFirestore(userId)
            
            _syncState.value = if (result.isSuccess) {
                SyncState.Synced
            } else {
                SyncState.Error(result.exceptionOrNull()?.message ?: "Fetch failed")
            }
        }
    }
    
    /**
     * Get sync statistics for current user.
     * 
     * @return SyncStats object with counts
     */
    suspend fun getSyncStats(): SyncStats? {
        val userId = _currentUserId.value ?: return null
        return dataRepository.getSyncStats(userId)
    }
    
    /**
     * Observe network connectivity and update sync state accordingly.
     */
    private fun observeNetworkAndSyncState() {
        viewModelScope.launch {
            isOnline.collect { online ->
                _syncState.value = if (online) {
                    if (unsyncedCount.value > 0) {
                        SyncState.Online
                    } else {
                        SyncState.Synced
                    }
                } else {
                    SyncState.Offline
                }
            }
        }
        
        // Also observe unsynced count
        viewModelScope.launch {
            unsyncedCount.collect { count ->
                if (isOnline.value && count == 0 && _syncState.value !is SyncState.Syncing) {
                    _syncState.value = SyncState.Synced
                }
            }
        }
    }
    
    /**
     * Observe WorkManager job status.
     * 
     * @param workId ID of the work request to observe
     */
    private fun observeWorkStatus(workId: java.util.UUID) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        _syncState.value = SyncState.Syncing()
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        _syncState.value = SyncState.Synced
                    }
                    WorkInfo.State.FAILED -> {
                        _syncState.value = SyncState.Error("Sync failed")
                    }
                    WorkInfo.State.CANCELLED -> {
                        _syncState.value = SyncState.Error("Sync cancelled")
                    }
                    else -> {
                        // ENQUEUED or BLOCKED - keep current state
                    }
                }
            }
        }
    }
    
    /**
     * Setup periodic sync (optional - call from UI if needed).
     * 
     * @param intervalHours How often to sync in hours
     */
    fun setupPeriodicSync(intervalHours: Long = 6) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalHours,
            java.util.concurrent.TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    SyncWorker.KEY_SYNC_TYPE to SyncWorker.SYNC_TYPE_FULL
                )
            )
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "periodic_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSyncRequest
        )
    }
}
