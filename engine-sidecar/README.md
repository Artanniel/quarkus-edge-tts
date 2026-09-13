# Quarkus Edge TTS – Python Sidecar

This module contains a **Java wrapper** (`PythonSidecarTtsEngine`) that calls a **FastAPI** service
implemented in Python. The sidecar implements the same Edge‑TTS protocol (WebSocket handshake,
DRM token, SSML) but runs in a separate process, allowing us to:

* **Fail‑over** from the pure‑Java engine to the sidecar when the Java implementation encounters
  protocol‑breakage or connectivity issues.
* Keep the Java module lightweight (no heavy native dependencies).

## Expected sidecar contract (FastAPI)

```
POST /synthesize
Content‑Type: application/json
{
  "text": "string",
  "voice": "pt‑BR‑FranciscaNeural",
  "rate": 1.0,
  "pitch": 1.0,
  "volume": 1.0
}
```

* **Success (200)** → raw audio bytes (`application/octet-stream`).
* **Error** → any non‑200 status; the wrapper treats it as a failure and triggers the circuit‑breaker.

## How to run the sidecar locally (for development)

```bash
# Create a virtual environment
python3 -m venv .venv && source .venv/bin/activate

# Install dependencies (fastapi, uvicorn, edge‑tts)
pip install fastapi uvicorn edge‑tts

# fastapi_sidecar.py (sample implementation) – see `src/main/python/fastapi_sidecar.py`
uvicorn fastapi_sidecar:app --host 0.0.0.0 --port 8000
```

The Java engine should be configured with the sidecar URL, e.g.:

```properties
quarkus.edge.tts.sidecar-url=http://localhost:8000/synthesize
```

---

*Created using the hermes‑agent skills for software‑engineering and Java.*