#!/usr/bin/env python3
"""
Simple monitoring dashboard for federated learning server.
Shows real-time statistics and client activity.
"""

import requests
import time
import os
import json
from datetime import datetime

def clear_screen():
    os.system('cls' if os.name == 'nt' else 'clear')

def get_server_stats(server_url="https://modic-fl-server.onrender.com"):  # Your live Render server
    try:
        response = requests.get(f"{server_url}/status")
        if response.status_code == 200:
            return response.json()
    except Exception as e:
        return {"error": str(e)}
    return None

def format_timestamp(timestamp):
    if timestamp:
        return datetime.fromtimestamp(timestamp).strftime("%Y-%m-%d %H:%M:%S")
    return "Never"

def display_dashboard():
    while True:
        clear_screen()
        
        print("🏥 ModicAnalyzer Federated Learning Dashboard")
        print("=" * 60)
        print(f"⏰ Current time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print()
        
        stats = get_server_stats()
        
        if stats and "error" not in stats:
            print("📊 SERVER STATISTICS")
            print("-" * 30)
            print(f"🔄 Pending uploads:     {stats.get('uploads', 0)}")
            print(f"👥 Unique clients:      {stats.get('unique_clients', 0)}")
            print(f"📤 Total uploads:       {stats.get('total_uploads', 0)}")
            print(f"⚡ Total aggregations:  {stats.get('total_aggregations', 0)}")
            print(f"🗄️ Archived files:      {stats.get('archived_files', 0)}")
            print(f"🎯 Global model exists: {'✅ Yes' if stats.get('global_exists') else '❌ No'}")
            print(f"⏱️ Last aggregation:    {format_timestamp(stats.get('last_aggregation'))}")
            
            print()
            print("📈 FEDERATED LEARNING METRICS")
            print("-" * 30)
            
            if stats.get('total_aggregations', 0) > 0:
                avg_clients_per_round = stats.get('total_uploads', 0) / stats.get('total_aggregations', 1)
                print(f"📊 Avg clients/round:   {avg_clients_per_round:.1f}")
                
                if stats.get('last_aggregation'):
                    time_since_last = time.time() - stats.get('last_aggregation')
                    print(f"⌛ Time since last agg: {int(time_since_last)}s")
            else:
                print("🏁 No aggregations yet")
                
            print()
            print("🔄 REAL-TIME ACTIVITY")
            print("-" * 30)
            
            if stats.get('uploads', 0) > 0:
                print(f"🟢 {stats.get('uploads')} clients ready for aggregation")
            else:
                print("🟡 Waiting for client uploads...")
                
        elif stats and "error" in stats:
            print("❌ SERVER CONNECTION ERROR")
            print("-" * 30)
            print(f"Error: {stats['error']}")
            print("Make sure the server is running on http://localhost:8000")
        else:
            print("❌ CANNOT REACH SERVER")
            print("-" * 30)
            print("Server might be down or unreachable")
        
        print()
        print("🎮 CONTROLS")
        print("-" * 30)
        print("Press Ctrl+C to exit")
        print("Dashboard updates every 5 seconds")
        
        try:
            time.sleep(5)
        except KeyboardInterrupt:
            print("\n\n👋 Dashboard closed")
            break

if __name__ == "__main__":
    display_dashboard()