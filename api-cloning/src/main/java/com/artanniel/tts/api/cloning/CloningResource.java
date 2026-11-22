package com.artanniel.tts.api.cloning;

import com.artanniel.tts.core.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v1")
@ApplicationScoped
public class CloningResource {

    private final TtsEngine engine;

    public CloningResource() {
        String cloningUrl = System.getenv("CLONING_SERVICE_URL");
        this.engine = new com.artanniel.tts.enginecloning.VoiceCloningEngine(
                cloningUrl != null ? cloningUrl : "http://localhost:8500/clone");
    }

    public static record CloneRequest(String text, String referenceAudioBase64) {}

    @POST
    @Path("/voice-clone")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({"application/json", "audio/mpeg"})
    public Response cloneVoice(CloneRequest req) {
        var synthesisRequest = new SynthesisRequest(
                req.text(),
                new VoiceRef("clone-temp"),
                1.0,
                1.0,
                1.0);
        try {
            byte[] audio = engine.synthesize(synthesisRequest);
            return Response.ok(audio, "audio/mpeg").build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(e.getMessage())
                    .build();
        }
    }
}
