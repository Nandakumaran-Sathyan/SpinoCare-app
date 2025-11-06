package com.example.modicanalyzer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModicGuideScreen() {
    var selectedType by remember { mutableStateOf<ModicType?>(null) }
    
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Modic Changes Guide",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Learn about different types of Modic changes and their characteristics",
                        fontSize = 14.sp,
                        color = com.example.modicanalyzer.ui.theme.ModicareAccent,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
        }
        
        item {
            // Overview Card
            ModicOverviewCard()
        }
        
        item {
            // Modic Types
            Text(
                "Types of Modic Changes",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        items(ModicType.values().size) { index ->
            val modicType = ModicType.values()[index]
            ModicTypeCard(
                modicType = modicType,
                isExpanded = selectedType == modicType,
                onToggle = { 
                    selectedType = if (selectedType == modicType) null else modicType 
                }
            )
        }
    }
}

@Composable
fun ModicOverviewCard() {
    // Use a Box with a pale background and rounded border to avoid elevation halo
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = com.example.modicanalyzer.ui.theme.ModicareSecondary.copy(alpha = 0.18f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = com.example.modicanalyzer.ui.theme.ModicareAccent.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "What are Modic Changes?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                "Modic changes are signal intensity changes in the vertebral body bone marrow on MRI, " +
                "located adjacent to the endplates of degenerative discs. They represent different " +
                "stages of degenerative disc disease and inflammatory processes.",
                fontSize = 14.sp,
                color = com.example.modicanalyzer.ui.theme.TextPrimary,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun ModicTypeCard(
    modicType: ModicType,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 8.dp else 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(modicType.color.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            modicType.icon,
                            contentDescription = null,
                            tint = modicType.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            modicType.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant
                        )
                        Text(
                            modicType.subtitle,
                            fontSize = 12.sp,
                            color = com.example.modicanalyzer.ui.theme.ModicareAccent
                        )
                    }
                }
                
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = com.example.modicanalyzer.ui.theme.ModicareAccent
                )
            }
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Characteristics:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                modicType.characteristics.forEach { characteristic ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("• ", color = modicType.color, fontSize = 14.sp)
                        Text(
                            characteristic,
                            fontSize = 14.sp,
                            color = com.example.modicanalyzer.ui.theme.TextPrimary,
                            lineHeight = 18.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    "Clinical Significance:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = com.example.modicanalyzer.ui.theme.ModicarePrimaryVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    modicType.significance,
                    fontSize = 14.sp,
                    color = com.example.modicanalyzer.ui.theme.TextPrimary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

enum class ModicType(
    val title: String,
    val subtitle: String,
    val characteristics: List<String>,
    val significance: String,
    val color: Color,
    val icon: ImageVector
) {
    TYPE_1(
        title = "Modic Type 1",
        subtitle = "Inflammatory/Edematous Changes",
        characteristics = listOf(
            "Hypointense on T1-weighted images",
            "Hyperintense on T2-weighted images",
            "Represents bone marrow edema and inflammation",
            "Often associated with acute disc degeneration",
            "May cause significant back pain"
        ),
        significance = "Type 1 changes are often associated with acute inflammatory processes and may correlate with clinical symptoms. They represent the most painful type of Modic changes.",
        color = Color(0xFFDC2626),
        icon = Icons.Default.Warning
    ),
    
    TYPE_2(
        title = "Modic Type 2",
        subtitle = "Fatty Infiltration",
        characteristics = listOf(
            "Hyperintense on T1-weighted images",
            "Isointense or slightly hyperintense on T2-weighted images",
            "Represents fatty replacement of bone marrow",
            "Most common type of Modic changes",
            "Usually chronic and stable"
        ),
        significance = "Type 2 changes represent chronic degenerative processes with fatty infiltration. They are generally less painful than Type 1 changes and more stable over time.",
        color = Color(0xFFF59E0B),
        icon = Icons.Default.Star
    ),
    
    TYPE_3(
        title = "Modic Type 3",
        subtitle = "Sclerotic Changes",
        characteristics = listOf(
            "Hypointense on both T1 and T2-weighted images",
            "Represents subchondral bone sclerosis",
            "Least common type of Modic changes",
            "Associated with advanced disc degeneration",
            "Indicates chronic, end-stage changes"
        ),
        significance = "Type 3 changes represent the end-stage of degenerative disc disease with extensive bone sclerosis. They are typically seen in advanced degenerative conditions.",
        color = Color(0xFF059669),
        icon = Icons.Default.Check
    )
}