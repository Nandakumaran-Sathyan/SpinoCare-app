package com.example.modicanalyzer

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.modicanalyzer.viewmodel.UserProfileViewModel
import com.example.modicanalyzer.data.remote.FirestoreHelper
import com.example.modicanalyzer.data.remote.FirebaseStorageHelper
import com.example.modicanalyzer.data.repository.ImageUploadRepository
import com.example.modicanalyzer.data.repository.MySQLRepository
import com.example.modicanalyzer.data.repository.MySQLAuthRepository
import com.example.modicanalyzer.util.NetworkConnectivityObserver
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.modicanalyzer.worker.ImageUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
import java.io.File


@AndroidEntryPoint
class SimpleMainActivity : ComponentActivity() {
    private lateinit var modicAnalyzer: ModicAnalyzer
    private val userProfileViewModel: UserProfileViewModel by viewModels()
    
    // Firebase dependencies injected by Hilt
    @javax.inject.Inject
    lateinit var firestoreHelper: FirestoreHelper
    
    @javax.inject.Inject
    lateinit var storageHelper: FirebaseStorageHelper
    
    @javax.inject.Inject
    lateinit var firebaseAuth: FirebaseAuth
    
    @javax.inject.Inject
    lateinit var imageUploadRepository: ImageUploadRepository
    
    @javax.inject.Inject
    lateinit var networkObserver: NetworkConnectivityObserver
    
    @javax.inject.Inject
    lateinit var workManager: WorkManager
    
    // MySQL Repository for new database backend
    @javax.inject.Inject
    lateinit var mySQLRepository: MySQLRepository
    
    // MySQL Auth Repository
    @javax.inject.Inject
    lateinit var authRepository: MySQLAuthRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if user is logged in
        if (!authRepository.isLoggedIn()) {
            android.util.Log.d("SimpleMainActivity", "❌ User not logged in, redirecting to login")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        
        modicAnalyzer = ModicAnalyzer(this)
        
        // Initialize automatic model updates
        modicAnalyzer.initializeAutoUpdates()
        
        setContent {
            com.example.modicanalyzer.ui.theme.ModicAnalyzerTheme(darkTheme = false, dynamicColor = false) {
                MainScreen(
                    analyzer = modicAnalyzer,
                    userProfileViewModel = userProfileViewModel,
                    firestoreHelper = firestoreHelper,
                    storageHelper = storageHelper,
                    firebaseAuth = firebaseAuth,
                    imageUploadRepository = imageUploadRepository,
                    networkObserver = networkObserver,
                    workManager = workManager,
                    mySQLRepository = mySQLRepository,
                    authRepository = authRepository
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        modicAnalyzer.cleanup()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    analyzer: ModicAnalyzer,
    userProfileViewModel: UserProfileViewModel,
    firestoreHelper: FirestoreHelper,
    storageHelper: FirebaseStorageHelper,
    firebaseAuth: FirebaseAuth,
    imageUploadRepository: ImageUploadRepository,
    networkObserver: NetworkConnectivityObserver,
    workManager: WorkManager,
    mySQLRepository: MySQLRepository,
    authRepository: MySQLAuthRepository
) {
    var selectedScreen by remember { mutableStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Observe user profile
    val userProfile by userProfileViewModel.userProfile.collectAsState()
    
    // NOTE: Auto-download disabled to prevent app crashes
    // Users can manually download model from Profile → Model Settings
    // The background download was causing "Module config changed" crashes
    
    Scaffold(
        topBar = {
            // Simple clean top bar with user name from Firestore
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SpinoCare",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        // Display name from MySQL auth repository
                        val userName = authRepository.getCurrentUserDisplayName() 
                            ?: authRepository.getCurrentUserEmail()?.substringBefore("@")
                            ?: "User"
                        
                        Text(
                            text = "Welcome, $userName",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.example.modicanalyzer.ui.theme.ModicarePrimary
                ),
                actions = {
                    StatusIndicator()
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                contentColor = com.example.modicanalyzer.ui.theme.ModicarePrimary
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Analyze") },
                    selected = selectedScreen == 0,
                    onClick = { selectedScreen = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                        selectedTextColor = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                        indicatorColor = com.example.modicanalyzer.ui.theme.ModicarePrimary.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text("Guide") },
                    selected = selectedScreen == 1,
                    onClick = { selectedScreen = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                        selectedTextColor = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                        indicatorColor = com.example.modicanalyzer.ui.theme.ModicarePrimary.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Profile") },
                    selected = selectedScreen == 2,
                    onClick = { selectedScreen = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                        selectedTextColor = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                        indicatorColor = com.example.modicanalyzer.ui.theme.ModicarePrimary.copy(alpha = 0.1f)
                    )
                )
            }
        }
    ) { paddingValues ->
        when (selectedScreen) {
            0 -> AnalyzeScreen(analyzer, paddingValues, firestoreHelper, storageHelper, firebaseAuth, imageUploadRepository, networkObserver, workManager, mySQLRepository, authRepository)
            1 -> Box(modifier = Modifier.padding(paddingValues)) { ModicGuideScreen() }
            2 -> Box(modifier = Modifier.padding(paddingValues)) { 
                ProfileScreen(
                    onHelpSupportClick = {
                        val intent = android.content.Intent(context, HelpSupportActivity::class.java)
                        context.startActivity(intent)
                    },
                    onPrivacyPolicyClick = {
                        val intent = android.content.Intent(context, PrivacyPolicyActivity::class.java)
                        context.startActivity(intent)
                    },
                    onModelSettingsClick = {
                        val intent = android.content.Intent(context, ModelSettingsActivity::class.java)
                        context.startActivity(intent)
                    },
                    onSignOutClick = {
                        // Logout from MySQL auth
                        authRepository.logout()
                        android.util.Log.d("SimpleMainActivity", "🚪 User logged out")
                        android.widget.Toast.makeText(context, "Signed out successfully", android.widget.Toast.LENGTH_SHORT).show()
                        
                        val intent = android.content.Intent(context, LoginActivity::class.java)
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                        context.startActivity(intent)
                    },
                    authRepository = authRepository
                )
            }
        }
    }
}

@Composable
fun StatusIndicator() {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("modic_settings", android.content.Context.MODE_PRIVATE)
    val isOfflineMode = sharedPrefs.getBoolean("offline_mode", false)
    
    val isModelAvailable = remember {
        val modelFile = java.io.File(context.filesDir, "modic_model.tflite")
        modelFile.exists()
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzeScreen(
    analyzer: ModicAnalyzer, 
    paddingValues: PaddingValues,
    firestoreHelper: FirestoreHelper,
    storageHelper: FirebaseStorageHelper,
    firebaseAuth: FirebaseAuth,
    imageUploadRepository: ImageUploadRepository,
    networkObserver: NetworkConnectivityObserver,
    workManager: WorkManager,
    mySQLRepository: MySQLRepository,
    authRepository: MySQLAuthRepository
) {
    var t1Image by remember { mutableStateOf<Bitmap?>(null) }
    var t2Image by remember { mutableStateOf<Bitmap?>(null) }
    var analysisResult by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val activity = context as ComponentActivity
    
    // State to track which image we're cropping (1 = T1, 2 = T2)
    var pendingCropTarget by remember { mutableStateOf(0) }
    
    // uCrop result launcher
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val resultUri = UCrop.getOutput(result.data!!)
            resultUri?.let { uri ->
                val bmp = ImageUtils.getBitmapFromUri(context, uri)
                if (bmp != null) {
                    when (pendingCropTarget) {
                        1 -> t1Image = bmp
                        2 -> t2Image = bmp
                    }
                }
                // Clean up temp file
                try {
                    File(uri.path ?: "").delete()
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
        }
        pendingCropTarget = 0
    }

    val t1ImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingCropTarget = 1
            launchUCrop(activity, it, cropLauncher)
        }
    }
    
    val t2ImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingCropTarget = 2
            launchUCrop(activity, it, cropLauncher)
        }
    }

    
    
    fun performAnalysis() {
        val t1 = t1Image
        val t2 = t2Image
        
        if (t1 == null || t2 == null) {
            Toast.makeText(context, "Please select both T1 and T2 weighted images", Toast.LENGTH_SHORT).show()
            return
        }
        
        isAnalyzing = true
        
        (context as ComponentActivity).lifecycleScope.launch {
            try {
                val result = analyzer.analyze(t1, t2)
                
                withContext(Dispatchers.Main) {
                    isAnalyzing = false
                    analysisResult = result.getDisplayText()
                    showResultDialog = true
                }
                
                // Save to cloud (Firebase Storage + MySQL) after successful analysis
                // Get user ID from MySQL auth
                val userId = authRepository.getCurrentUserUid()
                if (userId == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Please login first", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                val isOfflineUser = false // Using MySQL auth, always authenticated
                
                // Check if device is online
                val isOnline = networkObserver.isCurrentlyConnected()
                
                try {
                    // Prepare metadata
                    val metadata = buildMap<String, Any> {
                        put("mode", result.analysisMode)
                        put("timestamp", result.timestamp)
                        put("hasModicChange", result.hasModicChange)
                        put("noModicScore", result.noModicScore)
                        put("modicScore", result.modicScore)
                        result.changeType?.let { put("changeType", it) }
                        result.details?.let { put("details", it) }
                    }
                    
                    val resultLabel = if (result.hasModicChange) {
                        "Modic Change Detected"
                    } else {
                        "No Modic Changes"
                    }
                    
                    if (isOnline && !isOfflineUser) {
                        // Online user: Upload images immediately
                        android.util.Log.d("AnalyzeScreen", "📤 Uploading images to Firebase Storage...")
                        
                        val uploadResult = storageHelper.uploadMRIImages(userId, t1, t2)
                        
                        uploadResult.onSuccess { (t1Url, t2Url) ->
                            android.util.Log.d("AnalyzeScreen", "✅ Images uploaded successfully")
                            
                            (context as ComponentActivity).lifecycleScope.launch {
                                // Save to MySQL database via REST API
                                val entryId = java.util.UUID.randomUUID().toString()
                                val analysisMode = result.analysisMode // "online" or "offline"
                                val modelVersion = "v1.0" // You can make this dynamic
                                val processingTimeMs = null // Add if you track this
                                
                                val saveResult = mySQLRepository.saveAnalysis(
                                    firebaseUid = userId,
                                    entryId = entryId,
                                    t1ImageUrl = t1Url,
                                    t2ImageUrl = t2Url,
                                    analysisResult = resultLabel,
                                    confidence = result.confidence,
                                    analysisMode = analysisMode,
                                    modelVersion = modelVersion,
                                    processingTimeMs = processingTimeMs
                                )
                                
                                saveResult.onSuccess { saveData ->
                                    withContext(Dispatchers.Main) {
                                        android.util.Log.d("AnalyzeScreen", "🎉 Analysis saved to MySQL: ${saveData.entryId}")
                                        Toast.makeText(context, "Analysis saved to database ✅", Toast.LENGTH_SHORT).show()
                                    }
                                }.onFailure { e ->
                                    withContext(Dispatchers.Main) {
                                        android.util.Log.e("AnalyzeScreen", "❌ Failed to save to MySQL", e)
                                        Toast.makeText(context, "Failed to save analysis: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }.onFailure { e ->
                            // Upload failed
                            android.util.Log.w("AnalyzeScreen", "⚠️ Image upload failed", e)
                            
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Image upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        
                    } else {
                        // No network - show message
                        android.util.Log.d("AnalyzeScreen", "📴 No network - cannot upload images")
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "No network. Please connect to internet to save analysis.", Toast.LENGTH_LONG).show()
                        }
                    }
                    
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        android.util.Log.e("AnalyzeScreen", "❌ Save error", e)
                    }
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    isAnalyzing = false
                    Toast.makeText(context, "Error during analysis: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFAFBFC),
                        Color(0xFFF1F5F9)
                    )
                )
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            HeaderCard()
        }
        
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ImageCard(
                        modifier = Modifier.weight(1f),
                        title = "T1-weighted",
                        subtitle = "FLAIR Image",
                        image = t1Image,
                        onClick = { t1ImagePicker.launch("image/*") }
                    )
                    
                    ImageCard(
                        modifier = Modifier.weight(1f),
                        title = "T2-weighted", 
                        subtitle = "FLAIR Image",
                        image = t2Image,
                        onClick = { t2ImagePicker.launch("image/*") }
                    )
                }
            }
        }
        
        item {
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
                    Text(
                        "Medical Image Analysis",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1F2937)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        "Advanced AI-powered analysis for medical diagnostics",
                        fontSize = 14.sp,
                        color = Color(0xFF6B7280),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Button(
                        onClick = ::performAnalysis,
                        enabled = t1Image != null && t2Image != null && !isAnalyzing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                            disabledContainerColor = com.example.modicanalyzer.ui.theme.ModicarePrimary.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isAnalyzing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing...", color = Color.White, fontSize = 16.sp)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyze Images", color = Color.White, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showResultDialog) {
        ResultDialog(
            result = analysisResult ?: "",
            onDismiss = { 
                showResultDialog = false
                analysisResult = null
            }
        )
    }
}

@Composable
fun HeaderCard() {
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
            Text(
                "Advanced Medical AI",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Upload T1 and T2 weighted FLAIR images for professional medical analysis",
                fontSize = 14.sp,
                color = Color(0xFF6B7280),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ImageCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    image: Bitmap?,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (image != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .padding(12.dp)
                        .align(Alignment.BottomStart)
                ) {
                    Column {
                        Text(
                            title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            subtitle,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1F2937),
                    textAlign = TextAlign.Center
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ResultDialog(result: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Analysis Results",
                fontWeight = FontWeight.Bold,
                color = com.example.modicanalyzer.ui.theme.ModicarePrimary
            )
        },
        text = {
            Text(
                result,
                fontSize = 16.sp,
                color = Color(0xFF374151)
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = com.example.modicanalyzer.ui.theme.ModicarePrimary
                )
            ) {
                Text("OK", color = Color.White)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Launch uCrop activity for 1:1 aspect ratio cropping
 */
fun launchUCrop(
    activity: ComponentActivity,
    sourceUri: Uri,
    cropLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
) {
    // Create destination URI for cropped image
    val destinationFileName = "cropped_${System.currentTimeMillis()}.jpg"
    val destinationUri = Uri.fromFile(File(activity.cacheDir, destinationFileName))
    
    // Configure uCrop options with app theme colors
    val options = UCrop.Options().apply {
        // Quality and crop settings
        setCompressionQuality(90)
        setFreeStyleCropEnabled(false) // Lock aspect ratio
        setShowCropGrid(true)
        setShowCropFrame(true)
        setCircleDimmedLayer(false) // Use square dimmed overlay
        
        // App theme colors (matching ModicarePrimary #E57373)
        setToolbarColor(android.graphics.Color.parseColor("#E57373")) // ModicarePrimary
        setStatusBarColor(android.graphics.Color.parseColor("#C62828")) // Darker red
        setToolbarWidgetColor(android.graphics.Color.WHITE) // White icons/text
        setActiveControlsWidgetColor(android.graphics.Color.parseColor("#E57373")) // Active widget color
        setRootViewBackgroundColor(android.graphics.Color.parseColor("#FFFFFF")) // White background
        setDimmedLayerColor(android.graphics.Color.parseColor("#AA000000")) // Semi-transparent black
        
        // Toolbar settings
        setToolbarTitle("Crop Image (1:1)")
        setHideBottomControls(false) // Show bottom controls
        
        // Gesture settings for better UX
        setAllowedGestures(UCropActivity.SCALE, UCropActivity.ROTATE, UCropActivity.ALL)
    }
    
    // Build uCrop intent with 1:1 aspect ratio
    val uCropIntent = UCrop.of(sourceUri, destinationUri)
        .withAspectRatio(1f, 1f) // Force 1:1 square aspect ratio
        .withMaxResultSize(1024, 1024) // Max output size
        .withOptions(options)
        .getIntent(activity)
    
    cropLauncher.launch(uCropIntent)
}