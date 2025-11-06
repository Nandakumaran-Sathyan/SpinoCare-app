package com.example.modicanalyzer.data.model

/**
 * Sealed class representing the synchronization state of the app.
 * Used to display sync status to users in the UI.
 */
sealed class SyncState {
    /**
     * App is in offline mode (no network connectivity)
     */
    object Offline : SyncState()
    
    /**
     * App is online but not currently syncing
     */
    object Online : SyncState()
    
    /**
     * Synchronization is in progress
     * @param progress Optional progress percentage (0-100)
     */
    data class Syncing(val progress: Int? = null) : SyncState()
    
    /**
     * All data is successfully synced
     */
    object Synced : SyncState()
    
    /**
     * Sync failed with an error
     * @param message Error message
     */
    data class Error(val message: String) : SyncState()
}
