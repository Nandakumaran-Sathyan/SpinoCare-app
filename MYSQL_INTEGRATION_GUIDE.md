# MySQL Integration Guide for SpinoCare Android App

## 📋 Overview

This document describes how to integrate the MySQL REST API with your SpinoCare Android application. The integration is already partially complete for analysis saving.

## ✅ Completed Integration

### 1. Network Layer Setup
- ✅ Retrofit dependencies added to `build.gradle.kts`
- ✅ Data models created in `data/api/ApiModels.kt`
- ✅ API service interface created in `data/api/SpinoCareApiService.kt`
- ✅ Hilt DI module created in `di/NetworkModule.kt`
- ✅ Repository layer created in `data/repository/MySQLRepository.kt`

### 2. Analysis Saving
- ✅ `MySQLRepository` injected into `SimpleMainActivity`
- ✅ Analysis save logic updated to use MySQL API
- ✅ Backward compatibility maintained with Firestore

## 🔧 Required Integration Steps

### Step 1: Update User Registration (SignupActivity.kt)

**Location:** `app/src/main/java/com/example/modicanalyzer/SignupActivity.kt`

**Current Code (Lines ~154):**
```kotlin
val result = firestoreHelper.createOrUpdateUserProfile(
    userId = user.uid,
    name = name,
    email = email,
    role = role,
    profileImageUrl = null
)
```

**Updated Code:**
```kotlin
// First register in MySQL
lifecycleScope.launch {
    val mysqlResult = mySQLRepository.registerUser(
        firebaseUid = user.uid,
        email = email,
        displayName = name,
        phoneNumber = phone // If available
    )
    
    mysqlResult.onSuccess { userData ->
        android.util.Log.d(TAG, "✅ User registered in MySQL: ${userData.userId}")
        
        // Also save to Firestore for backward compatibility
        val result = firestoreHelper.createOrUpdateUserProfile(
            userId = user.uid,
            name = name,
            email = email,
            role = role,
            profileImageUrl = null
        )
        
        if (result.isSuccess) {
            android.util.Log.i(TAG, "🎉 User profile created in both databases")
            // Continue with success flow
        }
    }.onFailure { e ->
        android.util.Log.e(TAG, "❌ MySQL registration failed: ${e.message}")
        // Handle error
    }
}
```

**Required Changes:**
1. Add `@javax.inject.Inject lateinit var mySQLRepository: MySQLRepository` to SignupActivity
2. Update all instances of `createOrUpdateUserProfile` to include MySQL registration

### Step 2: Update User Login Sync (LoginActivity.kt)

**Location:** `app/src/main/java/com/example/modicanalyzer/LoginActivity.kt`

When processing queued signups after network is restored (Lines ~157):

```kotlin
lifecycleScope.launch {
    // First register in MySQL
    val mysqlResult = mySQLRepository.registerUser(
        firebaseUid = user.uid,
        email = signup.email,
        displayName = signup.fullName,
        phoneNumber = signup.phoneNumber
    )
    
    mysqlResult.onSuccess { userData ->
        android.util.Log.d(TAG, "✅ Queued user registered in MySQL")
        
        // Then create Firestore profile
        val result = firestoreHelper.createOrUpdateUserProfile(
            userId = user.uid,
            name = signup.fullName,
            email = signup.email,
            role = signup.role.lowercase(),
            profileImageUrl = null
        )
        
        if (result.isSuccess) {
            pendingSignupDao.markAsCompleted(signup.id)
        }
    }
}
```

**Required Changes:**
1. Add `@javax.inject.Inject lateinit var mySQLRepository: MySQLRepository` to LoginActivity
2. Update queued signup processing to register in MySQL first

### Step 3: Configure BASE_URL for Different Environments

**Location:** `app/src/main/java/com/example/modicanalyzer/di/NetworkModule.kt`

**Current Configuration:**
```kotlin
private const val BASE_URL = "http://10.0.2.2/spinocare-api/"
```

**Environment-Specific URLs:**
- **Emulator:** `http://10.0.2.2/spinocare-api/` (already configured)
- **Physical Device (local):** `http://192.168.x.x/spinocare-api/` (replace with your PC's local IP)
- **Production:** `https://yourdomain.com/api/` (when deployed)

**Recommended Approach:**
```kotlin
object NetworkModule {
    private const val BASE_URL = BuildConfig.API_BASE_URL
    // Define in build.gradle.kts:
    // buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2/spinocare-api/\"")
}
```

### Step 4: Update Image Upload Worker

**Location:** `app/src/main/java/com/example/modicanalyzer/worker/ImageUploadWorker.kt`

After images are uploaded to Firebase Storage, update MySQL instead of Firestore:

**Find this code (Line ~113):**
```kotlin
firestoreHelper.updateAnalysisImageUrls(
    analysisId = upload.analysisId,
    t1ImageUrl = t1Url,
    t2ImageUrl = t2Url
)
```

**Replace with:**
```kotlin
// Update MySQL database with image URLs
// Note: You'll need to add an update endpoint to your PHP API
// For now, you can re-save the analysis with updated URLs
```

**Required Changes:**
1. Add update endpoint to PHP API (`PUT /analysis/{entry_id}`)
2. Add update method to `SpinoCareApiService.kt`
3. Add update method to `MySQLRepository.kt`
4. Update worker to use MySQL update method

### Step 5: Load Analysis History from MySQL

**Future Enhancement:** Create a new screen or update existing screens to load past analyses from MySQL.

**Example Code:**
```kotlin
lifecycleScope.launch {
    val firebaseUid = firebaseAuth.currentUser?.uid ?: return@launch
    
    val result = mySQLRepository.getAnalysisList(
        firebaseUid = firebaseUid,
        limit = 20,
        offset = 0
    )
    
    result.onSuccess { listData ->
        val analyses = listData.analyses
        // Display in UI
        analyses.forEach { entry ->
            // entry.t1ImageUrl, entry.t2ImageUrl, entry.analysisResult, etc.
        }
    }.onFailure { e ->
        android.util.Log.e(TAG, "Failed to load analyses: ${e.message}")
    }
}
```

## 🧪 Testing Checklist

### Backend Testing
- ✅ XAMPP running (Apache + MySQL)
- ✅ API endpoints working (`http://localhost/spinocare-api/test.html`)
- ✅ Database has sample data
- ✅ All CRUD operations tested

### Android App Testing
- [ ] Register new user → Check MySQL `users` table
- [ ] Perform analysis → Check MySQL `mri_analysis_entries` table
- [ ] View analysis history → Load from MySQL API
- [ ] Delete analysis → Remove from MySQL
- [ ] Test on emulator (BASE_URL: `http://10.0.2.2/...`)
- [ ] Test on physical device (BASE_URL: `http://192.168.x.x/...`)

## 📊 API Endpoint Summary

| Endpoint | Method | Purpose | Android Usage |
|----------|--------|---------|---------------|
| `/users/register` | POST | Register/update user | After Firebase Auth signup |
| `/users/{firebase_uid}` | GET | Get user data | Load user profile |
| `/analysis/save` | POST | Save analysis entry | After successful analysis |
| `/analysis/list/{firebase_uid}` | GET | List all analyses | Analysis history screen |
| `/analysis/{entry_id}` | GET | Get single analysis | View details |
| `/analysis/{entry_id}` | DELETE | Delete analysis | Delete operation |

## 🔐 Security Considerations

### Current Setup (Development)
- No authentication on API endpoints
- XAMPP accessible only on localhost
- Suitable for development testing

### Production Recommendations
1. **Add Firebase Auth Token Validation**
   ```php
   // In config.php
   function validateFirebaseToken($token) {
       // Use Firebase Admin SDK to verify token
       // Return user UID if valid
   }
   ```

2. **HTTPS Only**
   - Use SSL certificate for production
   - Update BASE_URL to `https://`

3. **Rate Limiting**
   - Limit requests per user/IP
   - Prevent API abuse

4. **Input Validation**
   - Already implemented in API
   - Additional validation on Android side

## 🚀 Deployment Checklist

### Production Deployment
- [ ] Deploy PHP API to web hosting (e.g., shared hosting, VPS, AWS)
- [ ] Create production MySQL database
- [ ] Update connection credentials in `config.php`
- [ ] Add SSL certificate
- [ ] Update Android app BASE_URL to production URL
- [ ] Add Firebase Auth token validation to API
- [ ] Set up database backups
- [ ] Add error monitoring (e.g., Sentry)
- [ ] Load test API endpoints
- [ ] Update Play Store with new version

## 📝 Migration Notes

### Existing Data Migration
If you have existing data in Firestore:

1. **Export Firestore Data**
   ```javascript
   // Use Firebase Admin SDK or Cloud Functions
   const users = await firestore.collection('users').get();
   const analyses = await firestore.collection('mri_analyses').get();
   ```

2. **Import to MySQL**
   ```php
   // Create migration script in /api/migrate.php
   // Read exported JSON
   // Insert into MySQL tables
   ```

3. **Dual Write Period**
   - Keep writing to both Firestore and MySQL
   - Verify data consistency
   - Eventually remove Firestore writes

### Current Integration Status
- ✅ Analysis saving: Writes to both MySQL (primary) and Firestore (backup)
- ⏳ User registration: Still writes to Firestore only
- ⏳ Analysis loading: Not yet implemented

## 🛠️ Troubleshooting

### Common Issues

**1. Network Error: Unable to resolve host**
- **Cause:** BASE_URL incorrect for environment
- **Fix:** Use `10.0.2.2` for emulator, local IP for physical device

**2. HTTP 404 Not Found**
- **Cause:** Routing issue or wrong endpoint path
- **Fix:** Ensure query parameter routing: `?path=endpoint/action`

**3. HTTP 500 Internal Server Error**
- **Cause:** PHP error or database connection failed
- **Fix:** Check Apache error logs: `C:\xampp-2\apache\logs\error.log`

**4. Empty Response or Timeout**
- **Cause:** Apache not running or firewall blocking
- **Fix:** Start XAMPP, check Windows Firewall settings

**5. User Not Found Error**
- **Cause:** User not registered in MySQL yet
- **Fix:** Ensure user registration completes Step 1 above

## 📞 Next Steps

1. **Complete User Registration Integration** (Step 1 & 2 above)
2. **Test End-to-End Flow** (Signup → Login → Analysis → View History)
3. **Add Analysis History Screen** (Optional)
4. **Add Image Update Endpoint** (Step 4 above)
5. **Deploy to Production** (When ready)

## 📚 Additional Resources

- **PHP REST API Code:** `C:\xampp-2\htdocs\spinocare-api\`
- **Test Console:** `http://localhost/spinocare-api/test.html`
- **phpMyAdmin:** `http://localhost/phpmyadmin`
- **Database Schema:** `database/spinocare_schema.sql`

---

**Status:** Analysis saving integrated ✅ | User registration pending ⏳ | History loading pending ⏳
