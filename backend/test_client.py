#!/usr/bin/env python3
"""
Test client for federated learning backend.
Simulates multiple clients training locally and uploading weight updates.
"""

import numpy as np
import requests
import time
import uuid
from pathlib import Path
import tempfile

# Backend server URL
SERVER_URL = "https://modic-fl-server.onrender.com"  # Your live Render server

def create_dummy_weights(layer_shapes=None):
    """Create dummy model weights to simulate a trained model."""
    if layer_shapes is None:
        # Example shapes for a simple CNN model (similar to what TFLite might have)
        layer_shapes = {
            'conv2d_kernel': (3, 3, 3, 32),  # Conv2D kernel
            'conv2d_bias': (32,),            # Conv2D bias
            'dense_kernel': (1568, 128),     # Dense layer kernel (flattened conv output)
            'dense_bias': (128,),            # Dense bias
            'output_kernel': (128, 10),      # Output layer (10 classes)
            'output_bias': (10,)             # Output bias
        }
    
    weights = {}
    for name, shape in layer_shapes.items():
        # Add some random noise to simulate training differences
        weights[name] = np.random.normal(0, 0.1, shape).astype(np.float32)
    
    return weights

def save_weights_to_npz(weights, filepath):
    """Save weights dictionary to .npz file."""
    np.savez_compressed(filepath, **weights)

def load_weights_from_npz(filepath):
    """Load weights from .npz file."""
    with np.load(filepath) as data:
        return {k: data[k] for k in data.files}

def upload_weights(client_id, weights_file):
    """Upload weights file to the server."""
    url = f"{SERVER_URL}/upload_weights"
    
    with open(weights_file, 'rb') as f:
        files = {'file': ('weights.npz', f, 'application/octet-stream')}
        data = {'client_id': client_id}
        
        response = requests.post(url, files=files, data=data)
        return response.json()

def download_latest_weights(save_path):
    """Download the latest aggregated weights from the server."""
    url = f"{SERVER_URL}/latest_weights"
    
    response = requests.get(url)
    if response.status_code == 200:
        with open(save_path, 'wb') as f:
            f.write(response.content)
        return True
    return False

def trigger_aggregation():
    """Trigger aggregation on the server."""
    url = f"{SERVER_URL}/aggregate"
    response = requests.post(url)
    return response.json()

def get_server_status():
    """Get server status."""
    url = f"{SERVER_URL}/status"
    response = requests.get(url)
    return response.json()

def simulate_local_training(base_weights, client_id, num_epochs=5):
    """Simulate local training by adding noise to weights."""
    print(f"Client {client_id}: Simulating {num_epochs} epochs of local training...")
    
    # Simulate training by adding small random updates
    trained_weights = {}
    for name, weight in base_weights.items():
        # Simulate gradient updates with small learning rate
        learning_rate = 0.01
        gradient = np.random.normal(0, 0.01, weight.shape)
        trained_weights[name] = weight + learning_rate * gradient
    
    return trained_weights

def main():
    print("🚀 Starting Federated Learning Test Client")
    print(f"Server URL: {SERVER_URL}")
    
    # Check server status
    try:
        status = get_server_status()
        print(f"✅ Server status: {status}")
    except Exception as e:
        print(f"❌ Cannot connect to server: {e}")
        return
    
    # Create initial base weights
    print("\n📊 Creating base model weights...")
    base_weights = create_dummy_weights()
    print(f"Model has {len(base_weights)} layers")
    
    # Simulate multiple clients
    num_clients = 3
    client_updates = []
    
    print(f"\n👥 Simulating {num_clients} clients...")
    
    for i in range(num_clients):
        client_id = f"client_{i+1}_{uuid.uuid4().hex[:8]}"
        print(f"\n🔄 Client {i+1} ({client_id}):")
        
        # Simulate local training
        trained_weights = simulate_local_training(base_weights, client_id)
        
        # Save weights to temporary file
        with tempfile.NamedTemporaryFile(suffix='.npz', delete=False) as tmp:
            tmp_path = tmp.name
        
        try:
            save_weights_to_npz(trained_weights, tmp_path)
            
            # Upload to server
            try:
                result = upload_weights(client_id, tmp_path)
                print(f"  ✅ Upload successful: {result}")
                client_updates.append(client_id)
            except Exception as e:
                print(f"  ❌ Upload failed: {e}")
        finally:
            # Clean up temp file
            try:
                Path(tmp_path).unlink()
            except PermissionError:
                # On Windows, sometimes the file is still locked
                import time
                time.sleep(0.1)
                try:
                    Path(tmp_path).unlink()
                except:
                    print(f"  ⚠️ Could not delete temp file: {tmp_path}")
    
    print(f"\n📈 All clients uploaded. Total updates: {len(client_updates)}")
    
    # Trigger aggregation
    print("\n🔄 Triggering federated aggregation...")
    try:
        agg_result = trigger_aggregation()
        print(f"✅ Aggregation successful: {agg_result}")
    except Exception as e:
        print(f"❌ Aggregation failed: {e}")
        return
    
    # Download aggregated weights
    print("\n⬇️ Downloading aggregated weights...")
    aggregated_file = "aggregated_weights.npz"
    try:
        if download_latest_weights(aggregated_file):
            print(f"✅ Downloaded aggregated weights to: {aggregated_file}")
            
            # Load and inspect
            aggregated_weights = load_weights_from_npz(aggregated_file)
            print(f"📊 Aggregated model has {len(aggregated_weights)} layers")
            
            # Compare with original base weights
            print("\n📈 Weight comparison (L2 norm differences):")
            for name in base_weights.keys():
                if name in aggregated_weights:
                    diff = np.linalg.norm(aggregated_weights[name] - base_weights[name])
                    print(f"  {name}: {diff:.6f}")
            
        else:
            print("❌ Failed to download aggregated weights")
    except Exception as e:
        print(f"❌ Download error: {e}")
    
    # Final status
    final_status = get_server_status()
    print(f"\n🏁 Final server status: {final_status}")
    print("\n✅ Federated Learning cycle completed!")

if __name__ == "__main__":
    main()