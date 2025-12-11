# Quick Start Guide - SpinoCare Backend Deployment

## 🎯 30-Minute Deployment Guide

### Prerequisites Check
- [ ] Web hosting account with PHP 8.0+ and MySQL
- [ ] Python server (VPS or cloud platform)
- [ ] Gmail account with App Password
- [ ] Domain name (optional but recommended)

---

## Part 1: PHP API (15 minutes)

### 1. Upload Files (3 min)
Upload `php-api/` folder to your web hosting:
- Via cPanel File Manager → `public_html/spinocare-api/`
- Or via FTP to `/public_html/spinocare-api/`

### 2. Create Database (2 min)
In cPanel → phpMyAdmin:
1. Click "New" to create database: `spinocare_db`
2. Go to Import tab
3. Choose `database/spinocare_schema.sql`
4. Click "Go"

### 3. Configure Database (3 min)
Edit `php-api/config.php`:
```php
define('DB_HOST', 'localhost');
define('DB_NAME', 'spinocare_db');
define('DB_USER', 'your_cpanel_username');
define('DB_PASS', 'your_database_password');
```

### 4. Setup Email (5 min)
Get Gmail App Password:
1. Go to https://myaccount.google.com/security
2. Enable 2-Step Verification
3. Click "App passwords" → Generate for "Mail"
4. Copy the 16-character password

Edit `php-api/email.php` (lines 15-16):
```php
$mail->Username = 'youremail@gmail.com';
$mail->Password = 'abcd efgh ijkl mnop';  // App password (no spaces)
```

### 5. Test (2 min)
Visit: `http://yourdomain.com/spinocare-api/test.html`

Click "🚀 Register User" → Check your email for OTP!

---

## Part 2: Python ML Server (15 minutes)

### Option A: Render.com (Easiest - 5 minutes)

1. **Push to GitHub:**
   ```bash
   cd python-ml-server/
   git init
   git add .
   git commit -m "Initial ML server"
   git push github main
   ```

2. **Deploy on Render:**
   - Go to https://render.com
   - Click "New +" → "Web Service"
   - Connect your GitHub repo
   - Select `python-ml-server/` folder
   - Render auto-detects Python and uses `Procfile`
   - Click "Create Web Service"
   - Done! You'll get a URL like `https://spinocare-ml.onrender.com`

### Option B: DigitalOcean Droplet (Full Control - 15 minutes)

1. **Create Droplet:**
   - Go to DigitalOcean → Create Droplet
   - Choose Ubuntu 22.04
   - Select $6/month plan
   - Create

2. **SSH and Setup:**
   ```bash
   ssh root@your_droplet_ip
   
   # Install Python
   apt update
   apt install python3 python3-pip python3-venv -y
   
   # Upload files (from your PC, new terminal)
   scp -r python-ml-server/* root@your_droplet_ip:~/ml-server/
   
   # Back on server
   cd ~/ml-server/
   python3 -m venv venv
   source venv/bin/activate
   pip install -r requirements.txt
   
   # Run server
   python main.py
   ```

3. **Keep Running (systemd):**
   ```bash
   sudo nano /etc/systemd/system/spinocare-ml.service
   ```
   
   Paste:
   ```ini
   [Unit]
   Description=SpinoCare ML
   After=network.target

   [Service]
   User=root
   WorkingDirectory=/root/ml-server
   ExecStart=/root/ml-server/venv/bin/python main.py
   Restart=always

   [Install]
   WantedBy=multi-user.target
   ```
   
   Enable:
   ```bash
   sudo systemctl enable spinocare-ml
   sudo systemctl start spinocare-ml
   sudo systemctl status spinocare-ml
   ```

---

## Part 3: Update Android App (5 minutes)

### 1. Update API URLs

Edit `app/src/main/java/com/example/modicanalyzer/di/NetworkModule.kt`:

```kotlin
object NetworkModule {
    // Change this line:
    private const val BASE_URL = "http://192.168.29.203/spinocare-api/"
    
    // To your production URL:
    private const val BASE_URL = "https://yourdomain.com/spinocare-api/"
}
```

### 2. Rebuild App

In Android Studio:
1. Build → Clean Project
2. Build → Rebuild Project
3. Run on device

---

## ✅ Verification Tests

### Test PHP API:
```bash
# Health check
curl https://yourdomain.com/spinocare-api/

# Should return: {"status":"success","message":"SpinoCare API is running"}
```

### Test ML Server:
```bash
# Health check
curl https://your-ml-server.com/health

# Should return: {"status":"healthy"}
```

### Test Android App:
1. Open app → Sign Up
2. Enter email and details
3. Tap "Sign Up"
4. Check email for OTP code
5. Enter OTP → Should create account!

---

## 🆘 Quick Troubleshooting

**Problem:** Database connection error
- **Fix:** Check `config.php` credentials match cPanel database

**Problem:** Email not sending
- **Fix:** Use Gmail App Password (not regular password)
- **Fix:** Check spam folder

**Problem:** 500 Internal Server Error
- **Fix:** Check file permissions: `chmod 644 *.php`
- **Fix:** Enable PHP extensions in cPanel

**Problem:** Python server not starting
- **Fix:** Check port 5000 is available: `lsof -i :5000`
- **Fix:** Install dependencies: `pip install -r requirements.txt`

**Problem:** Android app can't connect
- **Fix:** Update `NetworkModule.kt` with correct URL
- **Fix:** Enable HTTPS if using SSL
- **Fix:** Check CORS settings in `index.php`

---

## 📞 Need Help?

1. Check full README.md for detailed instructions
2. Check DEPLOYMENT_CHECKLIST.md for complete steps
3. Review error logs:
   - PHP: `/var/log/apache2/error.log`
   - Python: Check server console output

---

## 🎉 Success!

If all tests pass:
- ✅ PHP API is live and processing requests
- ✅ Email OTP verification working
- ✅ Python ML server responding
- ✅ Android app connected to backend
- ✅ Database storing user data

**Your SpinoCare backend is now fully deployed! 🚀**

---

## 📌 Important URLs to Save

- **PHP API:** https://yourdomain.com/spinocare-api/
- **ML Server:** https://your-ml-server.com/
- **API Test Console:** https://yourdomain.com/spinocare-api/test.html
- **phpMyAdmin:** https://yourdomain.com/phpmyadmin/

---

**Deployment Time:** ~30 minutes
**Difficulty:** ⭐⭐⭐ (Moderate)
**Cost:** $5-15/month (hosting + VPS)
