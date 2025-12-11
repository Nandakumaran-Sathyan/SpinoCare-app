# Production Deployment Guide

## 🚀 Deploy to Heroku (Recommended)

### Step 1: Prepare for deployment
```bash
cd backend
echo "web: uvicorn main:app --host=0.0.0.0 --port=\$PORT" > Procfile
echo "python-3.9.11" > runtime.txt
```

### Step 2: Create Heroku app
```bash
heroku create your-modic-fl-server
heroku config:set PYTHON_ENV=production
```

### Step 3: Deploy
```bash
git init
git add .
git commit -m "Initial FL server deployment"
git push heroku main
```

### Step 4: Scale and monitor
```bash
heroku ps:scale web=1
heroku logs --tail
```

## 🔧 Alternative: Railway Deployment

1. Go to [railway.app](https://railway.app)
2. Connect your GitHub repo
3. Select the `backend` folder
4. Auto-deploys on git push

## 🌐 Alternative: DigitalOcean App Platform

1. Create account at DigitalOcean
2. Use App Platform
3. Connect GitHub repo
4. Configure:
   - Source: `backend/`
   - Build command: `pip install -r requirements.txt`
   - Run command: `uvicorn main:app --host=0.0.0.0 --port=8080`

## 📱 Update Android App

Change server URL in your Android app:
```kotlin
private val serverUrl: String = "https://your-modic-fl-server.herokuapp.com"
```

## 🧪 Testing with Real Users

1. Build and distribute your Android APK
2. Users can participate in federated learning
3. Monitor server logs to see real FL training
4. Use analytics to track participation

## 📊 Monitoring & Analytics

Add to your server:
- User participation tracking
- Model performance metrics
- Training statistics
- Error monitoring

## 🔐 Production Security

1. Add API authentication
2. Rate limiting
3. HTTPS enforcement
4. Input validation
5. Differential privacy