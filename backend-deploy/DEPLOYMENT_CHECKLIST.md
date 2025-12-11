# SpinoCare Backend Deployment Checklist

## Pre-Deployment Preparation

### PHP API
- [ ] Copy `.env.example` to `.env` and configure
- [ ] Update database credentials in `config.php`
- [ ] Set Gmail SMTP credentials in `email.php`
- [ ] Generate strong JWT secret key
- [ ] Remove or secure `test.html` (delete in production)
- [ ] Review CORS settings in `index.php`

### Python ML Server
- [ ] Copy `.env.example` to `.env` and configure
- [ ] Verify `modic_model.tflite` file exists
- [ ] Update `requirements.txt` if needed
- [ ] Configure port and host settings
- [ ] Set API key for authentication

### Database
- [ ] Create database: `spinocare_db`
- [ ] Import schema: `spinocare_schema.sql`
- [ ] Run migrations in order
- [ ] Create database user with limited privileges
- [ ] Test database connection

---

## Deployment Steps

### Step 1: Upload Files
- [ ] Upload `php-api/` to web server
- [ ] Upload `python-ml-server/` to ML server
- [ ] Set correct file permissions (644 files, 755 directories)

### Step 2: Configure Web Server
- [ ] Set up Apache/Nginx virtual host
- [ ] Configure URL rewriting (.htaccess or nginx.conf)
- [ ] Enable required PHP extensions (mysqli, curl, openssl)
- [ ] Restart web server

### Step 3: Database Setup
- [ ] Create database and user
- [ ] Import schema and migrations
- [ ] Verify tables created successfully
- [ ] Test database connection from API

### Step 4: Python Server Setup
- [ ] Create virtual environment
- [ ] Install dependencies: `pip install -r requirements.txt`
- [ ] Test server locally: `python main.py`
- [ ] Set up systemd service or process manager
- [ ] Configure reverse proxy (Nginx)

### Step 5: SSL/HTTPS
- [ ] Obtain SSL certificate (Let's Encrypt recommended)
- [ ] Configure HTTPS for PHP API
- [ ] Configure HTTPS for Python ML server
- [ ] Force HTTPS redirect
- [ ] Test SSL configuration

### Step 6: Security Hardening
- [ ] Change all default passwords
- [ ] Restrict database access to localhost
- [ ] Configure firewall rules
- [ ] Set up fail2ban (optional)
- [ ] Enable PHP security settings
- [ ] Disable directory listing
- [ ] Remove sensitive files from public access

---

## Testing Checklist

### PHP API Tests
- [ ] Health check: `GET /`
- [ ] Register user: `POST /auth/register`
- [ ] Verify OTP email received
- [ ] Verify email: `POST /auth/verify-email`
- [ ] Login: `POST /auth/login`
- [ ] Get user: `GET /users/{uid}`
- [ ] Save analysis: `POST /analysis/save`
- [ ] Get analyses: `GET /analysis/list/{uid}`

### Python ML Server Tests
- [ ] Health check: `GET /health`
- [ ] Model info: `GET /model/info`
- [ ] Test prediction endpoint
- [ ] Test model update endpoint
- [ ] Verify model aggregation works

### Integration Tests
- [ ] Android app can connect to PHP API
- [ ] Android app can connect to ML server
- [ ] OTP emails are delivered
- [ ] User registration flow works end-to-end
- [ ] Analysis save and retrieve works
- [ ] Model updates are received by app

---

## Post-Deployment

### Monitoring Setup
- [ ] Set up server monitoring (UptimeRobot, Pingdom)
- [ ] Configure log rotation
- [ ] Set up error alerting
- [ ] Monitor disk space
- [ ] Monitor database size

### Backup Configuration
- [ ] Set up automated database backups
- [ ] Test backup restoration
- [ ] Configure off-site backup storage
- [ ] Document backup procedures

### Documentation
- [ ] Update API base URL in Android app
- [ ] Update ML server URL in Android app
- [ ] Document all API endpoints
- [ ] Create troubleshooting guide
- [ ] Document emergency procedures

### Performance
- [ ] Enable PHP opcode caching (OPcache)
- [ ] Configure MySQL query cache
- [ ] Set up CDN for static assets (if needed)
- [ ] Optimize database indexes
- [ ] Configure connection pooling

---

## Production URLs to Update

After deployment, update these URLs in your Android app:

**File: `app/src/main/java/com/example/modicanalyzer/di/NetworkModule.kt`**

```kotlin
// Change from local IP
private const val BASE_URL = "http://192.168.29.203/spinocare-api/"

// To production URL
private const val BASE_URL = "https://api.yourdomain.com/spinocare-api/"
```

---

## Common Deployment Platforms

### For PHP API:
1. **Shared Hosting** (Hostinger, Bluehost) - Easiest
2. **VPS** (DigitalOcean, Linode) - Most flexible
3. **PaaS** (Heroku, Platform.sh) - Managed

### For Python ML Server:
1. **Render.com** - Easiest (already configured)
2. **Railway.app** - Simple
3. **AWS EC2** - Most control
4. **Google Cloud Run** - Serverless

---

## Emergency Contacts & Resources

- **Server Provider Support:** [Your provider's support link]
- **Domain Registrar:** [Your registrar]
- **SSL Provider:** Let's Encrypt / CloudFlare
- **Monitoring Dashboard:** [Your monitoring URL]
- **Database Backup Location:** [Backup location]

---

## Rollback Plan

If deployment fails:

1. **Database:** Restore from backup
   ```bash
   mysql -u root -p spinocare_db < backup_YYYYMMDD.sql
   ```

2. **PHP API:** Revert to previous version
   ```bash
   git checkout previous-working-commit
   ```

3. **Python ML Server:** Stop service and restore
   ```bash
   sudo systemctl stop spinocare-ml.service
   # Restore previous files
   sudo systemctl start spinocare-ml.service
   ```

---

**Deployment Date:** __________

**Deployed By:** __________

**Production URLs:**
- PHP API: __________
- Python ML Server: __________
- Database Host: __________

**Notes:**
