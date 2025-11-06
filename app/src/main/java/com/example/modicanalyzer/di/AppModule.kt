package com.example.modicanalyzer.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.modicanalyzer.data.local.AppDatabase
import com.example.modicanalyzer.data.local.dao.LocalDataDao
import com.example.modicanalyzer.data.local.dao.UserDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing application-level dependencies.
 * 
 * Provides instances for:
 * - Room Database and DAOs
 * - Firebase Auth and Firestore
 * - WorkManager
 * - Context-dependent utilities
 * 
 * All provided dependencies are Singletons for the application lifecycle.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    /**
     * Provide Room Database instance.
     * 
     * Features:
     * - Singleton pattern (only one instance for the entire app)
     * - Migration support for schema changes
     * - Type converters for custom types
     * 
     * Includes migrations:
     * - v1 to v2: adds encryptedPassword field
     * - v2 to v3: adds pending_signups table
     * - v3 to v4: adds pending_image_uploads table
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4
            )
            .fallbackToDestructiveMigration() // For development - use migrations in production
            .build()
    }
    
    /**
     * Provide UserDao from Room Database.
     */
    @Provides
    @Singleton
    fun provideUserDao(database: AppDatabase): UserDao {
        return database.userDao()
    }
    
    /**
     * Provide LocalDataDao from Room Database.
     */
    @Provides
    @Singleton
    fun provideLocalDataDao(database: AppDatabase): LocalDataDao {
        return database.localDataDao()
    }
    
    /**
     * Provide PendingSignupDao from Room Database.
     */
    @Provides
    @Singleton
    fun providePendingSignupDao(database: AppDatabase): com.example.modicanalyzer.data.local.dao.PendingSignupDao {
        return database.pendingSignupDao()
    }
    
    /**
     * Provide PendingImageUploadDao from Room Database.
     */
    @Provides
    @Singleton
    fun providePendingImageUploadDao(database: AppDatabase): com.example.modicanalyzer.data.local.dao.PendingImageUploadDao {
        return database.pendingImageUploadDao()
    }
    
    /**
     * Provide Firebase Authentication instance.
     */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }
    
    /**
     * Provide Firebase Firestore instance.
     * 
     * Uses default database (FREE on Spark Plan).
     * Custom databases require Blaze Plan with billing enabled.
     */
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }
    
    /**
     * Provide Firebase Storage instance.
     * 
     * Used for storing user-uploaded images (MRI scans, etc.)
     */
    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        return FirebaseStorage.getInstance()
    }
    
    /**
     * Provide WorkManager instance for background tasks.
     */
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager {
        return WorkManager.getInstance(context)
    }
    
    /**
     * Provide Application Context.
     * Used by other dependencies that need context.
     */
    @Provides
    @Singleton
    fun provideContext(
        @ApplicationContext context: Context
    ): Context {
        return context
    }
}
