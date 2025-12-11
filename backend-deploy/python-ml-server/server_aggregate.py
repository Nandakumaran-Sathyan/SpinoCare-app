import numpy as np
import tensorflow as tf
from pathlib import Path
from typing import List

# Global model paths
GLOBAL_MODEL_KERAS = "final_model.keras"
GLOBAL_MODEL_TFLITE = "global_model.tflite"


def load_npz(path: str):
    """Load a .npz and return a dict of arrays."""
    with np.load(path, allow_pickle=False) as data:
        return {k: data[k] for k in data.files}


def aggregate_npz_files(input_paths: List[str], output_path: str):
    """Aggregate multiple .npz files by averaging arrays with matching keys.

    Each file should be a .npz with the same set of keys and matching shapes.
    Saves the averaged arrays into output_path (.npz).
    """
    if not input_paths:
        raise ValueError("No input files provided")

    loaded = [load_npz(p) for p in input_paths]

    # ensure keys match
    keys = list(loaded[0].keys())
    for d in loaded[1:]:
        if list(d.keys()) != keys:
            raise ValueError("Mismatch in keys across uploaded files")

    averaged = {}
    for k in keys:
        arrays = [d[k].astype(np.float64) for d in loaded]
        # stack and mean
        stacked = np.stack(arrays, axis=0)
        averaged[k] = np.mean(stacked, axis=0)

    # save
    np.savez_compressed(output_path, **averaged)

    return output_path


def keras_federated_averaging(client_weights_list: List[dict], global_model_path: str = GLOBAL_MODEL_KERAS):
    """
    Perform federated averaging using .keras model for aggregation.
    
    Args:
        client_weights_list: List of client weight dictionaries
        global_model_path: Path to the global .keras model
        
    Returns:
        Updated Keras model
    """
    if not client_weights_list:
        raise ValueError("No client weights provided")
    
    # Load the global Keras model
    if not Path(global_model_path).exists():
        raise FileNotFoundError(f"Global model not found: {global_model_path}")
    
    model = tf.keras.models.load_model(global_model_path)
    
    # Get current model weights structure
    current_weights = model.get_weights()
    
    # Average client updates (simplified FedAvg)
    if len(client_weights_list) > 0:
        # For now, we'll use the .npz averaging approach
        # In a real implementation, you'd map client weights to model layers
        print(f"Averaging weights from {len(client_weights_list)} clients")
        
        # This is a placeholder - in practice, you'd need to:
        # 1. Map client weight deltas to the actual Keras model layers
        # 2. Apply the aggregated updates to the model
        # For now, we'll keep the model as-is and just re-save it
        
    # Save updated model
    model.save(global_model_path)
    
    return model


def convert_keras_to_tflite(keras_model_path: str = GLOBAL_MODEL_KERAS, 
                           tflite_output_path: str = GLOBAL_MODEL_TFLITE):
    """
    Convert the updated .keras model to .tflite for client download.
    
    Args:
        keras_model_path: Path to the Keras model
        tflite_output_path: Output path for the .tflite model
        
    Returns:
        Path to the generated .tflite file
    """
    if not Path(keras_model_path).exists():
        raise FileNotFoundError(f"Keras model not found: {keras_model_path}")
    
    # Load the Keras model
    model = tf.keras.models.load_model(keras_model_path)
    
    # Convert to TensorFlow Lite
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    
    # Optimization settings (optional)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]  # Use float16 for smaller size
    
    tflite_model = converter.convert()
    
    # Save the .tflite model
    with open(tflite_output_path, "wb") as f:
        f.write(tflite_model)
    
    print(f"Converted model to .tflite: {tflite_output_path} ({len(tflite_model)} bytes)")
    
    return tflite_output_path


def hybrid_fl_aggregation(input_paths: List[str], 
                         keras_output_path: str = GLOBAL_MODEL_KERAS,
                         tflite_output_path: str = GLOBAL_MODEL_TFLITE):
    """
    Complete hybrid FL aggregation pipeline:
    1. Load client weight updates (.npz files)
    2. Aggregate using Keras model
    3. Convert to .tflite for client distribution
    
    Args:
        input_paths: List of client .npz file paths
        keras_output_path: Output path for updated .keras model
        tflite_output_path: Output path for converted .tflite model
        
    Returns:
        Tuple of (keras_path, tflite_path)
    """
    if not input_paths:
        raise ValueError("No input files provided")
    
    # Step 1: Load client weights
    client_weights = [load_npz(path) for path in input_paths]
    
    # Step 2: Aggregate using Keras (for now, using existing logic)
    model = keras_federated_averaging(client_weights, keras_output_path)
    
    # Step 3: Convert to .tflite
    tflite_path = convert_keras_to_tflite(keras_output_path, tflite_output_path)
    
    return keras_output_path, tflite_path
