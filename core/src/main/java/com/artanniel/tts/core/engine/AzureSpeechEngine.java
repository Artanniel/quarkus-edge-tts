package com.artanniel.tts.core.engine;

import com.artanniel.tts.core.SynthesisRequest;
import com.artanniel.tts.core.TtsEngine;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@ApplicationScoped
public class AzureSpeechEngine implements TtsEngine {
    private static final Logger LOG = Logger.getLogger(AzureSpeechEngine.class);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String getProviderType() {
        return "AZURE";
    }

    @Override
    public byte[] synthesize(SynthesisRequest request) throws Exception {
        String apiKey = request.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Azure Speech API key is missing");
        }

        String voice = request.targetVoiceId();
        if (voice == null || voice.isBlank()) {
            voice = "pt-BR-ThalitaNeural";
        }

        String endpoint = request.endpointUrl();
        if (endpoint == null || endpoint.isBlank()) {
            String region = "eastus";
            if (request.configExtra() != null && request.configExtra().containsKey("region")) {
                region = String.valueOf(request.configExtra().get("region"));
            }
            endpoint = "https://" + region + ".tts.speech.microsoft.com/cognitiveservices/v1";
        }

        // Determine language code from voice prefix (e.g. pt-BR from pt-BR-ThalitaNeural)
        String lang = "pt-BR";
        if (voice.contains("-")) {
            String[] parts = voice.split("-");
            if (parts.length >= 2) {
                lang = parts[0] + "-" + parts[1];
            }
        }

        String escapedText = request.text()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");

        String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='" + lang + "'>" +
                "<voice name='" + voice + "'>" + escapedText + "</voice></speak>";

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .header("Content-Type", "application/ssml+xml")
                .header("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
                .header("User-Agent", "QuarkusEdgeTtsHub")
                .POST(HttpRequest.BodyPublishers.ofString(ssml))
                .build();

        HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            String errorMsg = new String(response.body());
            LOG.warnf("Azure Speech error HTTP %d: %s", response.statusCode(), errorMsg);
            throw new RuntimeException("Azure Speech failed with HTTP " + response.statusCode() + ": " + errorMsg);
        }

        return response.body();
    }
}
