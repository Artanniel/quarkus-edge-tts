package com.artanniel.tts.core;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Failover implementation that tries a primary {@link TtsEngine} and, on any exception,
 * falls back to a secondary engine. It also merges the voice catalogs for listVoices().
 */
public class FailoverTtsEngine implements TtsEngine {
    private final TtsEngine primary;
    private final TtsEngine secondary;

    public FailoverTtsEngine(TtsEngine primary, TtsEngine secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public Stream<byte[]> synthesize(SynthesisRequest request) {
        try {
            return primary.synthesize(request);
        } catch (Exception e) {
            // Log could be added here; now we simply fallback.
            return secondary.synthesize(request);
        }
    }

    @Override
    public List<Voice> listVoices() {
        List<Voice> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Voice v : primary.listVoices()) {
            if (seen.add(v.id())) {
                merged.add(v);
            }
        }
        for (Voice v : secondary.listVoices()) {
            if (seen.add(v.id())) {
                merged.add(v);
            }
        }
        return merged;
    }

    @Override
    public boolean supports(VoiceRef voice) {
        return primary.supports(voice) || secondary.supports(voice);
    }
}
