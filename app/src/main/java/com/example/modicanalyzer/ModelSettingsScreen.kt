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
import kotlinx.coroutines.launch

@Composable
fun ModelSettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Initialize analyzer
    val analyzer = remember { ModicAnalyzer(context) }
    
    // State variables
    var isOfflineMode by remember { mutableStateOf(analyzer.isOfflineModeEnabled()) }
    var isModelAvailable by remember { mutableStateOf(analyzer.isLocalModelAvailable()) }
    var isAutoUpdateEnabled by remember { mutableStateOf(analyzer.isAutoUpdateEnabled()) }
    var modelUpdateInfo by remember { mutableStateOf(analyzer.getModelUpdateInfo()) }
    var serverStatus by remember { mutableStateOf("Checking...") }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var isDownloadingModel by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    
    // Check server status on load
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val remoteAnalyzer = RemoteModelAnalyzer(context)
                val result = remoteAnalyzer.testConnection()
                serverStatus = if (result.isSuccess) {
                    "🟢 Server Online"
                } else {
                    "🔴 Server Offline"
                }
            } catch (e: Exception) {
                serverStatus = "🔴 Connection Failed"
            }
        }
    }
    
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
            // Header
            Text(
                "Model Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        item {
            // Server Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Server Connectivity",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        serverStatus,
                        fontSize = 16.sp,
                        color = com.example.modicanalyzer.ui.theme.TextPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            scope.launch {
                                serverStatus = "Checking..."
                                try {
                                    val remoteAnalyzer = RemoteModelAnalyzer(context)
                                    val result = remoteAnalyzer.testConnection()
                                    serverStatus = if (result.isSuccess) {
                                        "🟢 Server Online - ${System.currentTimeMillis()}"
                                    } else {
                                        "🔴 Server Offline"
                                    }
                                } catch (e: Exception) {
                                    serverStatus = "🔴 Connection Failed: ${e.message}"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.example.modicanalyzer.ui.theme.ModicarePrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Connection")
                    }
                }
            }
        }
        
        item {
            // Model Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = null,
                            tint = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Local Model Status",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isModelAvailable) Color(0xFF10B981) else Color(0xFFF59E0B)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isModelAvailable) "Model Available (Offline analysis ready)" 
                            else "Waiting for first update...",
                            fontSize = 14.sp,
                            color = com.example.modicanalyzer.ui.theme.TextPrimary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        modelUpdateInfo,
                        fontSize = 12.sp,
                        color = com.example.modicanalyzer.ui.theme.TextSecondary
                    )
                    
                    if (isDownloadingModel) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = downloadProgress / 100f,
                            modifier = Modifier.fillMaxWidth(),
                            color = com.example.modicanalyzer.ui.theme.ModicarePrimary
                        )
                        Text(
                            "Downloading model... $downloadProgress%",
                            fontSize = 12.sp,
                            color = com.example.modicanalyzer.ui.theme.TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Check for Updates Button (Manual trigger for auto-update)
                    Button(
                        onClick = {
                            if (!isCheckingUpdates) {
                                scope.launch {
                                    isCheckingUpdates = true
                                    val hasUpdate = analyzer.checkForModelUpdates()
                                    modelUpdateInfo = analyzer.getModelUpdateInfo()
                                    isModelAvailable = analyzer.isLocalModelAvailable()
                                    isCheckingUpdates = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.example.modicanalyzer.ui.theme.ModicareSecondary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCheckingUpdates
                    ) {
                        if (isCheckingUpdates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Update, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isCheckingUpdates) "Checking..." else "Check for Updates Now")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Auto-update info message
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = com.example.modicanalyzer.ui.theme.ModicarePrimary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "The app automatically checks for model updates every 6 hours when connected to the internet.",
                                fontSize = 12.sp,
                                color = com.example.modicanalyzer.ui.theme.TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
        
        item {
            // Analysis Mode Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Analysis Mode",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Online/Offline Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Offline Mode",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = com.example.modicanalyzer.ui.theme.TextPrimary
                            )
                            Text(
                                if (isOfflineMode) "Using local model for analysis" 
                                else "Using server for analysis",
                                fontSize = 12.sp,
                                color = com.example.modicanalyzer.ui.theme.TextSecondary
                            )
                        }
                        
                        Switch(
                            checked = isOfflineMode,
                            onCheckedChange = { enabled ->
                                isOfflineMode = enabled
                                analyzer.setOfflineMode(enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.Gray
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Auto Update Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Auto Update Model",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = com.example.modicanalyzer.ui.theme.TextPrimary
                            )
                            Text(
                                if (isAutoUpdateEnabled) "Automatically download model updates" 
                                else "Manual model updates only",
                                fontSize = 12.sp,
                                color = com.example.modicanalyzer.ui.theme.TextSecondary
                            )
                        }
                        
                        Switch(
                            checked = isAutoUpdateEnabled,
                            onCheckedChange = { enabled ->
                                isAutoUpdateEnabled = enabled
                                analyzer.setAutoUpdateEnabled(enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.Gray
                            )
                        )
                    }
                }
            }
        }
        
        item {
            // Current Analysis Mode Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOfflineMode && isModelAvailable) 
                        Color(0xFF10B981).copy(alpha = 0.1f)
                    else if (isOfflineMode) 
                        Color(0xFFF59E0B).copy(alpha = 0.1f)
                    else 
                        Color(0xFF3B82F6).copy(alpha = 0.1f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOfflineMode && isModelAvailable) Color(0xFF10B981).copy(alpha = 0.2f)
                                else if (isOfflineMode) Color(0xFFF59E0B).copy(alpha = 0.2f)
                                else Color(0xFF3B82F6).copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isOfflineMode) Icons.Default.PhoneAndroid else Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isOfflineMode) {
                                if (isModelAvailable) "Offline Mode (Ready)" else "Offline Mode (Download Model)"
                            } else "Online Mode (Server Analysis)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = com.example.modicanalyzer.ui.theme.TextPrimary
                        )
                        Text(
                            text = analyzer.getAnalysisModeInfo(),
                            fontSize = 12.sp,
                            color = com.example.modicanalyzer.ui.theme.TextSecondary
                        )
                    }
                }
            }
        }
    }
}