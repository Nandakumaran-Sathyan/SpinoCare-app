package com.example.modicanalyzer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.example.modicanalyzer.util.NetworkConnectivityObserver
import com.example.modicanalyzer.worker.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Main Application class for the Modicare Offline-First app.
 * 
 * Responsibilities:
 * - Initialize Hilt dependency injection
 * - Configure WorkManager for background sync
 * - Set up periodic and one-time sync jobs
 * - Check network connectivity on startup
 * - Auto-sync pending data when app starts with internet
 * 
 * Features:
 * - Automatic sync on app startup (if online)
 * - Periodic background sync every 15 minutes
 * - Network-aware sync scheduling
 */
@HiltAndroidApp
class ModicareApplication : Application(), Configuration.Provider {
    
    /**
     * Hilt-provided WorkerFactory for creating Workers with dependency injection.
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    /**
     * Network connectivity observer to check internet status.
     */
    @Inject
    lateinit var networkObserver: NetworkConnectivityObserver
    
    /**
     * WorkManager instance for scheduling sync jobs.
     */
    @Inject
    lateinit var workManager: WorkManager
    
    /**
     * Application-level coroutine scope.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        
        android.util.Log.d("ModicareApp", "Application initialized with Hilt and WorkManager")
        
        // Schedule periodic sync
        schedulePeriodicSync()
        
        // Check for pending data and sync if online
        checkAndSyncOnStartup()
    }
    
    /**
     * Schedule periodic background sync.
     * Runs every 15 minutes when device has network connectivity.
     */
    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag("periodic_sync")
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "periodic_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSyncRequest
        )
        
        android.util.Log.d("ModicareApp", "Periodic sync scheduled (every 15 minutes)")
    }
    
    /**
     * Check network connectivity on app startup.
     * If online and pending data exists, trigger immediate sync.
     */
    private fun checkAndSyncOnStartup() {
        applicationScope.launch {
            try {
                // Check if device is currently connected
                val isOnline = networkObserver.observe().first()
                
                if (isOnline) {
                    android.util.Log.d("ModicareApp", "Device online - triggering startup sync")
                    triggerImmediateSync()
                } else {
                    android.util.Log.d("ModicareApp", "Device offline - sync will trigger when connected")
                }
            } catch (e: Exception) {
                android.util.Log.e("ModicareApp", "Error checking network on startup: ${e.message}")
            }
        }
    }
    
    /**
     * Trigger immediate one-time sync.
     * Used on app startup when network is available.
     */
    private fun triggerImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    SyncWorker.KEY_SYNC_TYPE to SyncWorker.SYNC_TYPE_FULL
                )
            )
            .addTag("startup_sync")
            .build()
        
        workManager.enqueueUniqueWork(
            "startup_sync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
        
        android.util.Log.d("ModicareApp", "Immediate sync triggered")
    }
    
    /**
     * Provide WorkManager configuration with custom WorkerFactory.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
