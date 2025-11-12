# MySQL Authentication Migration Guide

## 🎯 Overview

This guide explains how to migrate from Firebase Authentication to MySQL-based authentication for SpinoCare.

## ✅ Completed Backend Setup

### 1. Database Changes
**Action Required:** Run this SQL in phpMyAdmin:
```sql
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255) NULL AFTER phone_number;
CREATE INDEX idx_email ON users(email);
```

### 2. API Files Created
- ✅ `auth.php` - Authentication endpoints (register, login)
- ✅ Updated `index.php` - Added auth routing
- ✅ JWT-like token generation and verification

### 3. Android Files Created
- ✅ `ApiModels.kt` - Added `RegisterRequest`, `LoginRequest`, `AuthResponse`
- ✅ `SpinoCareApiService.kt` - Added `register()` and `login()` endpoints
- ✅ `MySQLAuthRepository.kt` - Full auth repository with session management

## 🔧 Required Android Changes

### Step 1: Update Login/Signup Activities

The main changes need to happen in:
- `LoginActivity.kt`
- `SignupActivity.kt`

**Replace Firebase Auth calls with MySQL Auth:**

**OLD (Firebase):**
```kotlin
firebaseAuth.signInWithEmailAndPassword(email, password)
    .addOnSuccessListener { authResult ->
        val user = authResult.user
        // Handle success
    }
```

**NEW (MySQL):**
```kotlin
@Inject lateinit var authRepository: MySQLAuthRepository

lifecycleScope.launch {
    val result = authRepository.login(email, password)
    result.onSuccess { authResponse ->
        // authResponse.uid, authResponse.token, authResponse.displayName
        // Navigate to main screen
    }.onFailure { e ->
        // Handle error: e.message
    }
}
```

### Step 2: Update SimpleMainActivity

**Replace Firebase Auth user checks:**

**OLD:**
```kotlin
val firebaseUser = firebaseAuth.currentUser
val userId = firebaseUser?.uid
```

**NEW:**
```kotlin
@Inject lateinit var authRepository: MySQLAuthRepository

val userId = authRepository.getCurrentUserUid()
val userEmail = authRepository.getCurrentUserEmail()
val displayName = authRepository.getCurrentUserDisplayName()
```

### Step 3: Update Analysis Save Logic

Already partially done! Just ensure user ID comes from MySQL:

```kotlin
val userId = authRepository.getCurrentUserUid() ?: run {
    Toast.makeText(context, "Please login first", Toast.LENGTH_SHORT).show()
    return@launch
}
```

### Step 4: Remove Firebase Dependencies (Optional)

Once fully migrated, you can remove:
```kotlin
// build.gradle.kts
// implementation("com.google.firebase:firebase-auth")
```

Keep Firebase Storage if still using it for images.

## 📝 Implementation Example

### Complete LoginActivity Example

```kotlin
@AndroidEntryPoint
class LoginActivity : ComponentActivity() {
    
    @Inject
    lateinit var authRepository: MySQLAuthRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if already logged in
        if (authRepository.isLoggedIn()) {
            navigateToMain()
            return
        }
        
        setContent {
            LoginScreen(
                onLogin = { email, password ->
                    handleLogin(email, password)
                },
                onNavigateToSignup = {
                    startActivity(Intent(this, SignupActivity::class.java))
                }
            )
        }
    }
    
    private fun handleLogin(email: String, password: String) {
        lifecycleScope.launch {
            try {
                val result = authRepository.login(email, password)
                
                result.onSuccess { authResponse ->
                    Log.d(TAG, "✅ Login successful: ${authResponse.email}")
                    Toast.makeText(
                        this@LoginActivity,
                        "Welcome ${authResponse.displayName}!",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToMain()
                }.onFailure { e ->
                    Log.e(TAG, "❌ Login failed: ${e.message}")
                    Toast.makeText(
                        this@LoginActivity,
                        "Login failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@LoginActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun navigateToMain() {
        startActivity(Intent(this, SimpleMainActivity::class.java))
        finish()
    }
}
```

### Complete SignupActivity Example

```kotlin
@AndroidEntryPoint
class SignupActivity : ComponentActivity() {
    
    @Inject
    lateinit var authRepository: MySQLAuthRepository
    
    private fun handleSignup(
        name: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String
    ) {
        // Validation
        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords don't match", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                val result = authRepository.register(
                    email = email,
                    password = password,
                    displayName = name,
                    phoneNumber = phone.takeIf { it.isNotBlank() }
                )
                
                result.onSuccess { authResponse ->
                    Log.d(TAG, "✅ Registration successful: ${authResponse.email}")
                    Toast.makeText(
                        this@SignupActivity,
                        "Account created successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToMain()
                }.onFailure { e ->
                    Log.e(TAG, "❌ Registration failed: ${e.message}")
                    Toast.makeText(
                        this@SignupActivity,
                        "Registration failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SignupActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun navigateToMain() {
        startActivity(Intent(this, SimpleMainActivity::class.java))
        finish()
    }
}
```

## 🧪 Testing

### Test Registration
```bash
curl -X POST http://localhost/spinocare-api/index.php?path=auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123",
    "display_name": "Test User",
    "phone_number": "+1234567890"
  }'
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "user_id": 1,
    "uid": "user_abc123...",
    "email": "test@example.com",
    "display_name": "Test User",
    "phone_number": "+1234567890",
    "token": "eyJ0eXAiOiJKV1QiLCJhbGc...",
    "message": "User registered successfully"
  }
}
```

### Test Login
```bash
curl -X POST http://localhost/spinocare-api/index.php?path=auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123"
  }'
```

## 🔐 Security Notes

### Current Implementation
- ✅ Passwords hashed with BCrypt
- ✅ JWT-like token generation
- ✅ Token expiry (7 days)
- ⚠️ Basic token validation (no signature verification yet)

### Production Recommendations
1. **Use proper JWT library** (e.g., `firebase/php-jwt`)
2. **HTTPS only** - Never send passwords over HTTP in production
3. **Add rate limiting** - Prevent brute force attacks
4. **Add password reset** - Email-based password recovery
5. **Add 2FA** (optional) - Two-factor authentication
6. **Store secret key securely** - Use environment variables

## 📊 Migration Checklist

- [ ] Run SQL to add `password_hash` column
- [ ] Copy `auth.php` to XAMPP htdocs
- [ ] Test registration endpoint
- [ ] Test login endpoint
- [ ] Update `LoginActivity` to use `MySQLAuthRepository`
- [ ] Update `SignupActivity` to use `MySQLAuthRepository`
- [ ] Update `SimpleMainActivity` to get user from `MySQLAuthRepository`
- [ ] Remove Firebase Auth initialization (optional)
- [ ] Test complete flow (signup → login → analysis)

## 🚀 Next Steps

1. **Complete the Android integration** using examples above
2. **Test thoroughly** with emulator
3. **Add password reset feature** (future enhancement)
4. **Deploy to production** with HTTPS

## 📞 Troubleshooting

**Error: "Email already registered"**
- User exists in database
- Use login instead or check email

**Error: "Invalid email or password"**
- Check credentials
- Verify password is correct
- Check if user is active (`is_active = 1`)

**Error: Connection timeout**
- Verify XAMPP is running
- Check BASE_URL in NetworkModule.kt
- Ensure BASE_URL matches your local IP

**Token not working**
- Check token expiry
- Verify secret key matches between registration and verification
- Token expires after 7 days by default

---

**Status:** Backend complete ✅ | Android integration pending ⏳
