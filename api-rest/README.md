# Quarkus Edge TTS – REST API

This module **exposes** the TTS service via HTTP.

## Endpoints

| Method | Path | Description | Returns |
|--------|------|-------------|---------|
| `GET`  | `/v1/voices` | List the unified voice catalog (Edge + custom placeholder). | JSON array of `Voice` objects.
| `POST` | `/v1/tts` | Synthesize text. Body JSON with `text`, `voice`, optional `rate`, `pitch`, `volume`. Query param `stream=true` returns Server‑Sent Events (SSE) of raw audio bytes; otherwise returns a single MP3 payload. | `audio/mpeg` or `text/event-stream`.

## Usage example (curl)

```bash
# List voices
curl -s http://localhost:8080/v1/voices | jq

# Synthesize (non‑streaming)
curl -X POST -H "Content-Type: application/json" \
     -d '{"text":"Olá, World!","voice":"pt-BR-FranciscaNeural"}' \
     http://localhost:8080/v1/tts --output speech.mp3

# Synthesize with streaming (SSE)
curl -N -X POST -H "Content-Type: application/json" \
     -d '{"text":"Olá, World!","voice":"pt-BR-FranciscaNeural"}' \
     "http://localhost:8080/v1/tts?stream=true" | cat > speech.raw
```

## Configuration

Add to `application.properties` (or env var) the Edge trusted client token:

```properties
# Edge trusted client token – read from the environment variable TRUSTED_CLIENT_TOKEN
quarkus.edge.tts.trusted-token=${TRUSTED_CLIENT_TOKEN}
```

The `JavaEdgeTtsEngine` reads the token from `System.getenv("TRUSTED_CLIENT_TOKEN")`.

---

*Created via the Hermes Agent using the `software‑engineering` and `java` skill set.*