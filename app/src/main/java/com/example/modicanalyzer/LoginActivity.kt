package com.example.modicanalyzer

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.modicanalyzer.data.local.dao.PendingSignupDao
import com.example.modicanalyzer.data.local.entity.PendingSignupEntity
import com.example.modicanalyzer.data.model.AuthState
import com.example.modicanalyzer.data.remote.FirestoreHelper
import com.example.modicanalyzer.data.repository.MySQLAuthRepository
import com.example.modicanalyzer.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "LoginActivity"
    }
    
    private val authViewModel: AuthViewModel by viewModels()
    
    @Inject
    lateinit var authRepository: MySQLAuthRepository
    
    @Inject
    lateinit var pendingSignupDao: PendingSignupDao
    
    @Inject
    lateinit var firestoreHelper: FirestoreHelper
    
    @Inject
    lateinit var firebaseAuth: FirebaseAuth
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        android.util.Log.d(TAG, "=== LoginActivity onCreate ===")
        
        // Check if user is already logged in with MySQL auth
        if (authRepository.isLoggedIn()) {
            android.util.Log.d(TAG, "✅ User already logged in, navigating to main")
            startActivity(Intent(this, SimpleMainActivity::class.java))
            finish()
            return
        }
        
        android.util.Log.d(TAG, "Network available: ${isNetworkAvailable()}")
        
        // Process any pending signups from queue
        processPendingSignups()
        
        setContent {
            com.example.modicanalyzer.ui.theme.ModicAnalyzerTheme(darkTheme = false, dynamicColor = false) {
                LoginScreen(
                    viewModel = authViewModel,
                    onLoginSuccess = {
                        startActivity(Intent(this@LoginActivity, SimpleMainActivity::class.java))
                        finish()
                    },
                    onNavigateToSignup = {
                        startActivity(Intent(this@LoginActivity, SignupActivity::class.java))
                    },
                    authRepository = authRepository,
                    onMySQLLogin = { email, password, onComplete ->
                        handleLogin(email, password, onComplete)
                    }
                )
            }
        }
    }
    
    /**
     * Handle MySQL login
     */
    private fun handleLogin(email: String, password: String, onComplete: () -> Unit) {
        lifecycleScope.launch {
            try {
                android.util.Log.d(TAG, "🔐 Attempting MySQL login: $email")
                
                val result = authRepository.login(email, password)
                
                result.onSuccess { authResponse ->
                    android.util.Log.d(TAG, "✅ Login successful: ${authResponse.email}")
                    android.util.Log.d(TAG, "User UID: ${authResponse.uid}")
                    
                    onComplete() // Reset loading state
                    
                    // Successfully logged in, navigate to main
                    startActivity(Intent(this@LoginActivity, SimpleMainActivity::class.java))
                    finish()
                    
                }.onFailure { e ->
                    android.util.Log.e(TAG, "❌ Login failed: ${e.message}")
                    
                    onComplete() // Reset loading state
                    
                    Toast.makeText(
                        this@LoginActivity,
                        "Login failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Login error", e)
                
                onComplete() // Reset loading state
                
                Toast.makeText(
                    this@LoginActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
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
                
                android.util.Log.i(TAG, "🚀 Processing ${pendingSignups.size} pending signup(s) from queue...")
                
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
            android.util.Log.d(TAG, "🔐 Decrypting password for ${signup.email}...")
            val decryptedPassword = PendingSignupEntity.decryptPassword(signup.passwordHash)
            android.util.Log.i(TAG, "✅ Password decrypted")
            
            // Create Firebase Auth account
            android.util.Log.d(TAG, "🔥 Creating Firebase Auth account for ${signup.email}...")
            firebaseAuth.createUserWithEmailAndPassword(signup.email, decryptedPassword)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    if (user != null) {
                        android.util.Log.i(TAG, "✅ Firebase Auth account created! UID: ${user.uid}")
                        
                        // Update Firebase profile
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(signup.fullName)
                            .build()
                        
                        user.updateProfile(profileUpdates)
                            .addOnSuccessListener {
                                android.util.Log.i(TAG, "✅ Firebase profile updated with name")
                                
                                // Create Firestore profile
                                lifecycleScope.launch {
                                    android.util.Log.d(TAG, "📄 Creating Firestore profile...")
                                    val result = firestoreHelper.createOrUpdateUserProfile(
                                        userId = user.uid,
                                        name = signup.fullName,
                                        email = signup.email,
                                        role = signup.role.lowercase(),
                                        profileImageUrl = null
                                    )
                                    
                                    if (result.isSuccess) {
                                        android.util.Log.i(TAG, "🎉 QUEUED SIGNUP COMPLETED: ${signup.email}")
                                        pendingSignupDao.markAsCompleted(signup.id)
                                        
                                        runOnUiThread {
                                            Toast.makeText(
                                                this@LoginActivity,
                                                "✅ Account created: ${signup.fullName}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } else {
                                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                                        android.util.Log.e(TAG, "❌ Firestore profile creation failed: $error")
                                        pendingSignupDao.markAsFailed(signup.id, "Firestore error: $error")
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e(TAG, "❌ Profile update failed: ${e.message}")
                                lifecycleScope.launch {
                                    pendingSignupDao.markAsFailed(signup.id, "Profile update failed")
                                }
                            }
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e(TAG, "❌ Firebase Auth creation failed: ${e.message}")
                    
                    lifecycleScope.launch {
                        val errorMsg = if (e.message?.contains("email address is already in use") == true) {
                            android.util.Log.w(TAG, "⚠️ Email already exists, marking as completed: ${signup.email}")
                            // Email already exists - consider it completed
                            pendingSignupDao.markAsCompleted(signup.id)
                            return@launch
                        } else {
                            e.message ?: "Unknown error"
                        }
                        
                        pendingSignupDao.markAsFailed(signup.id, errorMsg)
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Exception processing queued signup: ${e.message}", e)
            pendingSignupDao.markAsFailed(signup.id, e.message ?: "Unknown error")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit,
    authRepository: MySQLAuthRepository,
    onMySQLLogin: (String, String, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    // Note: We're using MySQL authentication now, so Firebase-specific features
    // like email verification and password reset are disabled

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            // App Name/Header Section
            Text(
                text = "SpinoCare",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Advanced Spinal Health Analysis",
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 48.dp)
            )
            
            // Login Form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Sign In",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        label = { Text("Email") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = "Email")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = "Password")
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    if (isPasswordVisible) Icons.Default.Done else Icons.Default.Clear,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Login Button
                    Button(
                        onClick = {
                            if (email.isBlank()) {
                                Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (password.isBlank()) {
                                Toast.makeText(context, "Please enter your password", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            // Use MySQL authentication with loading callback
                            isLoading = true
                            onMySQLLogin(email, password) {
                                // Reset loading state when login completes (success or error)
                                isLoading = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.example.modicanalyzer.ui.theme.ModicarePrimary
                        ),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = "Sign In",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Signup Link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Don't have an account? ",
                            color = Color.Gray
                        )
                        TextButton(
                            onClick = onNavigateToSignup,
                            enabled = !isLoading
                        ) {
                            Text(
                                text = "Sign Up",
                                color = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ForgotPasswordDialog(
    onDismiss: () -> Unit,
    onSendEmail: (String) -> Unit,
    isLoading: Boolean
) {
    var resetEmail by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                text = "Reset Password",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter your email address and we'll send you a link to reset your password.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = { resetEmail = it },
                    label = { Text("Email") },
                    placeholder = { Text("your@email.com") },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = "Email")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (resetEmail.isNotBlank()) {
                        onSendEmail(resetEmail)
                    }
                },
                enabled = !isLoading && resetEmail.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Send Reset Link")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EmailVerificationDialog(
    onDismiss: () -> Unit,
    onResendEmail: () -> Unit,
    onCheckVerification: () -> Unit,
    isLoading: Boolean
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        icon = {
            Icon(
                Icons.Default.Email,
                contentDescription = "Email Verification",
                tint = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "Email Verification Required",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your email is not verified yet. Please check your inbox and click the verification link.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    text = "Didn't receive the email?",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Button(
                    onClick = onResendEmail,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resend Verification Email")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onCheckVerification,
                enabled = !isLoading
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("I've Verified")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel Login")
            }
        }
    )
}

/**
 * MySQL Login Screen - Simple login UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySQLLoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo/Title
        Text(
            text = "SpinoCare",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = com.example.modicanalyzer.ui.theme.ModicarePrimary
        )
        
        Text(
            text = "MRI Analysis Platform",
            fontSize = 16.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 48.dp)
        )
        
        // Email field
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Password field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Login button
        Button(
            onClick = {
                if (email.isNotBlank() && password.isNotBlank()) {
                    onLogin(email, password)
                }
            },
            enabled = email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = com.example.modicanalyzer.ui.theme.ModicarePrimary
            )
        ) {
            Text("Login", modifier = Modifier.padding(8.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Signup link
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Don't have an account? ")
            TextButton(onClick = onNavigateToSignup) {
                Text(
                    "Sign Up",
                    color = com.example.modicanalyzer.ui.theme.ModicarePrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
