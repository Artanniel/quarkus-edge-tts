package com.artanniel.tts.api.rest;

import com.artanniel.tts.core.*;
import com.artanniel.tts.enginejava.JavaEdgeTtsEngine;
import io.quarkus.vertx.web.SseEventSink;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.metrics.annotation.Counted;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST façade exposing the TTS service.
 *
 * * `POST /v1/tts` – synthesises text. Query param `stream=true` returns an SSE stream of raw audio bytes.
 * * `GET  /v1/voices` – returns the unified voice catalog (Edge + custom placeholder).
 */
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
public class TtsResource {

    // In a real app this would be injected via @ConfigProperty or CDI.
    private final TtsEngine engine = new JavaEdgeTtsEngine(System.getenv("TRUSTED_CLIENT_TOKEN"));
    private final VoiceCatalog catalog = new VoiceCatalog();

    @GET
    @Path("/voices")
    @Counted(name = "tts_voices_requests", description = "How many times the voice catalog was requested")
    public Response listVoices() {
        List<Voice> voices = catalog.listVoices();
        return Response.ok(voices).build();
    }

    public static record TtsRequest(String text, String voice, Double rate, Double pitch, Double volume) {}

    @POST
    @Path("/tts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({"audio/mpeg", "text/event-stream"})
    public Response synthesize(TtsRequest request,
                              @QueryParam("stream") @DefaultValue("false") boolean stream,
                              @Context SseEventSink sse) {
        // Build the domain request – defaults for optional parameters.
        var voiceRef = new VoiceRef(request.voice);
        var synthReq = new SynthesisRequest(
                request.text,
                voiceRef,
                request.rate != null ? request.rate : 1.0,
                request.pitch != null ? request.pitch : 1.0,
                request.volume != null ? request.volume : 1.0);

        if (!engine.supports(voiceRef)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Unsupported voice: " + request.voice)
                    .build();
        }

        if (stream) {
            // Convert the engine's Stream<byte[]> into a Mutiny Multi<byte[]> for SSE.
            Multi<byte[]> multi = Multi.createFrom().items(engine.synthesize(synthReq)::iterator);
            multi.subscribe().with(sse::send);
            return Response.ok().build(); // SSE connection managed by the sink.
        } else {
            // Collect all chunks into a single byte array (simple case).
            byte[] audio = engine.synthesize(synthReq).reduce(new byte[0], (a, b) -> {
                byte[] merged = new byte[a.length + b.length];
                System.arraycopy(a, 0, merged, 0, a.length);
                System.arraycopy(b, 0, merged, a.length, b.length);
                return merged;
            });
            return Response.ok(audio, "audio/mpeg").build();
        }
    }
}
