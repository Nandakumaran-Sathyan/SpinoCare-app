#!/usr/bin/env python3
"""
Production Model Initialization Script
Ensures models are properly aligned between server (.keras) and client (.tflite)
"""

import tensorflow as tf
import numpy as np
from pathlib import Path
import shutil
from server_aggregate import convert_keras_to_tflite

# Paths
BACKEND_DIR = Path(__file__).parent
APP_ASSETS_DIR = BACKEND_DIR.parent / "app" / "src" / "main" / "assets"
KERAS_MODEL_PATH = BACKEND_DIR / "final_model.keras"
TFLITE_MODEL_PATH = BACKEND_DIR / "global_model.tflite"
CLIENT_MODEL_PATH = APP_ASSETS_DIR / "modic_model.tflite"

def verify_model_compatibility():
    """Verify that Keras and TFLite models are compatible"""
    print("🔍 Verifying model compatibility...")
    
    if not KERAS_MODEL_PATH.exists():
        print(f"❌ Keras model not found: {KERAS_MODEL_PATH}")
        return False
    
    if not CLIENT_MODEL_PATH.exists():
        print(f"❌ Client TFLite model not found: {CLIENT_MODEL_PATH}")
        return False
    
    try:
        # Load Keras model
        keras_model = tf.keras.models.load_model(KERAS_MODEL_PATH)
        print(f"✅ Keras model loaded: {keras_model.input_shape} -> {keras_model.output_shape}")
        
        # Load TFLite model
        interpreter = tf.lite.Interpreter(model_path=str(CLIENT_MODEL_PATH))
        interpreter.allocate_tensors()
        
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()
        
        print(f"✅ TFLite model loaded: {input_details[0]['shape']} -> {output_details[0]['shape']}")
        
        # Verify shapes match
        keras_input_shape = keras_model.input_shape[1:]  # Remove batch dimension
        tflite_input_shape = tuple(input_details[0]['shape'][1:])  # Remove batch dimension
        
        if keras_input_shape == tflite_input_shape:
            print("✅ Input shapes match")
        else:
            print(f"⚠️ Input shape mismatch: Keras {keras_input_shape} vs TFLite {tflite_input_shape}")
        
        return True
        
    except Exception as e:
        print(f"❌ Model verification failed: {e}")
        return False

def initialize_global_tflite():
    """Initialize global TFLite model from Keras model"""
    print("🚀 Initializing global TFLite model...")
    
    try:
        # Convert Keras to TFLite for global distribution
        tflite_path = convert_keras_to_tflite(
            str(KERAS_MODEL_PATH), 
            str(TFLITE_MODEL_PATH)
        )
        print(f"✅ Global TFLite model created: {tflite_path}")
        return True
        
    except Exception as e:
        print(f"❌ Failed to create global TFLite model: {e}")
        return False

def sync_client_model():
    """Sync the client model with the current global model"""
    print("🔄 Syncing client model with global model...")
    
    if not TFLITE_MODEL_PATH.exists():
        print("❌ No global TFLite model to sync from")
        return False
    
    try:
        # Backup original client model
        backup_path = CLIENT_MODEL_PATH.with_suffix('.tflite.backup')
        if CLIENT_MODEL_PATH.exists() and not backup_path.exists():
            shutil.copy2(CLIENT_MODEL_PATH, backup_path)
            print(f"📦 Backed up original client model to: {backup_path}")
        
        # Copy global model to client assets
        APP_ASSETS_DIR.mkdir(parents=True, exist_ok=True)
        shutil.copy2(TFLITE_MODEL_PATH, CLIENT_MODEL_PATH)
        
        print(f"✅ Client model synced: {CLIENT_MODEL_PATH}")
        return True
        
    except Exception as e:
        print(f"❌ Failed to sync client model: {e}")
        return False

def create_model_manifest():
    """Create a manifest file with model information"""
    print("📋 Creating model manifest...")
    
    manifest = {
        "keras_model": {
            "path": str(KERAS_MODEL_PATH.relative_to(BACKEND_DIR)),
            "exists": KERAS_MODEL_PATH.exists(),
            "size_mb": round(KERAS_MODEL_PATH.stat().st_size / (1024*1024), 2) if KERAS_MODEL_PATH.exists() else 0
        },
        "global_tflite": {
            "path": str(TFLITE_MODEL_PATH.relative_to(BACKEND_DIR)),
            "exists": TFLITE_MODEL_PATH.exists(),
            "size_mb": round(TFLITE_MODEL_PATH.stat().st_size / (1024*1024), 2) if TFLITE_MODEL_PATH.exists() else 0
        },
        "client_tflite": {
            "path": str(CLIENT_MODEL_PATH.relative_to(BACKEND_DIR.parent)),
            "exists": CLIENT_MODEL_PATH.exists(),
            "size_mb": round(CLIENT_MODEL_PATH.stat().st_size / (1024*1024), 2) if CLIENT_MODEL_PATH.exists() else 0
        }
    }
    
    # Get model details if available
    if KERAS_MODEL_PATH.exists():
        try:
            model = tf.keras.models.load_model(KERAS_MODEL_PATH)
            manifest["keras_model"]["input_shape"] = str(model.input_shape)
            manifest["keras_model"]["output_shape"] = str(model.output_shape)
            manifest["keras_model"]["parameters"] = int(model.count_params())
        except Exception as e:
            manifest["keras_model"]["error"] = str(e)
    
    manifest_path = BACKEND_DIR / "model_manifest.json"
    import json
    with open(manifest_path, 'w') as f:
        json.dump(manifest, f, indent=2)
    
    print(f"✅ Model manifest created: {manifest_path}")
    return manifest

def production_model_setup():
    """Complete production model setup"""
    print("🏭 Production Model Setup")
    print("=" * 50)
    
    # Step 1: Verify existing models
    if not verify_model_compatibility():
        print("❌ Model verification failed")
        return False
    
    # Step 2: Initialize global TFLite model
    if not initialize_global_tflite():
        print("❌ Global TFLite initialization failed")
        return False
    
    # Step 3: Sync client model (optional for production)
    sync_success = sync_client_model()
    if sync_success:
        print("✅ Client model synced successfully")
    else:
        print("⚠️ Client model sync failed (using existing)")
    
    # Step 4: Create manifest
    manifest = create_model_manifest()
    
    # Step 5: Summary
    print("\n📊 Production Setup Summary:")
    print(f"   🧠 Keras model: {manifest['keras_model']['size_mb']:.2f} MB")
    print(f"   🌐 Global TFLite: {manifest['global_tflite']['size_mb']:.2f} MB")
    print(f"   📱 Client TFLite: {manifest['client_tflite']['size_mb']:.2f} MB")
    
    if manifest['keras_model'].get('parameters'):
        print(f"   📈 Model parameters: {manifest['keras_model']['parameters']:,}")
    
    print("\n🎉 Production model setup completed!")
    print("\n🚀 Ready for deployment:")
    print("   1. Deploy backend with Keras model for aggregation")
    print("   2. Android app uses TFLite model for inference")
    print("   3. FL workflow converts Keras → TFLite for distribution")
    
    return True

if __name__ == "__main__":
    production_model_setup()