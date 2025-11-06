package com.example.modicanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            com.example.modicanalyzer.ui.theme.ModicAnalyzerTheme(darkTheme = false, dynamicColor = false) {
                PrivacyPolicyScreen {
                    finish()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.example.modicanalyzer.ui.theme.ModicarePrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Privacy Policy",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant
                )
            }
            
            item {
                Text(
                    "Last updated: ${java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())}",
                    fontSize = 14.sp,
                    color = com.example.modicanalyzer.ui.theme.TextSecondary
                )
            }
            
            item {
                PolicySection(
                    title = "Information We Collect",
                    content = "We collect information you provide when using our medical image analysis services, including:\n\n• Medical images you upload for analysis\n• Account information (name, email)\n• Usage data and app preferences\n• Device information for optimal performance"
                )
            }
            
            item {
                PolicySection(
                    title = "How We Use Your Information",
                    content = "Your information is used to:\n\n• Provide accurate medical image analysis\n• Improve our AI models and services\n• Send important updates about your account\n• Ensure app security and functionality\n\nWe never share your medical data with third parties without explicit consent."
                )
            }
            
            item {
                PolicySection(
                    title = "Data Security",
                    content = "We implement industry-standard security measures:\n\n• End-to-end encryption for all medical data\n• HIPAA-compliant data storage\n• Regular security audits and updates\n• Secure data centers with 24/7 monitoring\n\nYour medical privacy is our top priority."
                )
            }
            
            item {
                PolicySection(
                    title = "Data Retention",
                    content = "We retain your data only as long as necessary:\n\n• Analysis results: 2 years for medical reference\n• Account information: Until account deletion\n• Usage data: 1 year for service improvement\n\nYou can request data deletion at any time through your account settings."
                )
            }
            
            item {
                PolicySection(
                    title = "Your Rights",
                    content = "You have the right to:\n\n• Access your personal data\n• Correct inaccurate information\n• Delete your account and data\n• Port your data to another service\n• Opt out of non-essential communications\n\nContact us at privacy@modicanalyzer.com to exercise these rights."
                )
            }
            
            item {
                PolicySection(
                    title = "Contact Information",
                    content = "For privacy-related questions or concerns:\n\nEmail: privacy@modicanalyzer.com\nPhone: +1 (555) 123-4567\nAddress: 123 Medical AI Drive, Tech City, TC 12345\n\nWe typically respond within 48 hours."
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = com.example.modicanalyzer.ui.theme.ModicareBackground)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Changes to This Policy",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "We may update this privacy policy from time to time. We will notify you of any changes by posting the new policy in the app and updating the 'Last updated' date.",
                            fontSize = 14.sp,
                            color = com.example.modicanalyzer.ui.theme.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PolicySection(title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                content,
                fontSize = 14.sp,
                color = com.example.modicanalyzer.ui.theme.TextSecondary,
                lineHeight = 20.sp
            )
        }
    }
}