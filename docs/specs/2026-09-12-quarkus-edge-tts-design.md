# Design: Quarkus Edge-TTS Facade

**Date:** 2026-09-12
**Status:** Approved
**Repo:** `Artanniel/quarkus-edge-tts`

## 1. Purpose

A Quarkus application that encapsulates Microsoft Edge's read-aloud text-to-speech
service behind three consumable interfaces: a native REST API, an MCP server, and an
OpenAI-compatible audio endpoint. It additionally supports custom voices cloned from a
reference audio sample and its transcript.

The service is a local API / wrapper. It owns protocol details, voice catalog
management, chunking, caching and failover so that consumers never deal with
WebSocket framing, DRM tokens or text splitting.

## 2. Feasibility Findings

The Edge TTS protocol was verified empirically before this design was written. A
throwaway pure-Java probe (JDK `java.net.http.WebSocket`, zero third-party
dependencies) reproduced the full flow:

| Check | Result |
|---|---|
| `Sec-MS-GEC` token port | Exact match against `edge_tts/drm.py` on 7 vectors including live timestamp |
| WSS handshake | Success |
| Message sequence | `turn.start` → `response` → `audio.metadata` → `turn.end` |
| Audio returned | 22,752 bytes, valid MP3, 48 kbps / 24 kHz mono, 3.79 s |
| Time to first byte | ~1.6–1.7 s |
| Voice catalog | Plain HTTPS GET → 322 voices, 142 locales |
| Concurrency | 4 simultaneous connections, no latency degradation |
| Custom voice names | Rejected: `close 1007 Unsupported voice` |

**Conclusions that drive this design:**

1. A pure-Java engine is viable. The complete protocol surface is ~250–300 lines of
   production code with no third-party dependencies.
2. The Edge voice catalog is a **closed set**. Voice cloning is impossible on this
   endpoint and requires a separate engine.
3. The real risk is not implementation difficulty but **maintenance ownership** of the
   protocol. Microsoft introduced `Sec-MS-GEC` in November 2024 and broke every client
   at once. With the Python `edge-tts` package, recovery is `pip install -U`. With a
   pure-Java engine, the reverse engineering is ours.

Risk 3 is the reason the synthesis engine is a port with more than one adapter.

## 3. Architecture

Hexagonal (ports and adapters), multi-module Maven. Every adapter is independently
testable and independently deployable-in-principle.

```
core/            Domain model, TtsEngine port, SynthesisService, VoiceCatalog
                 No framework dependencies.
engine-java/     Vert.x WebSocket client, DRM, frame codec, SSML builder, chunker
engine-sidecar/  @RestClient adapter -> FastAPI + python edge-tts
engine-cloning/  @RestClient adapter -> FastAPI + F5-TTS
api-rest/        POST /v1/tts, GET /v1/voices, voice profile CRUD
api-openai/      POST /v1/audio/speech, GET /v1/models
api-mcp/         quarkus-mcp-server tools
app/             Bootstrap, configuration, observability, packaging
```

### 3.1 The port

```java
public interface TtsEngine {
    Multi<SynthesisEvent> synthesize(SynthesisRequest request);
    Uni<List<Voice>> listVoices();
    boolean supports(VoiceRef voice);
}
```

`SynthesisEvent` is a sealed interface: `AudioChunk(byte[] data)` or
`BoundaryEvent(long offset, long duration, String text, BoundaryType type)`.

Inbound adapters never reference a concrete engine. They depend only on
`SynthesisService`.

### 3.2 Engine selection

`SynthesisService` resolves the requested voice through `VoiceCatalog`, then routes:

| Voice reference | Engine |
|---|---|
| `pt-BR-FranciscaNeural` (Edge catalog) | `JavaEdgeTtsEngine` (default) |
| `pt-BR-FranciscaNeural`, Java engine circuit open | `PythonSidecarTtsEngine` |
| `custom:<profile-id>` | `CloningTtsEngine` |

The Edge engine is selectable by configuration (`edgetts.engine=java|sidecar`) so a
protocol break can be mitigated by a ConfigMap change rather than a rebuild. All
protocol constants (`TRUSTED_CLIENT_TOKEN`, `CHROMIUM_FULL_VERSION`, base URLs) are
externalized configuration, never compiled-in literals, for the same reason.

## 4. Data Flow

Synthesis request:

1. Inbound adapter maps its wire format to `SynthesisRequest`.
2. `SynthesisService` validates the request.
3. Cache lookup, content-addressed on `hash(text, voice, rate, pitch, volume, format)`.
4. On miss: `VoiceCatalog` resolves the voice reference and selects the engine.
5. Text is split by the chunker into byte-bounded segments.
6. `engine.synthesize()` is invoked per segment; the resulting `Multi` streams are
   concatenated with CBR offset compensation applied across segment boundaries so
   boundary metadata timestamps remain monotonic.
7. Audio is written to cache and streamed to the caller.

## 5. Components

### 5.1 DRM (`engine-java`)

`Sec-MS-GEC` generation: take the current Unix timestamp adjusted by the tracked clock
skew, add the Windows epoch offset (11644473600), floor to a 300-second boundary,
convert to 100-nanosecond ticks, concatenate the trusted client token, SHA-256, upper
hex.

Clock skew correction: on HTTP 403, parse the RFC 2616 `Date` response header, set
`clockSkewSeconds = serverTime - clientTime`, and retry once.

Golden tests pin the 7 vectors verified against the Python reference implementation.

### 5.2 Chunker (`engine-java`)

Splits text to a maximum byte length per WebSocket request. The split point must
satisfy three constraints simultaneously:

1. Prefer the rightmost newline, else the rightmost space, within the limit.
2. Never split inside a multi-byte UTF-8 sequence.
3. Never split inside an XML entity (`&amp;` and friends).

This is the component most likely to harbour defects and is specified for
property-based testing rather than example-based testing.

### 5.3 Voice catalog (`core`)

Merges the Edge catalog (fetched from the voices endpoint, cached with TTL) with
custom voice profiles from the local store. Exposes a unified list. Custom voices are
namespaced `custom:<id>` to guarantee no collision with Edge short names.

### 5.4 Voice profiles (`core`, M6)

Aggregate:

```
VoiceProfile {
  id, displayName, locale, ownerId,
  status: PENDING | VALIDATING | PROCESSING | READY | FAILED,
  referenceAudioRef, referenceTranscript, audioChecksum,
  consent: ConsentRecord,
  createdAt, updatedAt, failureReason
}
```

`ConsentRecord` is a required field of the aggregate, not optional metadata. Voice
recordings are biometric data under GDPR Article 9, and synthetic speech imitating a
real person falls under EU AI Act Article 50 transparency obligations. The record
captures who attested, when, and to what.

Enrollment is asynchronous. `POST /v1/voices/custom` returns `202 Accepted` with a
profile id; the caller polls `GET /v1/voices/custom/{id}` for status.

Validation and preprocessing pipeline:

| Step | Rule |
|---|---|
| Duration | 5–15 s (F5-TTS reference window) |
| Channels | Downmix to mono |
| Sample rate | Resample to 24 kHz |
| Clipping | Reject if clipped sample ratio exceeds threshold |
| Silence | Trim leading and trailing |
| Loudness | Normalize |
| Transcript consistency | Words-per-second heuristic against measured duration |

Audio decoding and transformation is delegated to ffmpeg invoked as a pipeline step.
Java owns validation rules, the state machine, storage and audit.

### 5.5 Cloning engine (`engine-cloning`, M6)

F5-TTS (MIT licence, actively maintained) behind a FastAPI sidecar, selected because
its inference signature is `(ref_audio, ref_text, gen_text)`, which matches the
enrollment inputs directly.

XTTS-v2 was rejected: the `coqui-ai/TTS` repository is MPL-2.0 but the XTTS-v2 model
weights are published under the Coqui Public Model License, which prohibits commercial
use, and the project has been unmaintained since August 2024.

`cloning.device=cpu|cuda` is configurable. CPU inference is an order of magnitude
slower than the Edge engine and is acceptable only for non-interactive use.

## 6. Interfaces

### 6.1 REST

| Method | Path | Purpose |
|---|---|---|
| POST | `/v1/tts` | Synthesize; `Accept: audio/mpeg` or `text/event-stream` for boundaries |
| GET | `/v1/voices` | Unified catalog, filterable by locale and gender |
| POST | `/v1/voices/custom` | Enroll a custom voice (multipart) |
| GET | `/v1/voices/custom/{id}` | Enrollment status |
| DELETE | `/v1/voices/custom/{id}` | Delete profile and artifacts |

### 6.2 OpenAI-compatible

`POST /v1/audio/speech` accepting `{model, input, voice, response_format, speed}`.

OpenAI voice names (`alloy`, `echo`, `fable`, `onyx`, `nova`, `shimmer`) map to Edge
voices through configurable aliases. Custom profiles are addressable directly through
the `voice` field.

`response_format` support depends on which `outputFormat` values the Edge endpoint
accepts. This is unresolved and is scheduled as a spike before the implementation
task, because the answer determines whether transcoding is required.

### 6.3 MCP

`quarkus-mcp-server`, stdio and HTTP/SSE transports. Tools: `synthesize_speech`,
`list_voices`, `create_voice_profile`.

## 7. Error Handling

| Condition | Behaviour |
|---|---|
| HTTP 403 from Edge | Adjust clock skew from `Date` header, retry once |
| Abnormal WebSocket close | `@Retry` with backoff, then `@CircuitBreaker` |
| Circuit open on primary engine | Failover to configured secondary engine |
| `turn.end` with zero audio bytes | 502 |
| Unsupported voice | 400 with the resolved catalog hint |
| Enrollment validation failure | Profile transitions to `FAILED` with `failureReason` |

Error representation is adapter-specific: RFC 7807 for REST, the OpenAI error envelope
for `/v1/audio/speech`, and `isError` tool content for MCP.

## 8. Testing Strategy

| Layer | Approach |
|---|---|
| DRM | Golden vectors pinned against the Python reference |
| Chunker | Property-based: random UTF-8 with entities, assert the three invariants |
| Protocol | Fake Edge WebSocket server (Vert.x) replaying recorded frames; CI runs offline |
| Engines | One shared abstract contract suite executed against every `TtsEngine` adapter |
| REST / OpenAI | `@QuarkusTest` with RestAssured; OpenAI compatibility verified against the official SDK |
| MCP | MCP Inspector validation |
| E2E | Real network smoke tests, tagged, disabled by default |

The shared contract suite is what makes multiple engines safe: it is the executable
definition of engine equivalence.

## 9. Non-Functional

- GraalVM native image is a target. The Vert.x WebSocket client is used rather than
  `java.net.http` because it is already the Quarkus reactive core and is proven under
  native compilation. **Native compatibility has not yet been verified empirically**
  and carries a dedicated task.
- Content-addressed audio cache.
- Concurrency limiting on outbound Edge connections.
- Micrometer metrics, OpenTelemetry tracing, liveness and readiness probes.
- Deployment target is k3s; manifests and a Helm chart are in scope.

## 10. Legal and Operational Notes

The Edge read-aloud endpoint is undocumented and carries no SLA or commercial usage
guarantee. This risk is identical for the Java and Python engines and does not
discriminate between them, but it must be understood before production use.

Voice cloning of a real person's voice requires recorded consent. This is enforced
structurally through a required aggregate field rather than by convention.

## 11. Milestones

| # | Milestone | Delivers |
|---|---|---|
| M0 | Foundation | Scaffold, CI, quality gates |
| M1 | Core Domain and Java Edge Engine | Port, DRM, protocol, chunker, fake server |
| M2 | REST API | Streaming and non-streaming synthesis, catalog |
| M3 | Python Sidecar Engine and Failover | FastAPI sidecar, contract suite, circuit breaker |
| M4 | OpenAI-Compatible API | `/v1/audio/speech`, voice mapping, formats |
| M5 | MCP Server | stdio and HTTP/SSE transports, tools |
| M6 | Custom Voice Cloning | Voice profiles, consent, F5-TTS engine |
| M7 | Production Readiness | Cache, observability, native image, k3s |
