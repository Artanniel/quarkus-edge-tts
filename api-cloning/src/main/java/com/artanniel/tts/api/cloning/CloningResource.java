package com.artanniel.tts.api.cloning;

import com.artanniel.tts.core.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

/**
 * API façade exposing voice‑cloning capabilities (F5‑TTS) via a REST endpoint.
 * The actual cloning logic lives in {@link com.artanniel.tts.enginecloning.VoiceCloningEngine}.
 */
@Path("/v1")
@ApplicationScoped
public class CloningResource {

    private final TtsEngine engine;

    public CloningResource() {
        // URL of the external cloning service (e.g. http://localhost:8500/clone)
        String cloningUrl = System.getenv("CLONING_SERVICE_URL");
        this.engine = new com.artanniel.tts.enginecloning.VoiceCloningEngine(
                cloningUrl != null ? cloningUrl : "http://localhost:8500/clone");
    }

    /** Payload for a cloning request – text + reference audio (base64). */
    public static record CloneRequest(String text, String referenceAudioBase64) {}

    @POST
    @Path("/voice-clone")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({"application/json", "audio/mpeg"})
    public Response cloneVoice(CloneRequest req) {
        // Build a synthetic SynthesisRequest – the cloning engine will ignore the
        // voice reference and use the provided audio sample.
        var synthesisRequest = new SynthesisRequest(
                req.text(),
                new VoiceRef("clone-temp"), // placeholder
                1.0,
                1.0,
                1.0);
        // The cloning engine is expected to stream audio bytes.
        byte[] audio = engine.synthesize(synthesisRequest).reduce(new byte[0], (a, b) -> {
            byte[] merged = new byte[a.length + b.length];
            System.arraycopy(a, 0, merged, 0, a.length);
            System.arraycopy(b, 0, merged, a.length, b.length);
            return merged;
        });
        return Response.ok(audio, "audio/mpeg");
    }
}
