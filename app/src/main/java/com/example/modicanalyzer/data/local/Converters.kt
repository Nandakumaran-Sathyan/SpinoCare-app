package com.example.modicanalyzer.data.local

import androidx.room.TypeConverter
import com.example.modicanalyzer.data.model.SyncStatus

/**
 * Type converters for Room database.
 * Converts custom types to/from database-compatible types.
 */
class Converters {
    
    /**
     * Convert SyncStatus enum to String for database storage.
     */
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String {
        return status.name
    }
    
    /**
     * Convert String back to SyncStatus enum when reading from database.
     */
    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus {
        return try {
            SyncStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            SyncStatus.PENDING // Default fallback
        }
    }
}
