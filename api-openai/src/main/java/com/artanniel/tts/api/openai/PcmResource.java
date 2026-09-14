package com.artanniel.tts.api.openai;

import com.artanniel.tts.core.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Real‑time PCM streaming endpoint (experimental).
 *
 * GET /v1/audio/pcm?text=Hello&voice=en-US-AriaNeural&sampleRate=24000
 *
 * The engine synthesises the requested text and streams raw PCM bytes.
 * For now the implementation forwards the MP3 bytes as‑is – a proper
 * conversion layer can be added later.
 */
@Path("/v1/audio")
@ApplicationScoped
public class PcmResource {

    private final TtsEngine engine;

    public PcmResource() {
        // Same failover engine used by the OpenAI façade.
        String token = System.getenv("TRUSTED_CLIENT_TOKEN");
        TtsEngine primary = new com.artanniel.tts.enginejava.JavaEdgeTtsEngine(token != null ? token : "");
        String sidecarUrl = System.getenv("SIDE_CAR_URL");
        TtsEngine secondary = new com.artanniel.tts.enginejava.PythonSidecarTtsEngine(
                sidecarUrl != null ? sidecarUrl : "http://localhost:8000/synthesize");
        this.engine = new com.artanniel.tts.core.FailoverTtsEngine(primary, secondary);
    }

    @GET
    @Path("/pcm")
    @Produces("audio/pcm")
    public Response streamPcm(@QueryParam("text") String text,
                             @QueryParam("voice") String voice,
                             @QueryParam("sampleRate") @DefaultValue("24000") int sampleRate) {
        // Build a minimal synthesis request – only the mandatory fields.
        var request = new SynthesisRequest(
                text != null ? text : "",
                new VoiceRef(voice != null ? voice : "en-US-AriaNeural"),
                1.0, 1.0, 1.0);
        StreamingOutput out = new StreamingOutput() {
            @Override
            public void write(OutputStream os) {
                engine.synthesize(request).forEach(chunk -> {
                    try { os.write(chunk); } catch (Exception e) { /* ignore */ }
                });
                // No conversion – raw bytes are sent.
            }
        };
        return Response.ok(out, "audio/pcm").header("X-Sample-Rate", sampleRate).build();
    }
}
