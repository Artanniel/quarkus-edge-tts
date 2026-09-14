package com.artanniel.tts.api.mcp;

import com.artanniel.tts.core.*;
import com.artanniel.tts.enginejava.JavaEdgeTtsEngine;
import com.artanniel.tts.enginejava.PythonSidecarTtsEngine;
import com.artanniel.tts.core.FailoverTtsEngine;
import io.quarkus.vertx.web.SseEventSink;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP (Micro‑Connector Protocol) façade – simply proxies the same OpenAI‑compatible
 * endpoints but is intended to be exposed via `hermes mcp add`.
 */
@Path("/mcp/v1")
@ApplicationScoped
public class McpTtsResource {
    private final TtsEngine engine;
    private final VoiceCatalog catalog = new VoiceCatalog();

    public McpTtsResource() {
        String token = System.getenv("TRUSTED_CLIENT_TOKEN");
        TtsEngine primary = new JavaEdgeTtsEngine(token != null ? token : "");
        String sidecar = System.getenv("SIDE_CAR_URL");
        TtsEngine secondary = new PythonSidecarTtsEngine(sidecar != null ? sidecar : "http://localhost:8000/synthesize");
        this.engine = new FailoverTtsEngine(primary, secondary);
    }

    public static record SpeechRequest(String model, String input, String voice, Double speed, String response_format) {}
    public static record Usage(int prompt_tokens, int completion_tokens, int total_tokens) {}

    @POST
    @Path("/audio/speech")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({"application/json", "audio/mpeg"})
    public Response synthesize(SpeechRequest req) {
        double rate = req.speed() != null ? req.speed() : 1.0;
        String voiceId = req.voice() != null ? req.voice() : "en-US-AriaNeural";
        var synthesisRequest = new SynthesisRequest(req.input(), new VoiceRef(voiceId), rate, 1.0, 1.0);
        byte[] audio = engine.synthesize(synthesisRequest).reduce(new byte[0], (a, b) -> {
            byte[] merged = new byte[a.length + b.length];
            System.arraycopy(a, 0, merged, 0, a.length);
            System.arraycopy(b, 0, merged, a.length, b.length);
            return merged;
        });
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
