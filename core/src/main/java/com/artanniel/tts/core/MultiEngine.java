package com.artanniel.tts.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Engine that tries a list of {@link TtsEngine}s in order. It first checks if the engine
 * {@link TtsEngine#supports(VoiceRef)} the requested voice; if not, it skips it. If the engine
 * supports the voice but throws an exception during {@link #synthesize(SynthesisRequest)} it
 * falls back to the next engine.
 */
public class MultiEngine implements TtsEngine {
    private final List<TtsEngine> engines;

    public MultiEngine(List<TtsEngine> engines) {
        this.engines = new ArrayList<>(engines);
    }

    @Override
    public Stream<byte[]> synthesize(SynthesisRequest request) {
        VoiceRef voice = request.voice();
        for (TtsEngine engine : engines) {
            if (engine.supports(voice)) {
                try {
                    return engine.synthesize(request);
                } catch (Exception e) {
                    // continue to next engine
                }
            }
        }
        // No engine supports or all failed – return empty stream
        return Stream.empty();
    }

    @Override
    public List<Voice> listVoices() {
        List<Voice> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TtsEngine engine : engines) {
            for (Voice v : engine.listVoices()) {
                if (seen.add(v.id())) {
                    merged.add(v);
                }
            }
        }
        return merged;
    }

    @Override
    public boolean supports(VoiceRef voice) {
        for (TtsEngine engine : engines) {
            if (engine.supports(voice)) {
                return true;
            }
        }
        return false;
    }
}
