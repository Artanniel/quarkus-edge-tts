package com.artanniel.tts.core.engine;

import com.artanniel.tts.core.SynthesisRequest;
import com.artanniel.tts.core.TtsEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class ElevenLabsEngine implements TtsEngine {
    private static final Logger LOG = Logger.getLogger(ElevenLabsEngine.class);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getProviderType() {
        return "ELEVENLABS";
    }

    @Override
    public byte[] synthesize(SynthesisRequest request) throws Exception {
        String apiKey = request.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("ElevenLabs API key is missing");
        }

        String voiceId = request.targetVoiceId();
        if (voiceId == null || voiceId.isBlank()) {
            voiceId = "SAz9YHcvj6GT2YYXdXww"; // River default
        }

        String baseEndpoint = request.endpointUrl();
        if (baseEndpoint == null || baseEndpoint.isBlank()) {
            baseEndpoint = "https://api.elevenlabs.io/v1/text-to-speech";
        }
        if (baseEndpoint.endsWith("/")) {
            baseEndpoint = baseEndpoint.substring(0, baseEndpoint.length() - 1);
        }
        String targetUrl = baseEndpoint + "/" + voiceId;

        String modelId = "eleven_multilingual_v2";
        if (request.configExtra() != null && request.configExtra().containsKey("model_id")) {
            modelId = String.valueOf(request.configExtra().get("model_id"));
        }

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("text", request.text());
        bodyMap.put("model_id", modelId);

        String jsonBody = mapper.writeValueAsString(bodyMap);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(20))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            String errorMsg = new String(response.body());
            LOG.warnf("ElevenLabs error HTTP %d: %s", response.statusCode(), errorMsg);
            throw new RuntimeException("ElevenLabs failed with HTTP " + response.statusCode() + ": " + errorMsg);
        }

        return response.body();
    }
}
