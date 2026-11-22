package com.artanniel.tts.core.service;

import com.artanniel.tts.core.SynthesisRequest;
import com.artanniel.tts.core.TtsEngine;
import com.artanniel.tts.core.VoiceRef;
import com.artanniel.tts.core.model.ProvedorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.*;

@ApplicationScoped
public class DynamicFallbackTtsGateway {
    private static final Logger LOG = Logger.getLogger(DynamicFallbackTtsGateway.class);

    @Inject
    TtsDatabaseService dbService;

    @Inject
    Instance<TtsEngine> engines;

    private final Map<String, TtsEngine> engineMap = new HashMap<>();

    public byte[] synthesize(String voiceOpenai, String text, Double speed) throws Exception {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Synthesis text cannot be empty");
        }

        List<ProvedorConfig> providers = dbService.getActiveProviders();
        if (providers.isEmpty()) {
            throw new IllegalStateException("No active TTS providers available in tts.provedores database table");
        }

        Map<String, String> mappings = dbService.getVoiceMappings(voiceOpenai);
        List<String> errors = new ArrayList<>();

        for (ProvedorConfig provider : providers) {
            String providerType = provider.tipo().toUpperCase();
            TtsEngine engine = findEngine(providerType);

            if (engine == null) {
                LOG.warnf("No engine implementation registered for provider type: %s", providerType);
                continue;
            }

            String targetVoiceId = mappings.get(providerType);
            if (targetVoiceId == null || targetVoiceId.isBlank()) {
                targetVoiceId = voiceOpenai;
            }

            double rate = (speed != null && speed > 0) ? speed : 1.0;
            var request = new SynthesisRequest(
                    text,
                    new VoiceRef(voiceOpenai),
                    rate,
                    1.0,
                    1.0,
                    targetVoiceId,
                    provider.endpointUrl(),
                    provider.apiKey(),
                    provider.configExtra()
            );

            try {
                LOG.infof("Synthesizing audio: provider='%s' (%s, prio=%d), voice='%s' -> targetVoice='%s'",
                        provider.nome(), providerType, provider.prioridade(), voiceOpenai, targetVoiceId);

                byte[] audio = engine.synthesize(request);
                if (audio != null && audio.length > 0) {
                    LOG.infof("Synthesis successful via %s (%d bytes generated)", provider.nome(), audio.length);
                    return audio;
                } else {
                    LOG.warnf("Provider %s returned empty audio buffer", provider.nome());
                    errors.add(provider.nome() + ": empty audio buffer");
                }
            } catch (Exception e) {
                LOG.warnf("Synthesis failed on %s: %s. Initiating fallback to next tier...",
                        provider.nome(), e.getMessage());
                errors.add(provider.nome() + ": " + e.getMessage());
            }
        }

        String fullError = "All active TTS providers failed in fallback chain: " + String.join(" | ", errors);
        LOG.error(fullError);
        throw new RuntimeException(fullError);
    }

    private synchronized TtsEngine findEngine(String type) {
        if (engineMap.isEmpty()) {
            for (TtsEngine eng : engines) {
                engineMap.put(eng.getProviderType().toUpperCase(), eng);
            }
        }
        return engineMap.get(type);
    }
}
