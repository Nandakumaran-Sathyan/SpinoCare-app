package com.example.modicanalyzer 

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.modicanalyzer.data.local.dao.PendingSignupDao
import com.example.modicanalyzer.data.local.entity.PendingSignupEntity
import com.example.modicanalyzer.data.remote.FirestoreHelper
import com.example.modicanalyzer.data.repository.MySQLAuthRepository
import com.example.modicanalyzer.utils.SignupValidator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SignupActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "SignupActivity"
    }
    
    @Inject
    lateinit var authRepository: MySQLAuthRepository
    
    @Inject
    lateinit var firestoreHelper: FirestoreHelper
    
    @Inject
    lateinit var firebaseAuth: FirebaseAuth
    
    @Inject
    lateinit var pendingSignupDao: PendingSignupDao
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        android.util.Log.d(TAG, "=== SignupActivity onCreate ===")
        android.util.Log.d(TAG, "Network available: ${isNetworkAvailable()}")
        
        // Process pending signups when app starts (if network available)
        processPendingSignups()
        
        setContent {
            com.example.modicanalyzer.ui.theme.ModicAnalyzerTheme(darkTheme = false, dynamicColor = false) {
                SignupScreen(
                    onSignupSuccess = {
                        // Navigate to main activity on successful signup
                        startActivity(Intent(this@SignupActivity, SimpleMainActivity::class.java))
                        finish()
                    },
                    onNavigateToLogin = {
                        // Navigate back to login activity
                        finish() // This will return to login activity
                    },
                    onSignup = { name, email, phone, password, role, onComplete ->
                        signupWithMySQL(name, email, phone, password, role, onComplete)
                    }
                )
            }
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    /**
     * Sign up with MySQL authentication (new implementation)
     */
    private fun signupWithMySQL(
        name: String,
        email: String,
        phone: String,
        password: String,
        role: String,
        onComplete: () -> Unit
    ) {
        // Validate password
        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            onComplete() // Reset loading state
            return
        }
        
        lifecycleScope.launch {
            try {
                android.util.Log.d(TAG, "📝 Registering user with MySQL: $email")
                
                val result = authRepository.register(
                    email = email,
                    password = password,
                    displayName = name,
                    phoneNumber = phone.takeIf { it.isNotBlank() }
                )
                
                result.onSuccess { authResponse ->
                    android.util.Log.d(TAG, "✅ Registration successful: ${authResponse.email}")
                    android.util.Log.d(TAG, "User UID: ${authResponse.uid}")
                    
                    Toast.makeText(
                        this@SignupActivity,
                        "✅ Account created successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    onComplete() // Reset loading state
                    
                    // Navigate to main activity
                    startActivity(Intent(this@SignupActivity, SimpleMainActivity::class.java))
                    finish()
                    
                }.onFailure { e ->
                    android.util.Log.e(TAG, "❌ Registration failed: ${e.message}")
                    
                    onComplete() // Reset loading state
                    
                    Toast.makeText(
                        this@SignupActivity,
                        "Registration failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Registration error", e)
                
                onComplete() // Reset loading state
                
                Toast.makeText(
                    this@SignupActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Legacy Firebase signup (kept for reference, not used)
     */
    private fun signupWithFirestore(
        name: String,
        email: String,
        phone: String,
        password: String,
        role: String
    ) {
        lifecycleScope.launch {
            try {
                // Check network connectivity
                if (!isNetworkAvailable()) {
                    android.util.Log.w(TAG, "⚠️ No network - queueing signup for later")
                    
                    // Queue signup for later processing
                    val pendingSignup = PendingSignupEntity.fromSignupData(
                        fullName = name,
                        email = email,
                        phone = phone,
                        password = password,
                        role = role
                    )
                    
                    val id = pendingSignupDao.insertPendingSignup(pendingSignup)
                    android.util.Log.i(TAG, "✅ Signup queued offline! ID: $id")
                    
                    Toast.makeText(
                        this@SignupActivity,
                        "No internet! Signup queued and will be processed when online.",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Navigate to main activity (they can use app offline)
                    startActivity(Intent(this@SignupActivity, SimpleMainActivity::class.java))
                    finish()
                    return@launch
                }
                
                // Network available - create account immediately
                android.util.Log.d(TAG, "📡 Network available - creating account immediately")
                
                // Create Firebase Auth user
                firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { authResult ->
                        val user = authResult.user
                        if (user != null) {
                            // Update Firebase Auth profile with display name
                            val profileUpdates = UserProfileChangeRequest.Builder()
                                .setDisplayName(name)
                                .build()
                            
                            user.updateProfile(profileUpdates)
                                .addOnSuccessListener {
                                    // Send email verification
                                    user.sendEmailVerification()
                                        .addOnSuccessListener {
                                            android.util.Log.d(TAG, "✅ Verification email sent to ${user.email}")
                                            
                                            // Now create Firestore profile
                                            lifecycleScope.launch {
                                                val result = firestoreHelper.createOrUpdateUserProfile(
                                                    userId = user.uid,
                                                    name = name,
                                                    email = email,
                                                    role = role.lowercase(),
                                                    profileImageUrl = null
                                                )
                                                
                                                if (result.isSuccess) {
                                                    // Also save locally for backward compatibility
                                                    val authManager = AuthManager(this@SignupActivity)
                                                    authManager.saveUserProfileIfNeeded(email, name, role)
                                                    
                                                    Toast.makeText(
                                                        this@SignupActivity,
                                                        "✅ Account created! Please check your email to verify your account.",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    
                                                    startActivity(Intent(this@SignupActivity, SimpleMainActivity::class.java))
                                                    finish()
                                                } else {
                                                    Toast.makeText(
                                                        this@SignupActivity,
                                                        "Account created but profile save failed: ${result.exceptionOrNull()?.message}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            android.util.Log.w(TAG, "⚠️ Failed to send verification email", e)
                                            
                                            // Still create profile even if verification email fails
                                            lifecycleScope.launch {
                                                val result = firestoreHelper.createOrUpdateUserProfile(
                                                    userId = user.uid,
                                                    name = name,
                                                    email = email,
                                                    role = role.lowercase(),
                                                    profileImageUrl = null
                                                )
                                                
                                                if (result.isSuccess) {
                                                    val authManager = AuthManager(this@SignupActivity)
                                                    authManager.saveUserProfileIfNeeded(email, name, role)
                                                    
                                                    Toast.makeText(
                                                        this@SignupActivity,
                                                        "✅ Account created! (Email verification failed: ${e.message})",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    
                                                    startActivity(Intent(this@SignupActivity, SimpleMainActivity::class.java))
                                                    finish()
                                                }
                                            }
                                        }
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(
                                        this@SignupActivity,
                                        "Failed to update profile: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        // Fallback to local signup
                        val authManager = AuthManager(this@SignupActivity)
                        authManager.localLogin(email, name, role)
                        
                        Toast.makeText(
                            this@SignupActivity,
                            "Account created locally: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        startActivity(Intent(this@SignupActivity, SimpleMainActivity::class.java))
                        finish()
                    }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SignupActivity,
                    "Signup failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    /**
     * Process any pending signups from offline queue
     */
    private fun processPendingSignups() {
        if (!isNetworkAvailable()) {
            android.util.Log.d(TAG, "No network - skipping pending signup processing")
            return
        }
        
        lifecycleScope.launch {
            try {
                val pendingSignups = pendingSignupDao.getPendingSignups()
                
                if (pendingSignups.isEmpty()) {
                    android.util.Log.d(TAG, "No pending signups to process")
                    return@launch
                }
                
                android.util.Log.i(TAG, "🚀 Processing ${pendingSignups.size} pending signup(s)...")
                
                pendingSignups.forEach { signup ->
                    processQueuedSignup(signup)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error processing pending signups: ${e.message}", e)
            }
        }
    }
    
    /**
     * Process a single queued signup
     */
    private suspend fun processQueuedSignup(signup: PendingSignupEntity) {
        android.util.Log.d(TAG, "Processing queued signup: ${signup.email}")
        
        try {
            // Update status to processing
            pendingSignupDao.updateSignup(signup.copy(status = PendingSignupEntity.SignupStatus.PROCESSING))
            
            // Decrypt password
            val decryptedPassword = PendingSignupEntity.decryptPassword(signup.passwordHash)
            
            // Create Firebase Auth account
            firebaseAuth.createUserWithEmailAndPassword(signup.email, decryptedPassword)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    if (user != null) {
                        android.util.Log.i(TAG, "✅ Firebase Auth account created for queued signup: ${user.uid}")
                        
                        // Update Firebase profile
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(signup.fullName)
                            .build()
                        
                        user.updateProfile(profileUpdates)
                            .addOnSuccessListener {
                                // Create Firestore profile
                                lifecycleScope.launch {
                                    val result = firestoreHelper.createOrUpdateUserProfile(
                                        userId = user.uid,
                                        name = signup.fullName,
                                        email = signup.email,
                                        role = signup.role.lowercase(),
                                        profileImageUrl = null
                                    )
                                    
                                    if (result.isSuccess) {
                                        android.util.Log.i(TAG, "✅ Queued signup completed: ${signup.email}")
                                        pendingSignupDao.markAsCompleted(signup.id)
                                        
                                        runOnUiThread {
                                            Toast.makeText(
                                                this@SignupActivity,
                                                "✅ Queued account created: ${signup.fullName}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else {
                                        android.util.Log.e(TAG, "Firestore error for queued signup: ${signup.email}")
                                        pendingSignupDao.markAsFailed(signup.id, "Firestore error")
                                    }
                                }
                            }
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e(TAG, "Failed to process queued signup: ${e.message}")
                    lifecycleScope.launch {
                        val errorMsg = if (e.message?.contains("email address is already in use") == true) {
                            "Email already registered"
                        } else {
                            e.message ?: "Unknown error"
                        }
                        pendingSignupDao.markAsFailed(signup.id, errorMsg)
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Exception processing queued signup: ${e.message}", e)
            pendingSignupDao.markAsFailed(signup.id, e.message ?: "Unknown error")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onSignupSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onSignup: (String, String, String, String, String, () -> Unit) -> Unit = { _, _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var acceptTerms by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf("Patient") }
    val roles = listOf("Patient", "Doctor", "Radiologist", "Researcher")
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    
    // Validation states
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    
    // Show dialogs
    if (showTermsDialog) {
        TermsAndConditionsDialog(onDismiss = { showTermsDialog = false })
    }
    
    if (showPrivacyDialog) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyDialog = false })
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Header with App Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateToLogin) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back to Login",
                        tint = com.example.modicanalyzer.ui.theme.ModicarePrimary
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SpinoCare",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Join the SpinoCare community",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
                
                // Spacer to balance the back button
                Spacer(modifier = Modifier.width(48.dp))
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Signup Form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // Full Name Field
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { 
                            fullName = it
                            nameError = if (it.isNotBlank()) {
                                val result = SignupValidator.validateFullName(it.trim())
                                if (!result.isValid) result.errorMessage else null
                            } else null
                        },
                        label = { Text("Full Name") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = "Full Name")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = nameError != null,
                        supportingText = {
                            if (nameError != null) {
                                Text(
                                    text = nameError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            } else {
                                Text("Letters and spaces only", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { 
                            email = it
                            emailError = if (it.isNotBlank()) {
                                val result = SignupValidator.validateEmail(it.trim())
                                if (!result.isValid) result.errorMessage else null
                            } else null
                        },
                        label = { Text("Email Address") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = "Email")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = emailError != null,
                        supportingText = {
                            if (emailError != null) {
                                Text(
                                    text = emailError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            } else {
                                Text("Valid email format required", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Phone Field
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { 
                            // Only allow digits
                            if (it.isEmpty() || it.matches(Regex("^[0-9]*$"))) {
                                phone = it.take(10) // Limit to 10 digits
                                phoneError = if (it.isNotBlank()) {
                                    val result = SignupValidator.validatePhoneNumber(it)
                                    if (!result.isValid) result.errorMessage else null
                                } else null
                            }
                        },
                        label = { Text("Phone Number") },
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = "Phone")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = phoneError != null,
                        supportingText = {
                            if (phoneError != null) {
                                Text(
                                    text = phoneError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            } else {
                                Text("10 digits only", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Role Selection
                    var expanded by remember { mutableStateOf(false) }
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedRole,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Role") },
                            leadingIcon = {
                                Icon(Icons.Default.AccountCircle, contentDescription = "Role")
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            roles.forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(role) },
                                    onClick = {
                                        selectedRole = role
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it
                            passwordError = if (it.isNotBlank()) {
                                val result = SignupValidator.validatePassword(it)
                                if (!result.isValid) result.errorMessage else null
                            } else null
                            // Also revalidate confirm password
                            if (confirmPassword.isNotBlank()) {
                                val confirmResult = SignupValidator.validateConfirmPassword(it, confirmPassword)
                                confirmPasswordError = if (!confirmResult.isValid) confirmResult.errorMessage else null
                            }
                        },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = "Password")
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    if (isPasswordVisible) Icons.Default.Clear else Icons.Default.Done,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = passwordError != null,
                        supportingText = {
                            if (passwordError != null) {
                                Text(
                                    text = passwordError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            } else {
                                Text("8+ chars, uppercase, lowercase, number, special char", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Confirm Password Field
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { 
                            confirmPassword = it
                            confirmPasswordError = if (it.isNotBlank()) {
                                val result = SignupValidator.validateConfirmPassword(password, it)
                                if (!result.isValid) result.errorMessage else null
                            } else null
                        },
                        label = { Text("Confirm Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = "Confirm Password")
                        },
                        trailingIcon = {
                            IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                Icon(
                                    if (isConfirmPasswordVisible) Icons.Default.Clear else Icons.Default.Done,
                                    contentDescription = if (isConfirmPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = confirmPasswordError != null,
                        supportingText = {
                            if (confirmPasswordError != null) {
                                Text(
                                    text = confirmPasswordError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Terms and Conditions
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = acceptTerms,
                            onCheckedChange = { acceptTerms = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = com.example.modicanalyzer.ui.theme.ModicarePrimary
                            )
                        )
                        Column(
                            modifier = Modifier
                                .padding(start = 8.dp, top = 12.dp)
                                .weight(1f)
                        ) {
                            Text(
                                text = "I agree to the ",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                            Row {
                                TextButton(
                                    onClick = { showTermsDialog = true },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(20.dp)
                                ) {
                                    Text(
                                        text = "Terms & Conditions",
                                        fontSize = 14.sp,
                                        color = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = " and ",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                TextButton(
                                    onClick = { showPrivacyDialog = true },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(20.dp)
                                ) {
                                    Text(
                                        text = "Privacy Policy",
                                        fontSize = 14.sp,
                                        color = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    
                    // Show error if terms not accepted
                    if (!acceptTerms && fullName.isNotEmpty()) {
                        Text(
                            text = "⚠️ You must accept the terms to create an account",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 48.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Signup Button
                    Button(
                        onClick = {
                            // Comprehensive validation
                            val validationResults = SignupValidator.validateSignupForm(
                                fullName = fullName.trim(),
                                email = email.trim(),
                                phone = phone.trim(),
                                password = password,
                                confirmPassword = confirmPassword,
                                acceptedTerms = acceptTerms
                            )
                            
                            if (SignupValidator.isAllValid(validationResults)) {
                                isLoading = true
                                // Use MySQL authentication with loading callback
                                onSignup(fullName.trim(), email.trim(), phone.trim(), password, selectedRole) {
                                    // Reset loading state when signup completes (success or error)
                                    isLoading = false
                                }
                            } else {
                                // Show first validation error
                                val errorMessage = SignupValidator.getFirstError(validationResults)
                                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.example.modicanalyzer.ui.theme.ModicarePrimary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                "Create Account",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Login Navigation
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Already have an account? ",
                    color = Color.Gray
                )
                TextButton(onClick = onNavigateToLogin) {
                    Text(
                        "Sign In",
                        color = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun TermsAndConditionsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Terms & Conditions",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = """
                        Welcome to SpinoCare!
                        
                        By using our MRI analysis application, you agree to the following terms:
                        
                        1. MEDICAL DISCLAIMER
                        • SpinoCare is a diagnostic assistance tool and should not replace professional medical advice
                        • Always consult with qualified healthcare professionals for diagnosis and treatment
                        • Results provided are for informational purposes only
                        
                        2. USER RESPONSIBILITIES
                        • You must be 18+ years old or have parental consent
                        • Provide accurate medical information
                        • Keep your account credentials secure
                        • Do not share patient data without proper authorization
                        
                        3. DATA USAGE
                        • MRI images are encrypted and stored securely
                        • Data is used for analysis and improving our AI models
                        • We comply with HIPAA and GDPR regulations
                        • You can request data deletion at any time
                        
                        4. ACCURACY & LIABILITY
                        • We strive for high accuracy but cannot guarantee 100% correctness
                        • SpinoCare is not liable for medical decisions based on our analysis
                        • Always verify results with medical professionals
                        
                        5. INTELLECTUAL PROPERTY
                        • All content, AI models, and software are proprietary
                        • Unauthorized copying or distribution is prohibited
                        
                        6. SERVICE AVAILABILITY
                        • We aim for 99.9% uptime but cannot guarantee uninterrupted service
                        • Features may change with updates
                        
                        7. TERMINATION
                        • We reserve the right to terminate accounts for violations
                        • You can delete your account anytime from settings
                        
                        Last updated: November 2025
                    """.trimIndent(),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("I Understand")
            }
        }
    )
}

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Privacy Policy",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = """
                        SpinoCare Privacy Policy
                        
                        1. INFORMATION WE COLLECT
                        • Account Information: name, email, phone number
                        • Medical Data: MRI images, analysis results, metadata
                        • Usage Data: app interactions, device information
                        • Location: general location for service optimization
                        
                        2. HOW WE USE YOUR DATA
                        • Provide MRI analysis and diagnostic assistance
                        • Improve AI models and application performance
                        • Send important notifications and updates
                        • Comply with legal and regulatory requirements
                        • Research and development (anonymized data only)
                        
                        3. DATA SECURITY
                        • End-to-end encryption for all medical data
                        • Firebase secure authentication
                        • Regular security audits and updates
                        • Access controls and role-based permissions
                        • Secure cloud storage (Google Cloud Platform)
                        
                        4. DATA SHARING
                        We DO NOT sell your personal data. We may share data with:
                        • Healthcare providers (with your consent)
                        • Legal authorities (when required by law)
                        • Service providers (under strict contracts)
                        • Research institutions (anonymized data only)
                        
                        5. YOUR RIGHTS
                        • Access your data anytime
                        • Request data corrections
                        • Delete your account and data
                        • Export your data
                        • Opt-out of non-essential communications
                        
                        6. DATA RETENTION
                        • Active accounts: data retained indefinitely
                        • Deleted accounts: data removed within 30 days
                        • Legal requirements may extend retention
                        
                        7. COOKIES & TRACKING
                        • We use minimal cookies for authentication
                        • Analytics for improving user experience
                        • No third-party advertising trackers
                        
                        8. CHILDREN'S PRIVACY
                        • Not intended for users under 18 without consent
                        • Parental approval required for minors
                        
                        9. INTERNATIONAL USERS
                        • Data stored in secure US-based servers
                        • Comply with GDPR, HIPAA, and local regulations
                        
                        10. CHANGES TO POLICY
                        • We may update this policy
                        • Users notified of significant changes
                        • Continued use implies acceptance
                        
                        Contact: support@spinocare.com
                        Last updated: November 2025
                    """.trimIndent(),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("I Understand")
            }
        }
    )
}
