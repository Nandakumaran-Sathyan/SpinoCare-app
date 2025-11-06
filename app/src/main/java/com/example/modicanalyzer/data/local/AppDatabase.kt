package com.example.modicanalyzer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.modicanalyzer.data.local.dao.LocalDataDao
import com.example.modicanalyzer.data.local.dao.UserDao
import com.example.modicanalyzer.data.local.dao.PendingSignupDao
import com.example.modicanalyzer.data.local.dao.PendingImageUploadDao
import com.example.modicanalyzer.data.local.entity.LocalDataEntity
import com.example.modicanalyzer.data.local.entity.UserEntity
import com.example.modicanalyzer.data.local.entity.PendingSignupEntity
import com.example.modicanalyzer.data.local.entity.PendingImageUploadEntity

/**
 * Main Room Database for the application.
 * 
 * Manages four tables:
 * 1. users - Stores user authentication data with offline-first support
 * 2. local_data - Stores user-generated data that syncs to Firestore
 * 3. pending_signups - Queue for offline signup requests
 * 4. pending_image_uploads - Queue for offline image uploads
 * 
 * Version 1: Initial database schema
 * Version 2: Added encryptedPassword field to UserEntity for offline user sync
 * Version 3: Added pending_signups table for offline signup queue
 * Version 4: Added pending_image_uploads table for offline image upload queue
 * 
 * Features:
 * - Type converters for custom types (SyncStatus enum)
 * - Migration support for future schema changes
 * - Singleton pattern via Hilt dependency injection
 */
@Database(
    entities = [UserEntity::class, LocalDataEntity::class, PendingSignupEntity::class, PendingImageUploadEntity::class],
    version = 4,
    exportSchema = false  // Set to true and provide schema location in production
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    /**
     * Provides access to User table operations.
     */
    abstract fun userDao(): UserDao
    
    /**
     * Provides access to LocalData table operations.
     */
    abstract fun localDataDao(): LocalDataDao
    
    /**
     * Provides access to PendingSignup table operations.
     */
    abstract fun pendingSignupDao(): PendingSignupDao
    
    /**
     * Provides access to PendingImageUpload table operations.
     */
    abstract fun pendingImageUploadDao(): PendingImageUploadDao
    
    companion object {
        const val DATABASE_NAME = "modicare_offline_db"
        
        /**
         * Migration from version 1 to 2: Add encryptedPassword column.
         * This field stores encrypted password for offline users until they sync to Firebase.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add encryptedPassword column to users table
                database.execSQL("ALTER TABLE users ADD COLUMN encryptedPassword TEXT DEFAULT NULL")
            }
        }
        
        /**
         * Migration from version 2 to 3: Add pending_signups table.
         * This table queues signup requests when offline.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_signups (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        fullName TEXT NOT NULL,
                        email TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        passwordHash TEXT NOT NULL,
                        role TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        errorMessage TEXT,
                        retryCount INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }
        
        /**
         * Migration from version 3 to 4: Add pending_image_uploads table.
         * This table queues image uploads when offline or when upload fails.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_image_uploads (
                        id TEXT PRIMARY KEY NOT NULL,
                        userId TEXT NOT NULL,
                        analysisId TEXT NOT NULL,
                        t1ImagePath TEXT NOT NULL,
                        t2ImagePath TEXT NOT NULL,
                        t1UploadUrl TEXT,
                        t2UploadUrl TEXT,
                        syncStatus TEXT NOT NULL DEFAULT 'PENDING',
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        uploadedAt INTEGER,
                        errorMessage TEXT
                    )
                """.trimIndent())
            }
        }
    }
}
