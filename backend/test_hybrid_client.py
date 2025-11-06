#!/usr/bin/env python3
"""
Test client for hybrid FL architecture (.keras server + .tflite client)
Tests the new endpoints and workflow
"""

import requests
import numpy as np
import tempfile
import os
from pathlib import Path

# Server configuration
SERVER_URL = "http://localhost:8000"  # Change to your deployed URL
CLIENT_ID = "test_client_hybrid"

def create_dummy_weights():
    """Create dummy client weights for testing"""
    weights = {
        "conv2d_kernel": np.random.randn(3, 3, 3, 32).astype(np.float32),
        "conv2d_bias": np.random.randn(32).astype(np.float32),
        "dense_kernel": np.random.randn(1568, 128).astype(np.float32),
        "dense_bias": np.random.randn(128).astype(np.float32),
        "output_kernel": np.random.randn(128, 10).astype(np.float32),
        "output_bias": np.random.randn(10).astype(np.float32),
    }
    return weights

def save_weights_as_npz(weights, filepath):
    """Save weights as .npz file"""
    np.savez_compressed(filepath, **weights)

def test_hybrid_fl_workflow():
    """Test the complete hybrid FL workflow"""
    print("🧪 Testing Hybrid FL Architecture (.keras + .tflite)")
    print("=" * 60)
    
    # Step 1: Check server status
    print("1️⃣ Checking server status...")
    try:
        response = requests.get(f"{SERVER_URL}/status")
        if response.status_code == 200:
            status = response.json()
            print(f"   ✅ Server online - Architecture: {status.get('architecture', 'unknown')}")
            print(f"   📊 Keras model exists: {status.get('keras_model_exists', False)}")
            print(f"   📱 TFLite model exists: {status.get('tflite_model_exists', False)}")
        else:
            print(f"   ❌ Server not responding: {response.status_code}")
            return
    except Exception as e:
        print(f"   ❌ Connection failed: {e}")
        return
    
    # Step 2: Upload client weights
    print("\n2️⃣ Uploading client weights...")
    weights = create_dummy_weights()
    
    with tempfile.NamedTemporaryFile(suffix='.npz', delete=False) as tmp_file:
        save_weights_as_npz(weights, tmp_file.name)
        
        try:
            with open(tmp_file.name, 'rb') as f:
                files = {'file': ('weights.npz', f, 'application/octet-stream')}
                data = {'client_id': CLIENT_ID}
                
                response = requests.post(f"{SERVER_URL}/upload_weights", files=files, data=data)
                
                if response.status_code == 200:
                    result = response.json()
                    print(f"   ✅ Upload successful - Total clients: {result.get('total_clients', 0)}")
                else:
                    print(f"   ❌ Upload failed: {response.status_code}")
                    return
        finally:
            try:
                os.unlink(tmp_file.name)
            except PermissionError:
                # Windows file permission issue - ignore
                pass
    
    # Step 3: Trigger aggregation
    print("\n3️⃣ Triggering aggregation...")
    try:
        response = requests.post(f"{SERVER_URL}/aggregate")
        if response.status_code == 200:
            result = response.json()
            print(f"   ✅ Aggregation successful")
            print(f"   🧠 Keras model: {result.get('keras_model', 'N/A')}")
            print(f"   📱 TFLite model: {result.get('tflite_model', 'N/A')}")
            print(f"   📦 Legacy weights: {result.get('legacy_weights', 'N/A')}")
        else:
            print(f"   ❌ Aggregation failed: {response.status_code}")
            return
    except Exception as e:
        print(f"   ❌ Aggregation error: {e}")
        return
    
    # Step 4: Download global TFLite model
    print("\n4️⃣ Downloading global .tflite model...")
    try:
        response = requests.get(f"{SERVER_URL}/get_global_model")
        if response.status_code == 200:
            model_data = response.content
            print(f"   ✅ TFLite model downloaded: {len(model_data)} bytes")
            
            # Save to file for inspection
            with open("downloaded_global_model.tflite", "wb") as f:
                f.write(model_data)
            print(f"   💾 Saved as: downloaded_global_model.tflite")
        else:
            print(f"   ❌ Download failed: {response.status_code}")
            return
    except Exception as e:
        print(f"   ❌ Download error: {e}")
        return
    
    # Step 5: Test legacy endpoint
    print("\n5️⃣ Testing legacy .npz endpoint...")
    try:
        response = requests.get(f"{SERVER_URL}/latest_weights")
        if response.status_code == 200:
            weights_data = response.content
            print(f"   ✅ Legacy weights downloaded: {len(weights_data)} bytes")
        else:
            print(f"   ⚠️ Legacy endpoint failed: {response.status_code}")
    except Exception as e:
        print(f"   ⚠️ Legacy endpoint error: {e}")
    
    print("\n🎉 Hybrid FL workflow test completed!")
    print("\n📋 Summary:")
    print("   • Client uploads weight deltas (.npz)")
    print("   • Server aggregates using .keras model")
    print("   • Server converts to .tflite for distribution")
    print("   • Clients download updated .tflite model")

def test_server_info():
    """Test the server info endpoint"""
    print("\n🔍 Server Information:")
    try:
        response = requests.get(f"{SERVER_URL}/")
        if response.status_code == 200:
            info = response.json()
            print(f"   📝 {info.get('message', 'N/A')}")
            print(f"   🏗️ Architecture: {info.get('architecture', 'N/A')}")
            print(f"   📊 Version: {info.get('version', 'N/A')}")
            
            workflow = info.get('workflow', {})
            if workflow:
                print("   🔄 Workflow:")
                for step, desc in workflow.items():
                    print(f"      {step}. {desc}")
        else:
            print(f"   ❌ Info request failed: {response.status_code}")
    except Exception as e:
        print(f"   ❌ Info request error: {e}")

if __name__ == "__main__":
    test_server_info()
    test_hybrid_fl_workflow()