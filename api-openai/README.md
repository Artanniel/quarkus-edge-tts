# Quarkus Edge TTS – OpenAI compatible API

This module provides an **OpenAI‑style** REST façade for the Edge TTS service.

## Endpoints

| Method | Path                     | Description                                                  | Returns |
|--------|--------------------------|--------------------------------------------------------------|---------|
| `POST` | `/v1/audio/speech`      | Synthesises speech from a JSON payload (compatible with OpenAI). | `audio/mpeg` **or** JSON with Base64‑encoded audio (`response_format=json`). |
| `GET`  | `/v1/voices`             | Lists supported voices in the OpenAI schema.                | JSON array of voice objects. |

## Request payload (minimal subset)
```json
{
  "model": "edge-tts",           // ignored – we only support the Edge engine
  "input": "Olá, mundo!",
  "voice": "pt-BR-FranciscaNeural",
  "speed": 1.0,                     // maps to `rate` in the core engine
  "response_format": "audio"       // "audio" (raw MP3) or "json" (Base64 string)
}
```

## Usage example (curl)
```bash
# Raw MP3 (default)
curl -X POST -H "Content-Type: application/json" \
     -d '{"model":"edge-tts","input":"Olá, mundo!","voice":"pt-BR-FranciscaNeural"}' \
     http://localhost:8080/v1/audio/speech --output speech.mp3

# JSON with Base64 audio
curl -X POST -H "Content-Type: application/json" \
     -d '{"model":"edge-tts","input":"Olá, mundo!","voice":"pt-BR-FranciscaNeural","response_format":"json"}' \
     http://localhost:8080/v1/audio/speech | jq
```

## Configuration (environment variables)
```
TRUSTED_CLIENT_TOKEN   # Edge DRM token – used by the primary Java engine.
SIDE_CAR_URL          # URL of the Python sidecar (default: http://localhost:8000/synthesize)
```

The **fail‑over** strategy (`FailoverTtsEngine`) first tries the Java engine; on any exception it falls back to the Python sidecar.

---

*Created via the Hermes Agent using the `software‑engineering` and `java` skill set.*