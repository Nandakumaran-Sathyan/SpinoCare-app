#!/usr/bin/env python3
"""
Real-time user simulation for federated learning.
Simulates multiple users                # Trigger aggregation if we have enough participants
            if successful_uploads >= 2:
                try:
                    print("🔄 Triggering federated aggregation...")
                    response = requests.post("https://modic-fl-server.onrender.com/aggregate")  # Your live Render servering and leaving the federated learning process.
"""

import asyncio
import random
import time
import requests
import numpy as np
from pathlib import Path
import tempfile
from test_client import create_dummy_weights, save_weights_to_npz, upload_weights, simulate_local_training

class FLUser:
    def __init__(self, user_id, server_url="https://modic-fl-server.onrender.com"):  # Your live Render server
        self.user_id = user_id
        self.server_url = server_url
        self.model_weights = create_dummy_weights()
        self.training_data_size = random.randint(10, 100)  # Simulate different data sizes
        self.participation_probability = random.uniform(0.3, 0.9)  # Some users more active
        
    def should_participate(self):
        """Simulate real-world participation patterns"""
        return random.random() < self.participation_probability
    
    def local_training_rounds(self):
        """Simulate variable local training intensity"""
        if self.training_data_size > 50:
            return random.randint(3, 10)  # Users with more data train longer
        else:
            return random.randint(1, 5)   # Users with less data train shorter
    
    async def participate_in_round(self):
        """Simulate a user participating in one FL round"""
        if not self.should_participate():
            print(f"👤 User {self.user_id}: Skipping this round")
            return False
            
        print(f"👤 User {self.user_id}: Starting local training...")
        
        # Simulate local training
        rounds = self.local_training_rounds()
        trained_weights = simulate_local_training(self.model_weights, self.user_id, rounds)
        
        # Upload weights
        try:
            with tempfile.NamedTemporaryFile(suffix='.npz', delete=False) as tmp:
                tmp_path = tmp.name
            
            save_weights_to_npz(trained_weights, tmp_path)
            result = upload_weights(self.user_id, tmp_path)
            
            # Clean up
            try:
                Path(tmp_path).unlink()
            except:
                pass
                
            print(f"👤 User {self.user_id}: ✅ Uploaded weights successfully")
            
            # Update local model weights
            self.model_weights = trained_weights
            return True
            
        except Exception as e:
            print(f"👤 User {self.user_id}: ❌ Upload failed: {e}")
            return False

async def simulate_real_time_fl():
    """Simulate real-time federated learning with multiple users"""
    
    print("🌍 Starting Real-Time Federated Learning Simulation")
    print("=" * 60)
    
    # Create multiple users
    users = [FLUser(f"user_{i:03d}") for i in range(1, 11)]  # 10 users
    print(f"👥 Created {len(users)} simulated users")
    
    round_number = 1
    
    while True:
        print(f"\n🔄 === ROUND {round_number} ===")
        print(f"⏰ Time: {time.strftime('%H:%M:%S')}")
        
        # Users participate randomly
        participating_users = []
        tasks = []
        
        for user in users:
            if user.should_participate():
                participating_users.append(user)
                tasks.append(user.participate_in_round())
        
        if tasks:
            print(f"📊 {len(participating_users)} users participating in this round...")
            
            # Wait for all users to complete local training and upload
            results = await asyncio.gather(*tasks, return_exceptions=True)
            successful_uploads = sum(1 for r in results if r is True)
            
            print(f"✅ {successful_uploads}/{len(participating_users)} successful uploads")
            
            # Trigger aggregation if we have enough participants
            if successful_uploads >= 2:
                try:
                    print("🔄 Triggering federated aggregation...")
                    response = requests.post("http://localhost:8000/aggregate")
                    if response.status_code == 200:
                        data = response.json()
                        print(f"✅ Aggregation completed! Clients: {data['clients']}")
                        print(f"📈 Total aggregations so far: {data['aggregation_number']}")
                    else:
                        print(f"❌ Aggregation failed: {response.status_code}")
                except Exception as e:
                    print(f"❌ Aggregation error: {e}")
            else:
                print("⏳ Not enough participants for aggregation (need ≥2)")
        else:
            print("😴 No users participating in this round")
        
        # Show server status
        try:
            response = requests.get("https://modic-fl-server.onrender.com/status")  # Your live Render server
            if response.status_code == 200:
                status = response.json()
                print(f"🏥 Server status: {status['unique_clients']} unique clients, "
                      f"{status['total_uploads']} total uploads, "
                      f"{status['total_aggregations']} aggregations")
        except:
            pass
        
        round_number += 1
        
        # Wait before next round (simulate real-world timing)
        wait_time = random.randint(10, 30)  # 10-30 seconds between rounds
        print(f"⏱️ Waiting {wait_time} seconds until next round...")
        await asyncio.sleep(wait_time)

if __name__ == "__main__":
    print("🚀 Real-Time Federated Learning User Simulation")
    print("Make sure the server is running: python main.py")
    print("Press Ctrl+C to stop\n")
    
    try:
        asyncio.run(simulate_real_time_fl())
    except KeyboardInterrupt:
        print("\n\n🛑 Simulation stopped by user")
        print("📊 Check server logs for complete statistics")