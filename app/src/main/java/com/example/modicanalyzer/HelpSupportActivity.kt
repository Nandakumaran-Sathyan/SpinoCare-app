package com.example.modicanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class HelpSupportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            com.example.modicanalyzer.ui.theme.ModicAnalyzerTheme(darkTheme = false, dynamicColor = false) {
                HelpSupportScreen {
                    finish()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSupportScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Support", color = Color.White) },
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
                    "Frequently Asked Questions",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant
                )
            }
            
            item {
                FAQItem(
                    question = "How do I analyze an image?",
                    answer = "Simply tap the camera button on the main screen to take a photo or select an image from your gallery. The AI will automatically analyze it for medical conditions."
                )
            }
            
            item {
                FAQItem(
                    question = "Is my data secure?",
                    answer = "Yes, all your medical data is encrypted and stored securely. We follow strict HIPAA guidelines to protect your privacy."
                )
            }
            
            item {
                FAQItem(
                    question = "Can I use this offline?",
                    answer = "Yes, the app can work in offline mode using a local AI model. However, online mode provides more accurate results with the latest AI models."
                )
            }
            
            item {
                FAQItem(
                    question = "How accurate are the results?",
                    answer = "Our AI model has been trained on thousands of medical images and provides high accuracy. However, always consult with a healthcare professional for proper diagnosis."
                )
            }
            
            item {
                Text(
                    "Contact Support",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant
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
                            "Need more help?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Email: support@modicanalyzer.com\nPhone: +1 (555) 123-4567",
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
fun FAQItem(question: String, answer: String) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    question,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    answer,
                    fontSize = 14.sp,
                    color = com.example.modicanalyzer.ui.theme.TextSecondary
                )
            }
        }
    }
}