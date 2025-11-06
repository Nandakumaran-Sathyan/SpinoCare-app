package com.example.modicanalyzer

import android.app.Application
import android.util.Log

/**
 * Application class for ModicAnalyzer
 * Handles app-wide initialization
 */
class ModicApplication : Application() {
    
    companion object {
        private const val TAG = "ModicApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "ModicAnalyzer Application starting")
        
        // Initialize background services would go here if needed
        // For now, auto-updates are initialized per activity
    }
}