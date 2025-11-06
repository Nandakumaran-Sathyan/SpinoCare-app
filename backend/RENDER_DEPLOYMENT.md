# Deploy to Render.com

## 🚀 Render.com Deployment (Recommended)

Render.com is perfect for your FL server - better than Heroku with:
- ✅ Free tier with 750 hours/month
- ✅ Automatic HTTPS
- ✅ GitHub auto-deployment
- ✅ Better performance
- ✅ No sleeping on free tier (unlike Heroku)

### Step 1: Prepare Your Repository

Your backend is already configured! Just need to:

1. **Push to GitHub** (if not already):
```bash
cd backend
git init
git add .
git commit -m "Federated Learning Server for Render"
git remote add origin https://github.com/yourusername/modic-fl-server.git
git push -u origin main
```

### Step 2: Deploy on Render

1. Go to [render.com](https://render.com)
2. Sign up/login with GitHub
3. Click "New +" → "Web Service"
4. Connect your GitHub repository
5. Configure:

**Build and Deploy Settings:**
```
Name: modic-fl-server
Environment: Python 3
Region: Choose closest to your users
Branch: main
Root Directory: backend
Build Command: pip install -r requirements.txt
Start Command: uvicorn main:app --host=0.0.0.0 --port=$PORT
```

**Environment Variables:**
```
PYTHON_ENV=production
```

### Step 3: Your Server URL

After deployment, you'll get a URL like:
```
https://modic-fl-server.onrender.com
```

### Step 4: Update Android App

In your Android app, change:
```kotlin
// In FederatedLearningClient.kt
private val serverUrl: String = "https://modic-fl-server.onrender.com"
```

### Step 5: Test Your Deployed Server

```bash
# Test from your local machine
python test_client.py
# Change SERVER_URL in test_client.py to your Render URL first
```

## 🆚 Render vs Heroku Comparison

| Feature | Render | Heroku |
|---------|---------|---------|
| Free Hours | 750/month | 1000/month |
| Sleep Policy | No sleeping | Sleeps after 30min |
| Build Speed | Fast | Slow |
| HTTPS | Automatic | Manual setup |
| GitHub Integration | Native | Add-on required |
| Price (Paid) | $7/month | $7/month |

## 🔧 Render.com Specific Features

### Auto-Deploy on Git Push
- Every git push triggers automatic deployment
- No manual deploy commands needed

### Built-in Monitoring
- View logs in real-time
- CPU/Memory usage graphs
- Request metrics

### Custom Domains
```
# Add custom domain in Render dashboard
your-fl-server.yourdomain.com
```

## 🛠️ Production Optimizations for Render

Add to your `main.py` (already included):
```python
import os

# Production configuration
if os.getenv("PYTHON_ENV") == "production":
    # Render-specific optimizations
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["https://yourdomain.com"],  # Your app domain
        allow_credentials=True,
        allow_methods=["GET", "POST"],
        allow_headers=["*"],
    )
```

## 📊 Monitoring Your Live Server

Use the dashboard:
```bash
# Update dashboard.py server URL
SERVER_URL = "https://modic-fl-server.onrender.com"
python dashboard.py
```

## 🔒 Security for Production

1. **Environment Variables in Render**:
   - Add API keys securely
   - Database URLs
   - Secret keys

2. **CORS Configuration**:
   - Restrict to your Android app domains
   - Remove wildcard "*" origins

3. **Rate Limiting** (add to requirements.txt):
```
slowapi==0.1.9
```

Then in main.py:
```python
from slowapi import Limiter
from slowapi.util import get_remote_address

limiter = Limiter(key_func=get_remote_address)

@app.post("/upload_weights")
@limiter.limit("10/minute")  # Max 10 uploads per minute per IP
async def upload_weights(...):
```

## ✅ Ready to Deploy!

Your federated learning server is **Render.com ready**! The configuration files are already set up. Just:

1. Push to GitHub
2. Connect to Render
3. Deploy
4. Update Android app URL
5. Test with real users! 🎉