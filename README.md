# Quarkus Edge TTS

**Projeto:** façade para o serviço **Edge‑TTS** (https://edge‑tts.com) usando **Quarkus**.

- **M0‑M2** – Core domain (token, chunker, catalog) + Java Edge engine.
- **M3** – MCP façade (Hermes MCP) – expondo `/mcp/v1/*`.
- **M4** – API OpenAI‑compatible (`/v1/audio/*`).
- **M5** – CI/CD (GitHub Actions) + Helm chart.
- **M6** – Voice‑cloning stub (F5‑TTS).
- **M7** – Production‑ready (native image, Docker, Helm).
- **M8** – Realtime PCM streaming.

## Como rodar
```bash
# Build native image (requires GraalVM)
./mvnw -Pnative package

# Run locally
./target/quarkus-edge-tts-runner
```

## Endpoints
| Path | Method | Description |
|------|--------|-------------|
| `/v1/audio/speech` | POST | OpenAI‑compatible TTS (MP3 or Base64 JSON). |
| `/v1/voices` | GET | Lista de vozes disponíveis. |
| `/v1/audio/pcm` | GET | **M8** – stream raw PCM (default 24 kHz). |
| `/mcp/v1/audio/speech` | POST | Same as OpenAI endpoint, exposed via Hermes MCP. |
| `/mcp/v1/voices` | GET | Voice list via MCP. |

## Configuração (variáveis de ambiente)
- `TRUSTED_CLIENT_TOKEN` – token de acesso ao Edge‑TTS.
- `SIDE_CAR_URL` – URL do sidecar FastAPI (ex.: `http://localhost:8000/synthesize`).
- `CLONING_SERVICE_URL` – URL do serviço de voice‑cloning (F5‑TTS).
- `PCM_SAMPLE_RATE` – taxa de amostragem para PCM (default 24000).

## Deploy (Kubernetes)
```bash
helm upgrade --install quarkus-edge-tts ./helm \
  --set image.repository=your-registry/quarkus-edge-tts \
  --set image.tag=latest \
  --set env.TRUSTED_CLIENT_TOKEN=$TRUSTED_CLIENT_TOKEN \
  --set env.SIDE_CAR_URL=$SIDE_CAR_URL \
  --set env.CLONING_SERVICE_URL=$CLONING_SERVICE_URL
```

## CI/CD
GitHub Actions (`.github/workflows/ci.yml`) compila o projeto, gera a imagem nativa e publica o artefato binário.

---
*Construído com ♥ por Artanniel Fortes*