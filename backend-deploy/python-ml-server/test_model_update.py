#!/usr/bin/env python3
"""
Test script for model update functionality
"""

import requests
import json
import hashlib
from pathlib import Path

def test_model_info_endpoint():
    """Test the new model_info endpoint"""
    
    base_url = "http://localhost:8000"
    
    print("🧪 Testing Model Update Endpoints")
    print("=" * 50)
    
    # Test model_info endpoint
    try:
        response = requests.get(f"{base_url}/model_info")
        if response.status_code == 200:
            data = response.json()
            print(f"✅ Model Info Endpoint:")
            print(f"   Hash: {data['model_hash'][:16]}...")
            print(f"   Version: {data['model_version']}")
            print(f"   Size: {data['model_size_mb']} MB")
            print(f"   Download URL: {data['download_url']}")
            return data
        else:
            print(f"❌ Model info failed: {response.status_code}")
            return None
    except Exception as e:
        print(f"⚠️ Model info: Server not running - {e}")
        return None

def test_model_download():
    """Test model download with hash verification"""
    
    base_url = "http://localhost:8000"
    
    try:
        response = requests.get(f"{base_url}/get_global_model")
        if response.status_code == 200:
            # Get headers
            model_version = response.headers.get("Model-Version", "unknown")
            model_hash = response.headers.get("Model-Hash", "")
            model_size = response.headers.get("Model-Size", "0")
            
            print(f"✅ Model Download:")
            print(f"   Version: {model_version}")
            print(f"   Hash: {model_hash[:16]}...")
            print(f"   Size: {int(model_size)/1024/1024:.2f} MB")
            
            # Verify downloaded content hash
            downloaded_data = response.content
            calculated_hash = hashlib.sha256(downloaded_data).hexdigest()
            
            if calculated_hash == model_hash:
                print(f"✅ Hash verification: PASSED")
            else:
                print(f"❌ Hash verification: FAILED")
                print(f"   Expected: {model_hash}")
                print(f"   Calculated: {calculated_hash}")
            
            return True
        else:
            print(f"❌ Model download failed: {response.status_code}")
            return False
    except Exception as e:
        print(f"⚠️ Model download: Server not running - {e}")
        return False

def verify_local_model():
    """Verify local model file if it exists"""
    
    model_path = Path("modic_model.tflite")
    if model_path.exists():
        with open(model_path, 'rb') as f:
            local_hash = hashlib.sha256(f.read()).hexdigest()
        
        size_mb = model_path.stat().st_size / (1024*1024)
        
        print(f"✅ Local Model:")
        print(f"   Hash: {local_hash[:16]}...")
        print(f"   Size: {size_mb:.2f} MB")
        
        return local_hash
    else:
        print("⚠️ Local model file not found")
        return None

if __name__ == "__main__":
    # Test server endpoints
    model_info = test_model_info_endpoint()
    
    print()
    test_model_download()
    
    print()
    local_hash = verify_local_model()
    
    # Compare hashes if both available
    if model_info and local_hash:
        print(f"\n🔍 Hash Comparison:")
        if model_info['model_hash'] == local_hash:
            print("✅ Local model matches server model")
        else:
            print("❌ Local model differs from server model")
            print("   → Update available!")
    
    print(f"\n📱 Client Update Logic:")
    print("   1. Periodically call /model_info")
    print("   2. Compare hash with local model hash")  
    print("   3. If different, download via /get_global_model")
    print("   4. Verify downloaded hash matches expected")
    print("   5. Replace local model file")