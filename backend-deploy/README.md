# SpinoCare Backend Deployment Package

Complete backend infrastructure for the SpinoCare MRI Analysis application.

## 📦 Package Contents

```
backend-deploy/
├── php-api/              # PHP REST API for authentication & data storage
├── python-ml-server/     # Python Flask server for ML model serving
├── database/             # SQL schema and migration scripts
└── README.md            # This file
```

---

## 🚀 Quick Start Guide

### Prerequisites

**For PHP API:**
- PHP 8.0 or higher
- MySQL 5.7+ or MariaDB 10.3+
- Apache or Nginx web server
- Composer (optional, for PHPMailer updates)

**For Python ML Server:**
- Python 3.8 or higher
- pip package manager
- Virtual environment (recommended)

---

## 📍 Part 1: PHP API Deployment

### Step 1: Upload Files

Upload the `php-api/` folder to your web server:
```bash
# Via FTP/SFTP to your hosting provider
/var/www/html/spinocare-api/
# or
/public_html/spinocare-api/
```

### Step 2: Database Setup

1. **Create Database:**
   ```sql
   CREATE DATABASE spinocare_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. **Import Schema:**
   - Open phpMyAdmin or MySQL client
   - Select `spinocare_db` database
   - Import `database/spinocare_schema.sql`
   - Run migrations in order:
     - `database/add_email_verified_field.sql`
     - `database/add_password_field.sql`

3. **Create Database User:**
   ```sql
   CREATE USER 'spinocare_user'@'localhost' IDENTIFIED BY 'secure_password_here';
   GRANT ALL PRIVILEGES ON spinocare_db.* TO 'spinocare_user'@'localhost';
   FLUSH PRIVILEGES;
   ```

### Step 3: Configure PHP API

1. **Copy environment file:**
   ```bash
   cd php-api/
   cp .env.example .env
   ```

2. **Edit `php-api/config.php`:**
   ```php
   define('DB_HOST', 'localhost');
   define('DB_NAME', 'spinocare_db');
   define('DB_USER', 'spinocare_user');
   define('DB_PASS', 'your_secure_password');
   ```

3. **Configure Email in `php-api/email.php`:**
   ```php
   $mail->Username = 'your-gmail@gmail.com';
   $mail->Password = 'your-app-password';  // Gmail App Password
   ```

   **Get Gmail App Password:**
   - Go to Google Account → Security
   - Enable 2-Step Verification
   - Generate App Password for "Mail"

4. **Set Permissions:**
   ```bash
   chmod 755 php-api/
   chmod 644 php-api/*.php
   chmod 600 php-api/.env
   ```

### Step 4: Apache Configuration

**Option A: .htaccess (Shared Hosting)**

Create `php-api/.htaccess`:
```apache
<IfModule mod_rewrite.c>
    RewriteEngine On
    RewriteBase /spinocare-api/
    
    # Redirect all requests to index.php
    RewriteCond %{REQUEST_FILENAME} !-f
    RewriteCond %{REQUEST_FILENAME} !-d
    RewriteRule ^(.*)$ index.php?path=$1 [QSA,L]
    
    # CORS Headers
    Header set Access-Control-Allow-Origin "*"
    Header set Access-Control-Allow-Methods "GET, POST, PUT, DELETE, OPTIONS"
    Header set Access-Control-Allow-Headers "Content-Type, Authorization"
</IfModule>
```

**Option B: Virtual Host (VPS/Dedicated Server)**

Add to Apache config:
```apache
<VirtualHost *:80>
    ServerName api.spinocare.com
    DocumentRoot /var/www/html/spinocare-api
    
    <Directory /var/www/html/spinocare-api>
        Options -Indexes +FollowSymLinks
        AllowOverride All
        Require all granted
    </Directory>
    
    ErrorLog ${APACHE_LOG_DIR}/spinocare-api-error.log
    CustomLog ${APACHE_LOG_DIR}/spinocare-api-access.log combined
</VirtualHost>
```

### Step 5: Test PHP API

Visit: `http://your-domain.com/spinocare-api/test.html`

**Test Endpoints:**
- `GET /` → Health check
- `POST /auth/register` → Register with OTP
- `POST /auth/verify-email` → Verify OTP
- `POST /auth/login` → Login

---

## 🤖 Part 2: Python ML Server Deployment

### Step 1: Prepare Server Environment

```bash
# SSH into your server
ssh user@your-server.com

# Create directory
mkdir -p ~/spinocare-ml-server
cd ~/spinocare-ml-server
```

### Step 2: Upload Python Files

Upload `python-ml-server/` contents to the server:
```bash
# From your local machine
scp -r python-ml-server/* user@your-server.com:~/spinocare-ml-server/
```

### Step 3: Install Dependencies

```bash
# Create virtual environment
python3 -m venv venv
source venv/bin/activate  # Linux/Mac
# or
venv\Scripts\activate  # Windows

# Install packages
pip install --upgrade pip
pip install -r requirements.txt
```

### Step 4: Configure Environment

```bash
cp .env.example .env
nano .env  # Edit configuration
```

### Step 5: Run Server

**Development:**
```bash
python main.py
# Server runs on http://0.0.0.0:5000
```

**Production (using Gunicorn):**
```bash
# Install Gunicorn
pip install gunicorn

# Run with multiple workers
gunicorn -w 4 -b 0.0.0.0:5000 main:app

# Or use the production script
python production_deployment.py
```

**Production (using systemd service):**

Create `/etc/systemd/system/spinocare-ml.service`:
```ini
[Unit]
Description=SpinoCare ML Server
After=network.target

[Service]
User=your-username
WorkingDirectory=/home/your-username/spinocare-ml-server
Environment="PATH=/home/your-username/spinocare-ml-server/venv/bin"
ExecStart=/home/your-username/spinocare-ml-server/venv/bin/gunicorn -w 4 -b 0.0.0.0:5000 main:app
Restart=always

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl daemon-reload
sudo systemctl enable spinocare-ml.service
sudo systemctl start spinocare-ml.service
sudo systemctl status spinocare-ml.service
```

### Step 6: Nginx Reverse Proxy (Recommended)

Install Nginx:
```bash
sudo apt install nginx
```

Configure `/etc/nginx/sites-available/spinocare-ml`:
```nginx
server {
    listen 80;
    server_name ml.spinocare.com;
    
    location / {
        proxy_pass http://127.0.0.1:5000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Enable site:
```bash
sudo ln -s /etc/nginx/sites-available/spinocare-ml /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

### Step 7: Test ML Server

```bash
# Health check
curl http://your-server.com:5000/health

# Get model info
curl http://your-server.com:5000/model/info
```

---

## 🔐 Security Checklist

### PHP API:
- [ ] Change default database password
- [ ] Set up Gmail App Password (not regular password)
- [ ] Generate strong JWT secret key
- [ ] Disable directory listing
- [ ] Remove `test.html` in production
- [ ] Enable HTTPS (SSL certificate)
- [ ] Set restrictive file permissions (644 for files, 755 for dirs)
- [ ] Configure CORS properly (don't use `*` in production)

### Python ML Server:
- [ ] Generate strong API key
- [ ] Run as non-root user
- [ ] Enable firewall (allow only necessary ports)
- [ ] Set up HTTPS with reverse proxy
- [ ] Configure proper CORS origins
- [ ] Set up monitoring and logging
- [ ] Enable rate limiting

### Database:
- [ ] Use strong passwords
- [ ] Limit database user privileges
- [ ] Enable binary logging for backups
- [ ] Set up automated backups
- [ ] Restrict remote access (bind to 127.0.0.1)

---

## 🌐 Deployment Platforms

### PHP API Options:

**1. Shared Hosting (Easy)**
- Hostinger, Bluehost, SiteGround
- Upload via FTP/cPanel File Manager
- Use phpMyAdmin for database
- Cost: $3-10/month

**2. VPS/Cloud (Recommended)**
- DigitalOcean, Linode, Vultr, AWS EC2
- Full server control
- Install LAMP stack
- Cost: $5-20/month

**3. Platform-as-a-Service**
- Heroku (with ClearDB MySQL add-on)
- Platform.sh
- Cost: $7-25/month

### Python ML Server Options:

**1. Render.com (Easiest)**
```bash
# Already configured in Procfile
git push render main
```

**2. Railway.app**
- Connect GitHub repo
- Auto-deploy on push
- Cost: Free tier available

**3. Google Cloud Run**
- Containerized deployment
- Auto-scaling
- Pay per use

**4. AWS EC2 / DigitalOcean**
- Full control
- Manual setup required
- Most cost-effective for high traffic

---

## 📱 Update Android App Configuration

After deployment, update your Android app's `NetworkModule.kt`:

```kotlin
object NetworkModule {
    // Update to your deployed API URL
    private const val BASE_URL = "https://api.yourdomain.com/spinocare-api/"
    
    // Update ML server URL
    private const val ML_SERVER_URL = "https://ml.yourdomain.com/"
}
```

Rebuild the Android app:
```bash
cd app/
./gradlew clean
./gradlew assembleRelease
```

---

## 🧪 Testing Deployment

### Test PHP API:
```bash
# Health check
curl https://api.yourdomain.com/spinocare-api/

# Register user
curl -X POST https://api.yourdomain.com/spinocare-api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test123!","display_name":"Test User","phone_number":"1234567890"}'

# Verify OTP
curl -X POST https://api.yourdomain.com/spinocare-api/auth/verify-email \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","otp":"123456","password_hash":"...","display_name":"Test User","phone_number":"1234567890"}'
```

### Test ML Server:
```bash
# Health check
curl https://ml.yourdomain.com/health

# Model info
curl https://ml.yourdomain.com/model/info

# Test prediction (requires model)
curl -X POST https://ml.yourdomain.com/predict \
  -H "Content-Type: application/json" \
  -d '{"image_data":"base64_encoded_image"}'
```

---

## 📊 Monitoring & Maintenance

### PHP API Logs:
- Apache: `/var/log/apache2/error.log`
- PHP errors: Check `error_log` in `php-api/` folder
- Database: MySQL slow query log

### Python ML Server Logs:
```bash
# Systemd service logs
sudo journalctl -u spinocare-ml.service -f

# Application logs
tail -f ~/spinocare-ml-server/logs/app.log
```

### Database Backup:
```bash
# Daily backup script
mysqldump -u root -p spinocare_db > backup_$(date +%Y%m%d).sql

# Automate with cron (daily at 2 AM)
crontab -e
0 2 * * * mysqldump -u root -pYOUR_PASSWORD spinocare_db > /backups/spinocare_$(date +\%Y\%m\%d).sql
```

---

## 🆘 Troubleshooting

### PHP API Issues:

**500 Internal Server Error:**
- Check Apache error log
- Verify PHP extensions installed (mysqli, curl, openssl)
- Check file permissions (644 for files, 755 for directories)

**Database Connection Failed:**
- Verify credentials in `config.php`
- Check MySQL service: `sudo systemctl status mysql`
- Test connection: `mysql -u spinocare_user -p spinocare_db`

**Email Not Sending:**
- Verify Gmail App Password (not regular password)
- Check SMTP settings in `email.php`
- Enable "Less secure app access" if needed
- Check firewall allows outbound port 587

### Python ML Server Issues:

**Module Not Found:**
```bash
pip install -r requirements.txt
```

**Port Already in Use:**
```bash
# Find process using port 5000
lsof -i :5000
# Kill process
kill -9 <PID>
```

**TensorFlow Lite Error:**
- Verify `modic_model.tflite` exists
- Check model file permissions
- Ensure TensorFlow version matches model

---

## 📞 Support & Resources

- **PHP Documentation:** https://www.php.net/docs.php
- **Flask Documentation:** https://flask.palletsprojects.com/
- **MySQL Documentation:** https://dev.mysql.com/doc/
- **PHPMailer:** https://github.com/PHPMailer/PHPMailer

---

## 📝 Version History

- **v1.0.0** (Current)
  - 2-step OTP email verification
  - MySQL authentication
  - Federated learning support
  - TensorFlow Lite model serving

---

## 🎯 Next Steps

1. ✅ Deploy PHP API to web hosting
2. ✅ Set up MySQL database
3. ✅ Configure email SMTP
4. ✅ Deploy Python ML server
5. ✅ Update Android app URLs
6. ✅ Test all endpoints
7. ✅ Enable HTTPS (SSL)
8. ✅ Set up monitoring
9. ✅ Configure automated backups

---

**Ready to deploy! 🚀**

For questions or issues, refer to the original repository documentation.
