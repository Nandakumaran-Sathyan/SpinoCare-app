#!/usr/bin/env python3
"""
Quick test of the updated server endpoints
"""

import requests
import json
from pathlib import Path

def test_server_endpoints():
    """Test key server endpoints to verify TFLite migration"""
    
    base_url = "http://localhost:8000"
    
    print("🧪 Testing Server Endpoints")
    print("=" * 50)
    
    # Test root endpoint
    try:
        response = requests.get(f"{base_url}/")
        if response.status_code == 200:
            data = response.json()
            print(f"✅ Root endpoint: {data['version']} - {data['architecture']}")
            print(f"   Message: {data['message']}")
        else:
            print(f"❌ Root endpoint failed: {response.status_code}")
    except Exception as e:
        print(f"⚠️ Root endpoint: Server not running - {e}")
    
    # Test health endpoint
    try:
        response = requests.get(f"{base_url}/health")
        if response.status_code == 200:
            data = response.json()
            print(f"✅ Health check: {data['status']}")
            print(f"   TFLite model loaded: {data['components']['prediction_model_loaded']}")
            if 'model_info' in data:
                print(f"   Model inputs: {data['model_info']['input_count']}")
        else:
            print(f"❌ Health endpoint failed: {response.status_code}")
    except Exception as e:
        print(f"⚠️ Health endpoint: Server not running - {e}")
    
    # Test status endpoint  
    try:
        response = requests.get(f"{base_url}/status")
        if response.status_code == 200:
            data = response.json()
            print(f"✅ Status endpoint: {data['architecture']} v{data['version']}")
            print(f"   TFLite model: {data['tflite_model_size_mb']:.1f} MB")
            print(f"   Online inference: {data.get('features', {}).get('online_inference', False)}")
        else:
            print(f"❌ Status endpoint failed: {response.status_code}")
    except Exception as e:
        print(f"⚠️ Status endpoint: Server not running - {e}")

def check_local_files():
    """Check local file status"""
    print("\n📁 Local File Status")
    print("=" * 30)
    
    backend_dir = Path(".")
    
    # Check TFLite model
    tflite_model = backend_dir / "modic_model.tflite"
    if tflite_model.exists():
        size_mb = tflite_model.stat().st_size / (1024*1024)
        print(f"✅ modic_model.tflite: {size_mb:.2f} MB")
    else:
        print("❌ modic_model.tflite: Not found")
    
    # Check if old Keras model still exists
    keras_model = backend_dir / "final_model.keras"
    if keras_model.exists():
        size_mb = keras_model.stat().st_size / (1024*1024)
        print(f"⚠️ final_model.keras still exists: {size_mb:.2f} MB (consider removing)")
    else:
        print("✅ final_model.keras: Removed (as intended)")
    
    # Check global TFLite
    global_tflite = backend_dir / "global_model.tflite"
    if global_tflite.exists():
        size_mb = global_tflite.stat().st_size / (1024*1024)
        print(f"✅ global_model.tflite: {size_mb:.2f} MB")
    else:
        print("⚠️ global_model.tflite: Not found")

if __name__ == "__main__":
    check_local_files()
    print("\n" + "=" * 50)
    print("💡 To test server endpoints:")
    print("   1. Start server: python -m uvicorn main:app --host 0.0.0.0 --port 8000")
    print("   2. Run: python test_server_endpoints.py")
    print("   3. Or test manually: curl http://localhost:8000/health")