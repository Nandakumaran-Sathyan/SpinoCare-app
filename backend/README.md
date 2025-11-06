# Federated Learning Backend (minimal)

This folder contains a minimal FastAPI-based backend to experiment with federated learning for the `ModicAnalyzer` Android app.

Files:

- `main.py` — FastAPI app with endpoints to upload client weight `.npz` files, trigger aggregation, and download the latest aggregated weights.
- `server_aggregate.py` — simple federated averaging helper that loads `.npz` files and averages arrays by key.
- `requirements.txt` — python dependencies.
- `storage/` — created at runtime. Contains `uploads/`, `global/`, `archive/`.

Quick start (local):

1. Create and activate a Python virtualenv.

```powershell
python -m venv .venv; .\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

2. Run the server:

```powershell
# from this folder
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

3. Endpoints:

- `POST /upload_weights` — multipart form: `client_id` string, file (binary .npz). Saves into `storage/uploads/`.
- `POST /aggregate` — triggers aggregation of all `.npz` files in `storage/uploads/`. Saves averaged result to `storage/global/latest_weights.npz`.
- `GET /latest_weights` — download the latest aggregated `.npz` file.
- `GET /status` — quick status with counts.

Notes & next steps:

- The server is intentionally minimal. In production you should add authentication, HTTPS, secure aggregation, and robust validation.
- The Android client should convert model weights into an `.npz` (or binary protobuf), upload them, and download the aggregated `.npz` to reconstruct the new model.
- Consider using frameworks such as Flower (https://flower.dev) for a full FL lifecycle and client SDKs.