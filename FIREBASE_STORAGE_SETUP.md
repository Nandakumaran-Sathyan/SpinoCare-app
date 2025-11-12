# Firebase Storage Rules Setup

## Issue
**Error**: `StorageException: Object does not exist at location. Code: -13010 HttpResult: 404`

**Cause**: Firebase Storage has restrictive default rules that block all uploads.

---

## Solution: Configure Storage Rules

### Step 1: Open Firebase Console
1. Go to https://console.firebase.google.com
2. Select your project: **Modic** (or `modic-d0149`)
3. Click **Storage** in left sidebar
4. Click **Rules** tab at the top

### Step 2: Update Rules
Replace the existing rules with:

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    // Allow authenticated users to upload to their own folder
    match /users/{userId}/{allPaths=**} {
      // Read: Only the owner can read their images
      allow read: if request.auth != null && request.auth.uid == userId;
      
      // Write: Only the owner can upload/delete their images
      allow write: if request.auth != null && request.auth.uid == userId
                   && request.resource.size < 10 * 1024 * 1024;  // Max 10MB per file
    }
  }
}
```

### Step 3: Publish Rules
1. Click **Publish** button (top right)
2. Confirm the update

---

## Verification

### Test Upload After Rule Update:
1. **Rebuild app** (optional, just to be safe)
2. **Login** to your app
3. **Select T1 and T2 images**
4. **Analyze**
5. Check logcat for:
   ```
   ✅ Upload complete: t1_xxxxx.jpg
   ✅ Upload complete: t2_xxxxx.jpg
   🎉 Analysis saved to cloud: xyz123
   ```

---

## Alternative Rules (More Permissive)

### Public Read Access
If you want anyone to view uploaded images (useful for sharing):

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /users/{userId}/images/{imageId} {
      // Anyone can read (download) images
      allow read: if true;
      
      // Only owner can upload/delete
      allow write: if request.auth != null 
                   && request.auth.uid == userId
                   && request.resource.size < 10 * 1024 * 1024;
    }
  }
}
```

---

## Security Best Practices

### Current Rules Include:
✅ **Authentication Required**: Only logged-in users can upload  
✅ **User Isolation**: Users can only access their own folders  
✅ **File Size Limit**: 10MB max per file (adjustable)  
✅ **Path Validation**: Enforces `/users/{userId}/` structure  

### Additional Security (Optional):
```javascript
match /users/{userId}/images/{imageId} {
  allow write: if request.auth != null 
               && request.auth.uid == userId
               && request.resource.size < 10 * 1024 * 1024
               && request.resource.contentType.matches('image/.*');  // Only images
}
```

---

## Troubleshooting

### Still Getting 404 After Rule Update?
1. **Wait 1-2 minutes** - Rules take time to propagate
2. **Check Firebase Console** - Go to Storage → Files, verify bucket exists
3. **Verify Authentication** - User must be logged in (`firebaseAuth.currentUser != null`)
4. **Check Storage Bucket** - In `google-services.json`, verify `storage_bucket` matches console

### Check Your Storage Bucket:
Look in `app/google-services.json`:
```json
{
  "storage_bucket": "modic-d0149.firebasestorage.app"
}
```

This should match the bucket shown in Firebase Console → Storage.

---

## Testing Checklist

- [ ] Firebase Console → Storage → Rules updated and published
- [ ] Wait 1-2 minutes for propagation
- [ ] User logged in (check `firebaseAuth.currentUser?.uid`)
- [ ] Network connection active
- [ ] Try upload again
- [ ] Check logcat for success messages
- [ ] Verify files in Firebase Console → Storage → Files → `users/{userId}/images/`

---

## Expected Behavior After Fix

### Successful Upload Logs:
```
📤 Uploading MRI images for user: e13amxUYVAawo0esEr1GAxSOwFh1
📦 Storage compression: 224x224 → 224x224, 7KB
📤 Uploading: users/e13amxUYVAawo0esEr1GAxSOwFh1/images/t1_xxx.jpg (7KB)
✅ Upload complete: t1_xxx.jpg
📤 Uploading: users/e13amxUYVAawo0esEr1GAxSOwFh1/images/t2_xxx.jpg (7KB)
✅ Upload complete: t2_xxx.jpg
✅ Both images uploaded successfully
🎉 Analysis saved to cloud: abc123
```

### Toast Messages:
- ✅ "Analysis saved to cloud ✅"

---

## Summary

**Problem**: Firebase Storage rules block uploads by default (404 error)  
**Solution**: Update Storage rules to allow authenticated users to upload to their folders  
**Action**: Go to Firebase Console → Storage → Rules → Update & Publish  
**Result**: Users can upload MRI images successfully

After updating the rules, the upload feature will work perfectly! 🚀
