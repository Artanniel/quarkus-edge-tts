package com.artanniel.tts.api.openai;

import com.artanniel.tts.core.*;
import com.artanniel.tts.enginejava.JavaEdgeTtsEngine;
import com.artanniel.tts.enginejava.PythonSidecarTtsEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OpenAI‑compatible TTS façade.
 *
 * * POST /v1/audio/speech – synthesises audio according to OpenAI request schema.
 * * GET  /v1/voices        – returns supported voice list (id, name, description).
 */
@Path("/v1")
@ApplicationScoped
public class OpenAiTtsResource {

    private final TtsEngine engine;
    private final VoiceCatalog catalog = new VoiceCatalog();

    public OpenAiTtsResource() {
        // Primary Java engine – reads token from env.
        String token = System.getenv("TRUSTED_CLIENT_TOKEN");
        TtsEngine primary = new JavaEdgeTtsEngine(token != null ? token : "");
        // Secondary sidecar – URL from env.
        String sidecarUrl = System.getenv("SIDE_CAR_URL");
        TtsEngine secondary = new PythonSidecarTtsEngine(sidecarUrl != null ? sidecarUrl : "http://localhost:8000/synthesize");
        this.engine = new FailoverTtsEngine(primary, secondary);
    }

    /** OpenAI request payload (minimal subset). */
    public static record SpeechRequest(
            String model,            // ignored – we only support Edge TTS.
            String input,            // text to synthesize
            String voice,            // voice id (Edge id)
            Double speed,            // optional, mapped to rate
            String response_format   // "json" (base64) or "audio" (raw mp3)
    ) {}

    /** Simple usage stats placeholder for OpenAI compliance. */
    public static record Usage(int prompt_tokens, int completion_tokens, int total_tokens) {}

    @POST
    @Path("/audio/speech")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({"application/json", "audio/mpeg"})
    public Response synthesize(SpeechRequest req) {
        // Map fields – defaults if missing.
        double rate = req.speed() != null ? req.speed() : 1.0;
        String voiceId = req.voice() != null ? req.voice() : "en-US-AriaNeural";
        var synthesisRequest = new SynthesisRequest(
                req.input(),
                new VoiceRef(voiceId),
                rate,
                1.0,
                1.0);
        // Perform synthesis via failover engine.
        byte[] audio = engine.synthesize(synthesisRequest).reduce(new byte[0], (a, b) -> {
            byte[] merged = new byte[a.length + b.length];
            System.arraycopy(a, 0, merged, 0, a.length);
            System.arraycopy(b, 0, merged, a.length, b.length);
            return merged;
        });

        // Decide output format.
        if ("json".equalsIgnoreCase(req.response_format())) {
            String b64 = Base64.getEncoder().encodeToString(audio);
            var json = java.util.Map.of(
                    "id", java.util.UUID.randomUUID().toString(),
                    "object", "audio",
                    "created", java.time.Instant.now().getEpochSecond(),
                    "model", req.model(),
                    "data", b64,
                    "usage", new Usage(0, 0, 0)
            );
            return Response.ok(json, MediaType.APPLICATION_JSON).build();
        }
        // Default: raw MP3.
        return Response.ok(audio, "audio/mpeg").build();
    }

    @GET
    @Path("/voices")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listVoices() {
        List<java.util.Map<String, Object>> list = catalog.listVoices().stream()
                .map(v -> java.util.Map.of(
                        "id", v.id(),
                        "object", "voice",
                        "name", v.displayName(),
                        "language", v.locale(),
                        "custom", v.custom()))
                .collect(Collectors.toList());
        return Response.ok(list).build();
    }
}
