# Quarkus Quarkus‑ – MCP ‑ Service ‑ Key Features

## Overview

This module **`api‑mcp`** exposes the same OpenAI‑compatible TTS endpoints via **Hermes MCP** (Micro‑Connector Protocol). It is a thin proxy that re‑uses the existing `FailoverTtsEngine` (Java Edge engine + Python sidecar) so the same business logic is shared across the REST API and the MCP service.

## Endpoints (exposed under `/mcp/v1`)

| Method | Path | Description | Returns |
|--------|------|--------------|----------|
| `POST` | `/mcp/v1/audio/speech` | Synthesises speech, identical to the OpenAI façade. | `audio/mpeg` **or** JSON with Base64‑encoded audio (`response_format=json`). |
| `GET`  | `/mcp/v1/voices` | Lists available voices, same schema as the OpenAI API. | JSON array of voice objects. |

## How to register the MCP

Assuming the Quarkus application is running on `localhost:8080`:

```bash
hermes --profile yt-auto mcp add edge-tts-mcp \
    --url http://localhost:8080/mcp/v1
```

- `edge-tts-mcp` → name used in the Hermes registry.
- `--url` points to the base path of the MCP endpoints.

After registration, you can list all MCP servers:

```bash
hermes --profile yt-auto mcp list
```

## Configuration variables (environment)

| Variable | Purpose |
|----------|---------|
| `TRUSTED_CLIENT_TOKEN` | DRM token for the Java Edge engine. |
| `SIDE_CAR_URL` | URL of the Python sidecar (default `http://localhost:8000/synthesize`). |

Both variables are read at bean construction time, making the service fully configurable without code changes.

## Dependencies

- **Vert.x Core** – low‑level networking for the sidecar client.
- **Vert.x Web Client** – HTTP client used by `PythonSidecarTtsEngine`.
- **Quarkus Vert.x** – integration for reactive streams (`Multi`).

These are declared in `api-mcp/pom.xml`.

---

*Created with Hermes Agent using `software‑engineering` and `java` skills.*