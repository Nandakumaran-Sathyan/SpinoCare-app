#!/usr/bin/env python3
"""
Test script to verify TFLite model prediction functionality
"""

import numpy as np
import tensorflow as tf
from PIL import Image
import io

def test_tflite_model():
    """Test the TFLite model with dummy data"""
    
    # Load TFLite model
    model_path = "modic_model.tflite"
    interpreter = tf.lite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()
    
    # Get input and output details
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    print(f"🔍 Model Analysis:")
    print(f"   Input count: {len(input_details)}")
    print(f"   Output count: {len(output_details)}")
    
    for i, detail in enumerate(input_details):
        print(f"   Input {i}: {detail['name']} - Shape: {detail['shape']} - Type: {detail['dtype']}")
    
    for i, detail in enumerate(output_details):
        print(f"   Output {i}: {detail['name']} - Shape: {detail['shape']} - Type: {detail['dtype']}")
    
    # Create dummy test images (224x224x3)
    dummy_t1 = np.random.rand(1, 224, 224, 3).astype(np.float32)
    dummy_t2 = np.random.rand(1, 224, 224, 3).astype(np.float32)
    
    print(f"\n🧪 Testing with dummy data:")
    print(f"   T1 shape: {dummy_t1.shape}")
    print(f"   T2 shape: {dummy_t2.shape}")
    
    # Set input tensors
    interpreter.set_tensor(input_details[0]['index'], dummy_t1)
    interpreter.set_tensor(input_details[1]['index'], dummy_t2)
    
    # Run inference
    interpreter.invoke()
    
    # Get output
    output = interpreter.get_tensor(output_details[0]['index'])
    
    print(f"\n🔮 Prediction Results:")
    print(f"   Raw output: {output}")
    print(f"   Output shape: {output.shape}")
    
    # Extract results
    no_modic_score = float(output[0][0])
    modic_score = float(output[0][1])
    
    print(f"   No Modic score: {no_modic_score:.4f}")
    print(f"   Modic score: {modic_score:.4f}")
    
    # Determine prediction
    if modic_score > no_modic_score:
        label = "Modic"
        confidence = modic_score
    else:
        label = "No Modic"
        confidence = no_modic_score
    
    print(f"\n✅ Final Prediction: {label} (confidence: {confidence:.4f})")
    return True

if __name__ == "__main__":
    try:
        print("🚀 Testing TFLite Model Prediction")
        print("=" * 50)
        test_tflite_model()
        print("\n🎉 Test completed successfully!")
    except Exception as e:
        print(f"❌ Test failed: {e}")
        import traceback
        traceback.print_exc()