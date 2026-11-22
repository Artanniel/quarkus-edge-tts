package com.artanniel.tts.api.rest;

import com.artanniel.tts.core.*;
import com.artanniel.tts.core.service.DynamicFallbackTtsGateway;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
public class TtsResource {

    @Inject
    DynamicFallbackTtsGateway gateway;

    private final VoiceCatalog catalog = new VoiceCatalog();

    @GET
    @Path("/voices")
    public Response listVoices() {
        List<Voice> voices = catalog.listVoices();
        return Response.ok(voices).build();
    }

    public static record TtsRequest(String text, String voice, Double rate, Double pitch, Double volume) {}

    @POST
    @Path("/tts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("audio/mpeg")
    public Response synthesize(TtsRequest request) {
        String voice = request.voice() != null ? request.voice() : "alloy";
        String text = request.text() != null ? request.text() : "";
        Double rate = request.rate() != null ? request.rate() : 1.0;

        try {
            byte[] audio = gateway.synthesize(voice, text, rate);
            return Response.ok(audio, "audio/mpeg").build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(e.getMessage())
                    .build();
        }
    }
}
