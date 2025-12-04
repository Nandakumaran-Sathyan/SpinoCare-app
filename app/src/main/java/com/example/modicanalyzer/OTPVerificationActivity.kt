package com.example.modicanalyzer

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.modicanalyzer.data.repository.MySQLAuthRepository
import com.example.modicanalyzer.ui.theme.ModicAnalyzerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OTPVerificationActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "OTPVerificationActivity"
        const val EXTRA_EMAIL = "email"
        const val EXTRA_PASSWORD_HASH = "password_hash"
        const val EXTRA_DISPLAY_NAME = "display_name"
        const val EXTRA_PHONE_NUMBER = "phone_number"
    }
    
    @Inject
    lateinit var authRepository: MySQLAuthRepository
    
    private lateinit var email: String
    private lateinit var passwordHash: String
    private var displayName: String? = null
    private var phoneNumber: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get registration data from intent
        email = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        passwordHash = intent.getStringExtra(EXTRA_PASSWORD_HASH) ?: ""
        displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
        phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER)
        
        if (email.isBlank() || passwordHash.isBlank()) {
            Toast.makeText(this, "Invalid registration data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        android.util.Log.d(TAG, "=== OTP Verification for: $email ===")
        
        setContent {
            ModicAnalyzerTheme(darkTheme = false, dynamicColor = false) {
                OTPVerificationScreen(
                    email = email,
                    onVerify = { otp, onComplete ->
                        verifyOTP(otp, onComplete)
                    },
                    onResendOTP = {
                        resendOTP()
                    },
                    onBack = {
                        finish()
                    }
                )
            }
        }
    }
    
    private fun verifyOTP(otp: String, onComplete: () -> Unit) {
        lifecycleScope.launch {
            try {
                android.util.Log.d(TAG, "🔐 Verifying OTP: $otp for $email")
                
                val result = authRepository.verifyEmail(
                    email = email,
                    otp = otp,
                    passwordHash = passwordHash,
                    displayName = displayName,
                    phoneNumber = phoneNumber
                )
                
                result.onSuccess { authResponse ->
                    android.util.Log.d(TAG, "✅ OTP Verified! User created: ${authResponse.uid}")
                    
                    Toast.makeText(
                        this@OTPVerificationActivity,
                        "✅ Email verified! Welcome aboard!",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    onComplete()
                    
                    // Navigate to main activity
                    startActivity(Intent(this@OTPVerificationActivity, SimpleMainActivity::class.java))
                    finish()
                    
                }.onFailure { e ->
                    android.util.Log.e(TAG, "❌ OTP verification failed: ${e.message}")
                    
                    onComplete()
                    
                    Toast.makeText(
                        this@OTPVerificationActivity,
                        "Invalid or expired OTP: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ OTP verification error", e)
                
                onComplete()
                
                Toast.makeText(
                    this@OTPVerificationActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun resendOTP() {
        Toast.makeText(this, "Resending OTP...", Toast.LENGTH_SHORT).show()
        // TODO: Implement resend OTP API call
        android.util.Log.d(TAG, "📧 Resend OTP requested for $email")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OTPVerificationScreen(
    email: String,
    onVerify: (String, () -> Unit) -> Unit,
    onResendOTP: () -> Unit,
    onBack: () -> Unit
) {
    var otp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableStateOf(600) } // 10 minutes in seconds
    var canResend by remember { mutableStateOf(false) }
    
    // Countdown timer
    LaunchedEffect(Unit) {
        val timer = object : CountDownTimer(600000, 1000) { // 10 minutes
            override fun onTick(millisUntilFinished: Long) {
                timeLeft = (millisUntilFinished / 1000).toInt()
            }
            
            override fun onFinish() {
                canResend = true
                timeLeft = 0
            }
        }
        timer.start()
    }
    
    val minutes = timeLeft / 60
    val seconds = timeLeft % 60
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify Email") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Icon
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = "Email",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Verify Your Email",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "We've sent a 6-digit code to",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = email,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // OTP Input
            OutlinedTextField(
                value = otp,
                onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) otp = it },
                label = { Text("Enter OTP") },
                placeholder = { Text("000000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                singleLine = true,
                enabled = !isLoading
            )
            
            // Timer
            Text(
                text = if (timeLeft > 0) 
                    "Code expires in %02d:%02d".format(minutes, seconds)
                else 
                    "Code expired",
                fontSize = 14.sp,
                color = if (timeLeft > 0) MaterialTheme.colorScheme.onSurfaceVariant 
                        else MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Verify Button
            Button(
                onClick = {
                    if (otp.length == 6) {
                        isLoading = true
                        onVerify(otp) {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = otp.length == 6 && !isLoading && timeLeft > 0,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Verify Email", fontSize = 16.sp)
                }
            }
            
            // Resend Button
            TextButton(
                onClick = {
                    canResend = false
                    timeLeft = 600
                    otp = ""
                    onResendOTP()
                },
                enabled = canResend && !isLoading
            ) {
                Text(
                    text = if (canResend) "Resend OTP" else "Resend available after expiry",
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "💡 Didn't receive the code?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• Check your spam folder\n• Wait for the timer to expire and resend\n• Make sure the email is correct",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
