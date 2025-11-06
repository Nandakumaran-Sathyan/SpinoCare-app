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
    lateinit var pendingSignupDao: PendingSignupDao
    
    @Inject
    lateinit var firestoreHelper: FirestoreHelper
    
    @Inject
    lateinit var firebaseAuth: FirebaseAuth
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        android.util.Log.d(TAG, "=== LoginActivity onCreate ===")
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
    onNavigateToSignup: () -> Unit
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var showEmailVerificationDialog by remember { mutableStateOf(false) }
    
    // Collect auth state
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    
    // Handle auth state changes
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Success -> {
                // Check email verification for online users
                if (state.isFirebaseAuth && isOnline) {
                    val isVerified = viewModel.checkEmailVerified()
                    if (!isVerified) {
                        // Show verification dialog instead of logging in
                        showEmailVerificationDialog = true
                        Toast.makeText(
                            context,
                            "Please verify your email to continue",
                            Toast.LENGTH_LONG
                        ).show()
                        return@LaunchedEffect
                    }
                }
                
                Toast.makeText(
                    context,
                    "Login successful! ${if (state.isFirebaseAuth) "Online" else "Offline"} mode",
                    Toast.LENGTH_SHORT
                ).show()
                onLoginSuccess()
            }
            is AuthState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            }
            is AuthState.PasswordResetEmailSent -> {
                Toast.makeText(
                    context,
                    "Password reset email sent! Check your inbox.",
                    Toast.LENGTH_LONG
                ).show()
                showForgotPasswordDialog = false
                viewModel.resetAuthState()
            }
            is AuthState.EmailVerificationSent -> {
                Toast.makeText(
                    context,
                    "Verification email sent! Please check your inbox.",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.resetAuthState()
            }
            else -> {}
        }
    }
    
    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        ForgotPasswordDialog(
            onDismiss = { 
                showForgotPasswordDialog = false
                viewModel.resetAuthState()
            },
            onSendEmail = { resetEmail ->
                viewModel.sendPasswordResetEmail(resetEmail)
            },
            isLoading = authState is AuthState.Loading
        )
    }
    
    // Email Verification Dialog
    if (showEmailVerificationDialog) {
        EmailVerificationDialog(
            onDismiss = {
                showEmailVerificationDialog = false
                viewModel.signOut() // Sign them out until verified
                viewModel.resetAuthState()
            },
            onResendEmail = {
                viewModel.sendEmailVerification()
            },
            onCheckVerification = {
                viewModel.reloadUser()
                // Re-check verification status
                kotlinx.coroutines.GlobalScope.launch {
                    kotlinx.coroutines.delay(500) // Wait for reload
                    val isVerified = viewModel.checkEmailVerified()
                    if (isVerified) {
                        showEmailVerificationDialog = false
                        onLoginSuccess()
                    } else {
                        Toast.makeText(
                            context,
                            "Email not yet verified. Please check your inbox.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            isLoading = authState is AuthState.Loading
        )
    }
    
    val isLoading = authState is AuthState.Loading

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
            // Connection Status Indicator
            if (!isOnline) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Offline",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Offline Mode - Limited functionality",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
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
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Forgot Password
                    TextButton(
                        onClick = {
                            if (!isOnline) {
                                Toast.makeText(context, "Password reset requires internet connection", Toast.LENGTH_SHORT).show()
                            } else {
                                showForgotPasswordDialog = true
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        enabled = !isLoading
                    ) {
                        Text("Forgot Password?")
                    }
                    
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
                            
                            // Use AuthViewModel to login with proper validation
                            viewModel.login(email, password)
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
