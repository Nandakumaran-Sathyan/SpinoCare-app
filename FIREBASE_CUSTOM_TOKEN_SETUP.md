# Firebase Custom Token Authentication Setup

This guide explains how to set up Firebase custom token authentication to enable MySQL-authenticated users to upload images to Firebase Storage.

## 🎯 How It Works

```
User Login Flow:
1. User logs in with email/password → MySQL Auth
2. Backend validates credentials → Returns MySQL token
3. App requests Firebase custom token from backend (using MySQL token)
4. Backend generates Firebase custom token (using service account)
5. App signs into Firebase with custom token
6. User can now upload to Firebase Storage ✅
```

## 📋 Setup Steps

### Step 1: Download Firebase Service Account

1. Go to **Firebase Console** → Your Project
2. Click ⚙️ **Project Settings** → **Service Accounts** tab
3. Click **Generate New Private Key**
4. Download the JSON file
5. **IMPORTANT**: Keep this file secure! Never commit to Git!

### Step 2: Upload Service Account to Server

**For XAMPP (Local):**
```bash
# Copy the downloaded JSON file to your api folder
# Rename it to: firebase-service-account.json
# Place it at: C:\xampp\htdocs\spinocare-api\firebase-service-account.json
```

**For InfinityFree (Hosted):**
1. Go to File Manager
2. Navigate to `htdocs/`
3. Upload `firebase-service-account.json`
4. Make sure it's in the same folder as `index.php`

### Step 3: Update Firebase Storage Rules

Go to Firebase Console → Storage → Rules:

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /mri_images/{userId}/{imageId} {
      // Allow authenticated users to upload
      allow write: if request.auth != null && request.resource.size < 10 * 1024 * 1024;
      
      // Allow anyone to read
      allow read: if true;
    }
  }
}
```

Click **Publish**.

### Step 4: Test the Flow

1. **Build and run the Android app**
2. **Register a new user** (or login with existing)
3. **Upload an image** in the Analysis screen
4. **Check logs** for:
   - `✅ Firebase authentication successful`
   - `✅ Image uploaded successfully`

## 🔒 Security Notes

### ⚠️ Never Commit Service Account JSON

Add to `.gitignore`:
```
# Firebase service account (NEVER COMMIT)
firebase-service-account.json
api/firebase-service-account.json
```

### 🔐 Production Recommendations

1. **Store service account securely**:
   - Use environment variables
   - Use secret management service (AWS Secrets Manager, etc.)
   - Never hardcode in code

2. **Validate requests**:
   - Always verify MySQL token before generating Firebase token
   - Check token expiration
   - Rate limit token generation endpoint

3. **Monitor usage**:
   - Log all Firebase token requests
   - Set up alerts for unusual activity
   - Review Firebase Storage usage regularly

## 🐛 Troubleshooting

### Error: "Service account file not found"
**Solution**: Make sure `firebase-service-account.json` is in the same directory as `index.php`

### Error: "Invalid service account JSON"
**Solution**: Re-download the service account key from Firebase Console

### Error: "Permission denied" when uploading
**Solution**: 
1. Check Firebase Storage rules are updated
2. Make sure Firebase custom token was generated successfully
3. Check logs for `✅ Firebase authentication successful`

### Error: "Token generation failed"
**Solution**:
1. Verify service account JSON is valid
2. Check `openssl` is enabled in PHP
3. Verify private key format in service account

## 📝 API Endpoint

### GET /firebase/token

**Request:**
```http
GET /spinocare-api/index.php?path=firebase/token HTTP/1.1
Authorization: Bearer {mysql_auth_token}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "firebase_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expires_in": 3600
  }
}
```

## 🔄 Flow Diagram

```
┌─────────────┐
│ Android App │
└──────┬──────┘
       │ 1. Login (email/password)
       ▼
┌──────────────────┐
│  MySQL Backend   │
│  (auth.php)      │
└──────┬───────────┘
       │ 2. Returns MySQL token
       ▼
┌─────────────┐
│ Android App │
└──────┬──────┘
       │ 3. Request Firebase token (with MySQL token)
       ▼
┌──────────────────┐
│  MySQL Backend   │
│  (firebase_auth  │
│   .php)          │
└──────┬───────────┘
       │ 4. Generates Firebase custom token
       │    (using service account)
       ▼
┌─────────────┐
│ Android App │
└──────┬──────┘
       │ 5. Sign into Firebase with custom token
       ▼
┌──────────────────┐
│ Firebase Auth    │
└──────┬───────────┘
       │ 6. User authenticated
       ▼
┌──────────────────┐
│ Firebase Storage │
│  ✅ Upload OK     │
└──────────────────┘
```

## ✅ Verification Checklist

- [ ] Service account JSON downloaded from Firebase
- [ ] Service account file uploaded to server
- [ ] firebase_auth.php file created
- [ ] Route added to index.php
- [ ] Firebase Storage rules updated
- [ ] Android app rebuilt
- [ ] Test registration/login works
- [ ] Test image upload succeeds
- [ ] Firebase custom token logs show success
- [ ] Images appear in Firebase Storage console

## 🎉 Success Indicators

When everything is working, you should see these logs:

```
✅ User registered: user@example.com
✅ Signed into Firebase with custom token
✅ Image uploaded successfully: https://firebasestorage...
✅ Analysis saved to MySQL: entry_xxxxx
```
