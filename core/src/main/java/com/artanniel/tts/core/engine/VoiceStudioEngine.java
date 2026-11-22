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
public class VoiceStudioEngine implements TtsEngine {
    private static final Logger LOG = Logger.getLogger(VoiceStudioEngine.class);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getProviderType() {
        return "VOICESTUDIO";
    }

    @Override
    public byte[] synthesize(SynthesisRequest request) throws Exception {
        String endpoint = request.endpointUrl();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "http://voicestudio.production.svc.cluster.local:8000/v1/audio/speech";
        }

        String voice = request.targetVoiceId();
        if (voice == null || voice.isBlank()) {
            voice = "artan_voz_neutra";
        }

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("model", "cosyvoice");
        bodyMap.put("input", request.text());
        bodyMap.put("voice", voice);
        if (request.rate() > 0) {
            bodyMap.put("speed", request.rate());
        }

        String jsonBody = mapper.writeValueAsString(bodyMap);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        String apiKey = request.apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            String errorMsg = new String(response.body());
            LOG.warnf("VoiceStudio error HTTP %d: %s", response.statusCode(), errorMsg);
            throw new RuntimeException("VoiceStudio failed with HTTP " + response.statusCode() + ": " + errorMsg);
        }

        return response.body();
    }
}
