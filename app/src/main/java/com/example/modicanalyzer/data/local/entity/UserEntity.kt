package com.example.modicanalyzer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.modicanalyzer.data.model.SyncStatus

/**
 * Room entity representing a user in the local SQLite database.
 * 
 * This entity handles offline-first authentication:
 * - When offline, users are created locally with synced = false
 * - When online, they are synced to Firebase Auth and marked as synced = true
 * 
 * @property userId Unique identifier (Firebase UID when synced, local UUID when offline)
 * @property email User's email address
 * @property passwordHash Hashed password for offline authentication
 * @property encryptedPassword Encrypted password for offline users (needed for Firebase sync, deleted after sync)
 * @property displayName User's display name (optional)
 * @property isFirebaseAuth True if this user is authenticated via Firebase
 * @property syncStatus Current synchronization status with Firebase
 * @property createdAt Timestamp when the user was created locally
 * @property lastSyncedAt Timestamp of last successful sync with Firebase
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val userId: String,
    val email: String,
    val passwordHash: String,
    val encryptedPassword: String? = null, // Only for offline users, deleted after sync
    val displayName: String? = null,
    val isFirebaseAuth: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long? = null
)
