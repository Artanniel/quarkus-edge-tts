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
public class FishAudioEngine implements TtsEngine {
    private static final Logger LOG = Logger.getLogger(FishAudioEngine.class);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getProviderType() {
        return "FISH_AUDIO";
    }

    @Override
    public byte[] synthesize(SynthesisRequest request) throws Exception {
        String apiKey = request.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Fish Audio API key is missing");
        }

        String voiceId = request.targetVoiceId();
        if (voiceId == null || voiceId.isBlank()) {
            voiceId = "225d877aa7c3485eaea3d4ca1fac2a6d"; // Default Portuguese model
        }

        String endpoint = request.endpointUrl();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://api.fish.audio/v1/tts";
        }

        String model = "s2.1-pro-free";
        if (request.configExtra() != null && request.configExtra().containsKey("model")) {
            model = String.valueOf(request.configExtra().get("model"));
        }

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("text", request.text());
        bodyMap.put("reference_id", voiceId);
        bodyMap.put("model", model);

        String jsonBody = mapper.writeValueAsString(bodyMap);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            String errorMsg = new String(response.body());
            LOG.warnf("Fish Audio error HTTP %d: %s", response.statusCode(), errorMsg);
            throw new RuntimeException("Fish Audio failed with HTTP " + response.statusCode() + ": " + errorMsg);
        }

        return response.body();
    }
}
