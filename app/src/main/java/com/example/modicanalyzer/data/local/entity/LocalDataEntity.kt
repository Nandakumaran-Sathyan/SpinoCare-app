package com.example.modicanalyzer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.modicanalyzer.data.model.SyncStatus

/**
 * Room entity representing user-generated data that needs to be synced to Firestore.
 * 
 * This could represent medical scan results, analysis data, or any other user-specific data.
 * Data is stored locally first for offline capability, then synced to Firestore when online.
 * 
 * @property id Unique identifier for this data entry
 * @property userId Foreign key linking to the user who owns this data
 * @property dataType Type of data (e.g., "scan_result", "analysis", "image")
 * @property dataContent JSON string containing the actual data
 * @property metadata Additional metadata as JSON string
 * @property syncStatus Current synchronization status
 * @property createdAt Timestamp when the data was created
 * @property modifiedAt Timestamp when the data was last modified
 * @property syncedAt Timestamp when the data was last synced to Firestore
 */
@Entity(
    tableName = "local_data",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["syncStatus"])]
)
data class LocalDataEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val dataType: String,
    val dataContent: String, // JSON string
    val metadata: String? = null, // JSON string for additional info
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null
)
