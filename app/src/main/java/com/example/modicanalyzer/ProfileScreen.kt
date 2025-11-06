package com.example.modicanalyzer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.modicanalyzer.viewmodel.UserProfileViewModel

@Composable
fun ProfileScreen(
    onHelpSupportClick: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {},
    onModelSettingsClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    userProfileViewModel: UserProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val authManager = AuthManager(context)
    
    // Get user information from Firestore via UserProfileViewModel
    val userName = userProfileViewModel.getUserName().takeIf { it.isNotBlank() } ?: "Guest User"
    val userRole = userProfileViewModel.getUserRole().takeIf { it.isNotBlank() } ?: "Patient"
    val userEmail = userProfileViewModel.getUserEmail().takeIf { it.isNotBlank() } ?: "No email available"
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        com.example.modicanalyzer.ui.theme.ModicareBackground,
                        Color.White
                    )
                )
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Profile Header: avatar on left, details fill right
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: avatar column that occupies a fixed width
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        com.example.modicanalyzer.ui.theme.ModicarePrimary.copy(alpha = 0.2f),
                                        com.example.modicanalyzer.ui.theme.ModicareSecondary.copy(alpha = 0.1f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Right: name and details, take remaining space
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            userName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            userRole,
                            fontSize = 16.sp,
                            color = com.example.modicanalyzer.ui.theme.ModicareAccent
                        )

                        Text(
                            userEmail,
                            fontSize = 14.sp,
                            color = com.example.modicanalyzer.ui.theme.TextSecondary
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Account Status
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (authManager.isFirebaseAuthenticated()) Icons.Default.CheckCircle 
                                else if (authManager.isLoggedIn()) Icons.Default.AccountBox
                                else Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = if (authManager.isFirebaseAuthenticated()) Color(0xFF4CAF50) 
                                       else if (authManager.isLoggedIn()) Color(0xFF2196F3)
                                       else com.example.modicanalyzer.ui.theme.ModicareAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (authManager.isFirebaseAuthenticated()) "Firebase Account" 
                                else if (authManager.isLoggedIn()) "Local Account" 
                                else "Demo Account",
                                fontSize = 12.sp,
                                color = if (authManager.isFirebaseAuthenticated()) Color(0xFF4CAF50) 
                                       else if (authManager.isLoggedIn()) Color(0xFF2196F3)
                                       else com.example.modicanalyzer.ui.theme.ModicareAccent
                            )
                        }
                    }
                }
            }
        }
        
        // Usage statistics removed
        
        item {
            // Profile Options
            Text(
                "Profile Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        items(getProfileOptions().size) { index ->
            val option = getProfileOptions()[index]
            ProfileOptionCard(
                option = option,
                onClick = {
                    when (option.title) {
                        "Help & Support" -> onHelpSupportClick()
                        "Privacy Policy" -> onPrivacyPolicyClick()
                        "Model Settings" -> onModelSettingsClick()
                        "Sign Out" -> onSignOutClick()
                    }
                }
            )
        }
    }
}

@Composable
fun StatisticItem(
    value: String,
    label: String,
    icon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = com.example.modicanalyzer.ui.theme.ModicarePrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant
        )
        Text(
            label,
            fontSize = 12.sp,
            color = com.example.modicanalyzer.ui.theme.ModicareAccent,
            textAlign = TextAlign.Center
        )
    }
}

// StatisticItem function kept but not used (usage statistics removed)

@Composable
fun ProfileOptionCard(
    option: ProfileOption,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(option.color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    option.icon,
                    contentDescription = null,
                    tint = option.color,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    option.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = com.example.modicanalyzer.ui.theme.TextPrimary
                )
                Text(
                    option.subtitle,
                    fontSize = 12.sp,
                    color = com.example.modicanalyzer.ui.theme.TextSecondary
                )
            }
            
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = null,
                tint = com.example.modicanalyzer.ui.theme.ModicareAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

data class ProfileOption(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color
)

// Updated to only include essential options
fun getProfileOptions() = listOf(
    ProfileOption(
        "Help & Support",
        "Get help and frequently asked questions",
        Icons.Filled.Help,
        Color(0xFFF59E0B)
    ),
    ProfileOption(
        "Privacy Policy",
        "Review our terms and privacy policy",
        Icons.Default.Lock,
        Color(0xFF64748B)
    ),
    ProfileOption(
        "Model Settings",
        "Manage AI model and analysis settings",
        Icons.Default.Storage,
        Color(0xFF8B5CF6)
    ),
    ProfileOption(
        "Sign Out",
        "Sign out of your account",
        Icons.Filled.Logout,
        Color(0xFFDC2626)
    )
)
