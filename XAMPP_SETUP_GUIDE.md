# SpinoCare XAMPP Setup Guide

## 🎯 Goal
Migrate from Firebase Firestore to MySQL database while keeping:
- ✅ Firebase Authentication (for user login)
- ✅ Firebase Storage (for image URLs)
- ✅ MySQL (for user data and analysis entries)

---

## 📋 Step-by-Step Setup

### **Step 1: Start XAMPP**

1. Open **XAMPP Control Panel**
2. Click **Start** for:
   - ✅ **Apache** (for PHP API)
   - ✅ **MySQL** (for database)
3. Both should show green "Running" status

---

### **Step 2: Create Database**

1. Open browser: http://localhost/phpmyadmin
2. Click **"New"** in left sidebar
3. Database name: `spinocare_db`
4. Collation: `utf8mb4_unicode_ci`
5. Click **"Create"**

---

### **Step 3: Import Database Schema**

#### Method 1: Using phpMyAdmin
1. In phpMyAdmin, select `spinocare_db` from left sidebar
2. Click **"Import"** tab at top
3. Click **"Choose File"** button
4. Navigate to: `SpinoCare-app/database/spinocare_schema.sql`
5. Click **"Go"** at bottom
6. You should see: "3 tables successfully created"

#### Method 2: Using SQL Tab
1. In phpMyAdmin, select `spinocare_db`
2. Click **"SQL"** tab
3. Copy entire content from `database/spinocare_schema.sql`
4. Paste into the SQL box
5. Click **"Go"**

**Expected Tables:**
- ✅ `users` - User information linked to Firebase UID
- ✅ `mri_analysis_entries` - Analysis results with Firebase Storage URLs
- ✅ `analysis_statistics` - User statistics (auto-updated)

---

### **Step 4: Setup API Files**

1. **Copy API folder to XAMPP:**
   - From: `SpinoCare-app/api/`
   - To: `C:/xampp/htdocs/spinocare-api/`

   ```powershell
   # In PowerShell (run from SpinoCare-app directory)
   xcopy api C:\xampp\htdocs\spinocare-api\ /E /I /Y
   ```

2. **Verify files are copied:**
   - `C:/xampp/htdocs/spinocare-api/index.php`
   - `C:/xampp/htdocs/spinocare-api/config.php`
   - `C:/xampp/htdocs/spinocare-api/users.php`
   - `C:/xampp/htdocs/spinocare-api/analysis.php`

---

### **Step 5: Test API**

1. **Health Check:**
   Open browser: http://localhost/spinocare-api/
   
   Expected response:
   ```json
   {
     "success": true,
     "data": {
       "message": "SpinoCare API is running",
       "version": "1.0",
       "endpoints": [...]
     }
   }
   ```

2. **Test with Postman or browser:**

---

## 🧪 API Testing

### **Test 1: Register User**

**Endpoint:** `POST http://localhost/spinocare-api/users/register`

**Headers:**
```
Content-Type: application/json
```

**Body:**
```json
{
  "firebase_uid": "e13amxUYVAawo0esEr1GAxSOwFh1",
  "email": "test@example.com",
  "display_name": "Test User"
}
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "user_id": 1,
    "message": "User registered successfully"
  }
}
```

---

### **Test 2: Save Analysis**

**Endpoint:** `POST http://localhost/spinocare-api/analysis/save`

**Headers:**
```
Content-Type: application/json
```

**Body:**
```json
{
  "firebase_uid": "e13amxUYVAawo0esEr1GAxSOwFh1",
  "entry_id": "test_entry_001",
  "t1_image_url": "https://firebasestorage.googleapis.com/.../t1_abc123.jpg",
  "t2_image_url": "https://firebasestorage.googleapis.com/.../t2_def456.jpg",
  "analysis_result": "No Modic Changes",
  "confidence": 0.9970,
  "analysis_mode": "offline",
  "model_version": "v1.0",
  "processing_time_ms": 1245
}
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "analysis_id": 1,
    "entry_id": "test_entry_001",
    "message": "Analysis saved successfully"
  }
}
```

---

### **Test 3: Get User's Analyses**

**Endpoint:** `GET http://localhost/spinocare-api/analysis/list/e13amxUYVAawo0esEr1GAxSOwFh1`

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "analyses": [
      {
        "id": 1,
        "entry_id": "test_entry_001",
        "t1_image_url": "...",
        "t2_image_url": "...",
        "analysis_result": "No Modic Changes",
        "confidence": 0.9970,
        "created_at": "2025-11-12 10:30:00"
      }
    ],
    "total": 1,
    "limit": 10,
    "offset": 0
  }
}
```

---

### **Test 4: Get Specific Analysis**

**Endpoint:** `GET http://localhost/spinocare-api/analysis/test_entry_001`

---

### **Test 5: Delete Analysis**

**Endpoint:** `DELETE http://localhost/spinocare-api/analysis/test_entry_001`

---

## 📊 Database Structure

### **users** table
```sql
| Field         | Type          | Description                    |
|---------------|---------------|--------------------------------|
| id            | INT (PK)      | Auto-increment primary key     |
| firebase_uid  | VARCHAR(128)  | Firebase Auth UID (unique)     |
| email         | VARCHAR(255)  | User email                     |
| display_name  | VARCHAR(255)  | User display name              |
| phone_number  | VARCHAR(20)   | Phone number (optional)        |
| created_at    | TIMESTAMP     | Account creation date          |
| last_login    | TIMESTAMP     | Last login time                |
| is_active     | BOOLEAN       | Account status                 |
```

### **mri_analysis_entries** table
```sql
| Field              | Type          | Description                    |
|--------------------|---------------|--------------------------------|
| id                 | INT (PK)      | Auto-increment primary key     |
| entry_id           | VARCHAR(50)   | Unique entry identifier        |
| user_id            | INT (FK)      | References users(id)           |
| t1_image_url       | TEXT          | Firebase Storage URL (T1)      |
| t2_image_url       | TEXT          | Firebase Storage URL (T2)      |
| analysis_result    | VARCHAR(50)   | "Modic Change Detected" etc.   |
| confidence         | DECIMAL(5,4)  | 0.0000 - 1.0000               |
| analysis_mode      | VARCHAR(20)   | "online" or "offline"          |
| model_version      | VARCHAR(20)   | Model version used             |
| processing_time_ms | INT           | Processing time in ms          |
| created_at         | TIMESTAMP     | Analysis timestamp             |
```

---

## 🔧 Troubleshooting

### Issue: "Database connection failed"
**Solution:**
1. Check MySQL is running in XAMPP Control Panel
2. Verify database name is `spinocare_db`
3. Default credentials: user=`root`, password=`` (empty)
4. Check `api/config.php` has correct settings

---

### Issue: "404 Not Found" when accessing API
**Solution:**
1. Verify Apache is running in XAMPP
2. Check files are in `C:/xampp/htdocs/spinocare-api/`
3. Access: `http://localhost/spinocare-api/` (not `index.php`)

---

### Issue: "Table doesn't exist"
**Solution:**
1. Re-import `database/spinocare_schema.sql`
2. Check database name is exactly `spinocare_db`
3. Verify all 3 tables were created

---

### Issue: "CORS error" from Android app
**Solution:** Already handled in `config.php` with:
```php
header('Access-Control-Allow-Origin: *');
```

---

## 🔄 Migration Flow

### Current (Firebase Only):
```
Android App → Firebase Auth → Firestore DB + Storage
```

### New (Hybrid):
```
Android App → Firebase Auth (login) → MySQL (data) + Firebase Storage (images)
```

### What stays in Firebase:
- ✅ User authentication (email/password)
- ✅ Image storage (get URLs)

### What moves to MySQL:
- ✅ User details (email, name, etc.)
- ✅ Analysis entries
- ✅ Statistics

---

## 📱 Next Steps: Android App Integration

Once API is tested and working, you'll need to:

1. **Add Retrofit/OkHttp dependency** to Android app
2. **Create API service interface** for REST calls
3. **Update SimpleMainActivity** to call REST API instead of Firestore
4. **Keep Firebase Auth** - don't change login
5. **Keep Firebase Storage** - don't change image upload

I can help you with Android integration after you confirm the API is working!

---

## 🎯 Quick Start Commands

```powershell
# Copy API to XAMPP
xcopy api C:\xampp\htdocs\spinocare-api\ /E /I /Y

# Open phpMyAdmin
start http://localhost/phpmyadmin

# Test API
start http://localhost/spinocare-api/
```

---

## ✅ Checklist

- [ ] XAMPP installed and running (Apache + MySQL)
- [ ] Database `spinocare_db` created
- [ ] Schema imported (3 tables created)
- [ ] API files copied to `htdocs/spinocare-api/`
- [ ] Health check works: http://localhost/spinocare-api/
- [ ] Test user registration (Postman/browser)
- [ ] Test analysis save
- [ ] Test analysis retrieval
- [ ] Ready to integrate Android app!

---

**Need help with any step? Let me know!** 🚀
