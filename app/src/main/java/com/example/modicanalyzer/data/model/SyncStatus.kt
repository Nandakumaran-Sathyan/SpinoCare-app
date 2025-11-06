package com.example.modicanalyzer.data.model

/**
 * Represents the synchronization status of data between local Room database and remote Firestore.
 * 
 * PENDING: Data exists locally but hasn't been synced to Firestore yet
 * SYNCING: Currently in the process of syncing to Firestore
 * SYNCED: Successfully synced to Firestore
 * FAILED: Sync attempt failed (will retry on next network availability)
 */
enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED
}
