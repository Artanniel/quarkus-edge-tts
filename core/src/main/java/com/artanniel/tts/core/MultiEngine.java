package com.artanniel.tts.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class MultiEngine implements TtsEngine {
    private final List<TtsEngine> engines;

    public MultiEngine(List<TtsEngine> engines) {
        this.engines = new ArrayList<>(engines);
    }

    @Override
    public byte[] synthesize(SynthesisRequest request) throws Exception {
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
        throw new RuntimeException("No engine succeeded in MultiEngine");
    }

    @Override
    public Stream<byte[]> synthesizeStream(SynthesisRequest request) throws Exception {
        byte[] audio = synthesize(request);
        return Stream.of(audio);
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
