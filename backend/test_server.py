#!/usr/bin/env python3
"""
Simple test client to verify FL server functionality
"""
import requests
import numpy as np
import io
import zipfile

SERVER_URL = "https://modic-fl-server.onrender.com"

def test_server_status():
    """Test server status endpoint"""
    print("🔍 Testing server status...")
    try:
        response = requests.get(f"{SERVER_URL}/status")
        if response.status_code == 200:
            data = response.json()
            print(f"✅ Server is running!")
            print(f"   Uploads: {data['uploads']}")
            print(f"   Total uploads: {data['total_uploads']}")
            print(f"   Unique clients: {data['unique_clients']}")
            print(f"   Global model exists: {data['global_exists']}")
            return True
        else:
            print(f"❌ Server returned {response.status_code}")
            return False
    except Exception as e:
        print(f"❌ Error connecting to server: {e}")
        return False

def create_test_weights():
    """Create dummy weights for testing"""
    weights = {
        'layer_1': np.random.randn(10, 5).astype(np.float32),
        'layer_2': np.random.randn(5, 3).astype(np.float32),
        'bias_1': np.random.randn(5).astype(np.float32),
        'bias_2': np.random.randn(3).astype(np.float32)
    }
    return weights

def serialize_weights_to_npz(weights):
    """Serialize weights to NPZ format"""
    buffer = io.BytesIO()
    np.savez_compressed(buffer, **weights)
    return buffer.getvalue()

def test_upload_weights():
    """Test uploading weights to server"""
    print("\n📤 Testing weight upload...")
    try:
        # Create test weights
        weights = create_test_weights()
        npz_data = serialize_weights_to_npz(weights)
        
        # Upload to server
        files = {'file': ('test_weights.npz', npz_data, 'application/octet-stream')}
        data = {'client_id': 'test_client_123'}
        
        response = requests.post(f"{SERVER_URL}/upload_weights", files=files, data=data)
        
        if response.status_code == 200:
            result = response.json()
            print(f"✅ Upload successful!")
            print(f"   Status: {result['status']}")
            print(f"   Filename: {result['filename']}")
            print(f"   Total clients: {result['total_clients']}")
            print(f"   Total uploads: {result['total_uploads']}")
            return True
        else:
            print(f"❌ Upload failed: {response.status_code}")
            print(f"   Response: {response.text}")
            return False
    except Exception as e:
        print(f"❌ Error uploading weights: {e}")
        return False

def test_aggregation():
    """Test triggering aggregation"""
    print("\n🔄 Testing aggregation...")
    try:
        response = requests.post(f"{SERVER_URL}/aggregate")
        
        if response.status_code == 200:
            result = response.json()
            print(f"✅ Aggregation successful!")
            print(f"   Status: {result['status']}")
            print(f"   Clients aggregated: {result['clients']}")
            print(f"   Output file: {result['output']}")
            print(f"   Aggregation number: {result['aggregation_number']}")
            return True
        else:
            print(f"❌ Aggregation failed: {response.status_code}")
            print(f"   Response: {response.text}")
            return False
    except Exception as e:
        print(f"❌ Error triggering aggregation: {e}")
        return False

def main():
    print("🧪 Federated Learning Server Test")
    print("=" * 40)
    
    # Test 1: Server status
    if not test_server_status():
        print("\n❌ Server is not responding. Please check the deployment.")
        return
    
    # Test 2: Upload weights
    if not test_upload_weights():
        print("\n❌ Weight upload failed.")
        return
    
    # Test 3: Check status after upload
    print("\n🔍 Checking status after upload...")
    test_server_status()
    
    # Test 4: Trigger aggregation
    if not test_aggregation():
        print("\n❌ Aggregation failed.")
        return
    
    # Test 5: Final status check
    print("\n🔍 Final status check...")
    test_server_status()
    
    print("\n✅ All tests passed! Server is working correctly.")

if __name__ == "__main__":
    main()