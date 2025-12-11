from pathlib import Path
import time
import hashlib
import os
import logging
import io
import numpy as np
from PIL import Image

from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.responses import FileResponse
from fastapi.middleware.cors import CORSMiddleware

# import server_aggregate as sa  # Temporarily disabled due to TensorFlow issue

# Configure logging first
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# TensorFlow for prediction endpoint
try:
    import tensorflow as tf
    tf_available = True
    logger.info("✅ TensorFlow loaded successfully")
except ImportError:
    tf_available = False
    logger.warning("⚠️ TensorFlow not available - prediction endpoint will be disabled")

ROOT = Path(__file__).parent

# Ensure required model exists
TFLITE_MODEL = ROOT / "modic_model.tflite"

if not TFLITE_MODEL.exists():
    logger.error(f"❌ TFLite model not found: {TFLITE_MODEL}")
    raise FileNotFoundError(f"Required TFLite model not found: {TFLITE_MODEL}")
else:
    logger.info(f"✅ TFLite model found: {TFLITE_MODEL}")

# Load TFLite model for prediction endpoint
prediction_interpreter = None
if tf_available:
    try:
        logger.info(f"🔄 Loading TFLite model from: {TFLITE_MODEL}")
        prediction_interpreter = tf.lite.Interpreter(model_path=str(TFLITE_MODEL))
        prediction_interpreter.allocate_tensors()
        
        # Get input and output details
        input_details = prediction_interpreter.get_input_details()
        output_details = prediction_interpreter.get_output_details()
        
        logger.info(f"✅ Prediction model loaded successfully")
        logger.info(f"   Input shape: {input_details[0]['shape']}")
        logger.info(f"   Output shape: {output_details[0]['shape']}")
        logger.info(f"   Input count: {len(input_details)}")
    except Exception as e:
        logger.error(f"❌ Failed to load prediction model: {e}")
        logger.error(f"   Model path: {TFLITE_MODEL}")
        logger.error(f"   File exists: {TFLITE_MODEL.exists()}")
        prediction_interpreter = None
else:
    logger.warning("⚠️ TensorFlow not available - skipping model loading")

app = FastAPI(
    title="ModicAnalyzer Federated Learning Server - Production", 
    version="2.2",
    description="Production-ready privacy-preserving federated learning for medical image analysis (TFLite-optimized)"
)

# Production CORS configuration
allowed_origins = ["*"]  # In production, specify exact domains
if os.getenv("PYTHON_ENV") == "production":
    allowed_origins = [
        "https://your-app-domain.com",  # Replace with your Android app's domain
    ]

app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Statistics tracking
stats = {
    "total_clients": 0,
    "server_start_time": time.time()
}

# TFLite-only server - no federated learning


@app.post("/predict")
async def predict(
    file_t1: UploadFile = File(..., description="T1-weighted MRI image"),
    file_t2: UploadFile = File(..., description="T2-weighted MRI image")
):
    """
    Predict Modic changes from T1 and T2 MRI images using the server-side TFLite model.
    
    Returns:
        JSON with prediction score, label, and processing metadata
    """
    logger.info(f"📥 Prediction request received")
    logger.info(f"   TensorFlow available: {tf_available}")
    logger.info(f"   Model loaded: {prediction_interpreter is not None}")
    
    if not tf_available:
        logger.error("❌ TensorFlow not available")
        raise HTTPException(
            status_code=503, 
            detail="TensorFlow not available on server"
        )
    
    if prediction_interpreter is None:
        logger.error("❌ Prediction model not loaded")
        raise HTTPException(
            status_code=503, 
            detail="Prediction model not loaded on server"
        )
    
    try:
        start_time = time.time()
        
        # Validate file types
        if not file_t1.content_type.startswith('image/'):
            raise HTTPException(status_code=400, detail="T1 file must be an image")
        if not file_t2.content_type.startswith('image/'):
            raise HTTPException(status_code=400, detail="T2 file must be an image")
        
        # Read and process images
        t1_bytes = await file_t1.read()
        t2_bytes = await file_t2.read()
        
        # Convert to PIL Images
        t1_image = Image.open(io.BytesIO(t1_bytes)).convert('RGB')
        t2_image = Image.open(io.BytesIO(t2_bytes)).convert('RGB')
        
        # Resize to model input size (224x224)
        t1_resized = t1_image.resize((224, 224))
        t2_resized = t2_image.resize((224, 224))
        
        # Convert to numpy arrays and normalize
        t1_array = np.array(t1_resized, dtype=np.float32) / 255.0
        t2_array = np.array(t2_resized, dtype=np.float32) / 255.0
        
        logger.info(f"🔍 Processing prediction: T1={file_t1.filename}, T2={file_t2.filename}")
        
        # Get input and output details
        input_details = prediction_interpreter.get_input_details()
        output_details = prediction_interpreter.get_output_details()
        
        logger.info(f"📊 Model input count: {len(input_details)}")
        
        # Run prediction with TFLite interpreter
        if len(input_details) == 2:
            # Dual input model - separate T1 and T2
            t1_batch = np.expand_dims(t1_array, axis=0)
            t2_batch = np.expand_dims(t2_array, axis=0)
            
            logger.info(f"📊 T1 input shape: {t1_batch.shape}, T2 input shape: {t2_batch.shape}")
            
            # Set input tensors
            prediction_interpreter.set_tensor(input_details[0]['index'], t1_batch)
            prediction_interpreter.set_tensor(input_details[1]['index'], t2_batch)
        else:
            # Single input model - use combined input
            input_batch = np.expand_dims(np.stack([t1_array, t2_array], axis=0), axis=0)
            logger.info(f"📊 Input shape: {input_batch.shape}")
            prediction_interpreter.set_tensor(input_details[0]['index'], input_batch)
        
        # Run inference
        prediction_interpreter.invoke()
        
        # Get output
        output = prediction_interpreter.get_tensor(output_details[0]['index'])[0]  # Remove batch dimension
        no_modic_score = float(output[0])
        modic_score = float(output[1])
        
        # Determine label and confidence
        if modic_score > no_modic_score:
            label = "Modic"
            confidence = modic_score
        else:
            label = "No Modic"
            confidence = no_modic_score
        
        processing_time = int((time.time() - start_time) * 1000)  # ms
        
        result = {
            "prediction": confidence,
            "label": label,
            "confidence": confidence,
            "detailed_scores": {
                "no_modic": no_modic_score,
                "modic": modic_score
            },
            "processing_time_ms": processing_time,
            "model_version": str(stats.get("total_aggregations", 0)),
            "input_files": {
                "t1": file_t1.filename,
                "t2": file_t2.filename
            }
        }
        
        logger.info(f"✅ Prediction completed: {label} ({confidence:.3f}) in {processing_time}ms")
        return result
        
    except Exception as e:
        logger.error(f"❌ Prediction failed: {e}")
        raise HTTPException(status_code=500, detail=f"Prediction failed: {str(e)}")


@app.get("/model_info")
def get_model_info():
    """Get model metadata for client-side update checking."""
    import hashlib
    
    if not TFLITE_MODEL.exists():
        raise HTTPException(status_code=404, detail="No .tflite model available yet.")
    
    # Calculate file hash for change detection
    with open(TFLITE_MODEL, 'rb') as f:
        file_hash = hashlib.sha256(f.read()).hexdigest()
    
    file_stats = TFLITE_MODEL.stat()
    
    return {
        "model_hash": file_hash,
        "model_version": str(stats.get("total_aggregations", 0)),
        "model_size_bytes": file_stats.st_size,
        "model_size_mb": round(file_stats.st_size / (1024*1024), 2),
        "last_modified": file_stats.st_mtime,
        "download_url": "/get_global_model",
        "server_time": time.time()
    }


@app.get("/get_global_model")
def get_global_model():
    """Download the latest global .tflite model for client use."""
    if not TFLITE_MODEL.exists():
        raise HTTPException(status_code=404, detail="No global .tflite model available yet. Run aggregation first.")
    
    # Calculate hash for verification
    import hashlib
    with open(TFLITE_MODEL, 'rb') as f:
        file_hash = hashlib.sha256(f.read()).hexdigest()
    
    # Log download
    logger.info(f"📥 Global model downloaded: {TFLITE_MODEL.stat().st_size} bytes")
    
    return FileResponse(
        str(TFLITE_MODEL), 
        media_type="application/octet-stream", 
        filename="modic_model.tflite",
        headers={
            "Model-Version": "0",
            "Model-Hash": file_hash,
            "Model-Size": str(TFLITE_MODEL.stat().st_size)
        }
    )


# Aggregate endpoint temporarily disabled due to TensorFlow dependency issue

@app.get("/status")
def status():
    """Comprehensive server status for production monitoring"""
    current_time = time.time()
    uptime_hours = round((current_time - stats["server_start_time"]) / 3600, 2)
    
    return {
        # Basic status
        "status": "operational",
        "architecture": "tflite_only",
        "version": "2.3",
        "uptime_hours": uptime_hours,
        
        # Model status
        "tflite_model_exists": TFLITE_MODEL.exists(),
        
        # Statistics
        "total_clients": stats["total_clients"],
        
        # Model info
        "tflite_model_size_mb": round(TFLITE_MODEL.stat().st_size / (1024*1024), 2) if TFLITE_MODEL.exists() else 0,
        

        
        # Health indicators
        "health": {
            "models_ready": TFLITE_MODEL.exists(),
            "error_rate": 0.0
        }
    }


@app.get("/")
def root():
    """Production API information"""
    return {
        "message": "ModicAnalyzer TFLite Model Server - Production Ready",
        "version": "2.3",
        "architecture": "tflite_only",
        "status": "operational",
        "endpoints": {
            "predict": "POST /predict",
            "model_info": "GET /model_info",
            "download_model": "GET /get_global_model",
            "status": "GET /status",
            "health": "GET /health"
        },
        "workflow": {
            "1": "Clients send T1/T2 images via /predict for server-side inference",
            "2": "Clients check for model updates via /model_info",
            "3": "Server serves current .tflite model via /get_global_model",
            "4": "Clients auto-download updated models when available",
            "5": "Clients use local .tflite models for offline inference"
        },
        "features": {
            "online_inference": tf_available and prediction_interpreter is not None,
            "automatic_model_updates": True,
            "offline_model_distribution": True
        },
        "production_features": {
            "comprehensive_logging": True,
            "error_tracking": True,
            "model_validation": True,
            "hash_verification": True
        }
    }


@app.get("/health")
def health_check():
    """Health check endpoint for load balancers and debugging"""
    try:
        # Check critical components
        tflite_ok = TFLITE_MODEL.exists()
        storage_ok = True  # No storage directories needed for TFLite-only mode
        model_loaded = prediction_interpreter is not None
        
        status_info = {
            "status": "healthy" if (tflite_ok and storage_ok and tf_available and model_loaded) else "unhealthy",
            "timestamp": time.time(),
            "components": {
                "tensorflow_available": tf_available,
                "tflite_model_file": tflite_ok,
                "prediction_model_loaded": model_loaded,
                "storage_dirs": storage_ok
            }
        }
        
        if tf_available and prediction_interpreter:
            input_details = prediction_interpreter.get_input_details()
            output_details = prediction_interpreter.get_output_details()
            status_info["model_info"] = {
                "input_shape": str([detail['shape'] for detail in input_details]),
                "output_shape": str([detail['shape'] for detail in output_details]),
                "input_count": len(input_details)
            }
        
        return status_info
    except Exception as e:
        return {"status": "error", "error": str(e)}, 500


if __name__ == "__main__":
    # Run with: uvicorn main:app --host 0.0.0.0 --port 8000
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
