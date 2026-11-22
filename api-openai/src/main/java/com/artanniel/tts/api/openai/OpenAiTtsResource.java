package com.artanniel.tts.api.openai;

import com.artanniel.tts.core.model.ProvedorConfig;
import com.artanniel.tts.core.service.DynamicFallbackTtsGateway;
import com.artanniel.tts.core.service.TtsDatabaseService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;

@Path("/v1")
@ApplicationScoped
public class OpenAiTtsResource {
    private static final Logger LOG = Logger.getLogger(OpenAiTtsResource.class);

    @Inject
    DynamicFallbackTtsGateway gateway;

    @Inject
    TtsDatabaseService dbService;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpeechRequest(
            String model,
            String input,
            String voice,
            Double speed,
            String response_format
    ) {}

    @POST
    @Path("/audio/speech")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({"audio/mpeg", "audio/mp3", MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON, MediaType.WILDCARD})
    public Response synthesize(SpeechRequest req, @HeaderParam("Accept") String acceptHeader) {
        if (req == null || req.input() == null || req.input().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", Map.of("message", "Input text is required", "type", "invalid_request_error")))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        String voice = req.voice() != null ? req.voice().trim() : "alloy";
        double speed = req.speed() != null ? req.speed() : 1.0;

        try {
            byte[] audio = gateway.synthesize(voice, req.input(), speed);

            if ("json".equalsIgnoreCase(req.response_format()) || "b64_json".equalsIgnoreCase(req.response_format())) {
                String b64 = Base64.getEncoder().encodeToString(audio);
                Map<String, Object> resp = Map.of(
                        "id", UUID.randomUUID().toString(),
                        "object", "audio",
                        "created", Instant.now().getEpochSecond(),
                        "model", req.model() != null ? req.model() : "tts-1",
                        "data", b64
                );
                return Response.ok(resp, MediaType.APPLICATION_JSON).build();
            }

            String mediaType = (acceptHeader != null && acceptHeader.contains("application/octet-stream"))
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : "audio/mpeg";

            return Response.ok(audio, mediaType)
                    .header("Content-Disposition", "inline; filename=\"speech.mp3\"")
                    .build();
        } catch (Exception e) {
            LOG.errorf(e, "TTS synthesis failed for voice '%s': %s", voice, e.getMessage());
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", Map.of("message", e.getMessage(), "type", "tts_fallback_failed")))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    @GET
    @Path("/audio/pcm")
    @Produces("audio/pcm")
    public Response streamPcm(@QueryParam("text") String text,
                             @QueryParam("voice") String voice,
                             @QueryParam("sampleRate") @DefaultValue("24000") int sampleRate) {
        String effectiveVoice = voice != null ? voice : "alloy";
        String effectiveText = text != null ? text : "";
        try {
            byte[] audio = gateway.synthesize(effectiveVoice, effectiveText, 1.0);
            StreamingOutput out = os -> os.write(audio);
            return Response.ok(out, "audio/pcm").header("X-Sample-Rate", sampleRate).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(e.getMessage())
                    .build();
        }
    }

    @POST
    @Path("/admin/reload")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reloadCache() {
        dbService.invalidateCache();
        List<ProvedorConfig> providers = dbService.getActiveProviders();
        return Response.ok(Map.of(
                "status", "ok",
                "message", "Database cache flushed and refreshed successfully",
                "active_providers_count", providers.size(),
                "active_providers", providers.stream().map(ProvedorConfig::nome).toList()
        )).build();
    }

    @GET
    @Path("/models")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listModels() {
        List<Map<String, Object>> data = List.of(
                Map.of("id", "tts-1", "object", "model", "owned_by", "system"),
                Map.of("id", "tts-1-hd", "object", "model", "owned_by", "system")
        );
        return Response.ok(Map.of("object", "list", "data", data)).build();
    }

    @GET
    @Path("/voices")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listVoices() {
        List<String> openaiVoices = List.of("alloy", "echo", "fable", "onyx", "nova", "shimmer");
        List<ProvedorConfig> active = dbService.getActiveProviders();

        List<Map<String, Object>> result = new ArrayList<>();
        for (String v : openaiVoices) {
            Map<String, String> mappings = dbService.getVoiceMappings(v);
            result.add(Map.of(
                    "id", v,
                    "name", v,
                    "mappings", mappings
            ));
        }

        return Response.ok(Map.of(
                "voices", result,
                "active_providers", active.stream().map(p -> Map.of(
                        "nome", p.nome(),
                        "tipo", p.tipo(),
                        "prioridade", p.prioridade()
                )).toList()
        )).build();
    }

    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        List<ProvedorConfig> active = dbService.getActiveProviders();
        return Response.ok(Map.of(
                "status", "UP",
                "active_providers", active.size()
        )).build();
    }
}
