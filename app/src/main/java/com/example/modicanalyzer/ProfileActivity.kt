package com.example.modicanalyzer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            com.example.modicanalyzer.ui.theme.ModicAnalyzerTheme(darkTheme = false, dynamicColor = false) {
                ProfileActivityScreen {
                    finish() // Close profile when done
                }
            }
        }
    }
}

@Composable
fun ProfileActivityScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    
    ProfileScreen(
        onHelpSupportClick = {
            // Navigate to help & support
            val intent = Intent(context, HelpSupportActivity::class.java)
            context.startActivity(intent)
        },
        onPrivacyPolicyClick = {
            // Navigate to privacy policy
            val intent = Intent(context, PrivacyPolicyActivity::class.java)
            context.startActivity(intent)
        },
        onSignOutClick = {
            // Handle sign out
            val authManager = AuthManager(context)
            authManager.logout()
            Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
            
            // Navigate back to login
            val intent = Intent(context, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            context.startActivity(intent)
        },
        onNavigateBack = onNavigateBack
    )
}