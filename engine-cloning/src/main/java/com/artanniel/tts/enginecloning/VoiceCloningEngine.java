package com.artanniel.tts.enginecloning;

import com.artanniel.tts.core.*;
import java.util.List;

public class VoiceCloningEngine implements TtsEngine {
    private final String cloningServiceUrl;

    public VoiceCloningEngine(String cloningServiceUrl) {
        this.cloningServiceUrl = cloningServiceUrl;
    }

    @Override
    public byte[] synthesize(SynthesisRequest request) throws Exception {
        return new byte[0];
    }

    @Override
    public List<Voice> listVoices() {
        return List.of();
    }

    @Override
    public boolean supports(VoiceRef voice) {
        return voice != null && voice.name() != null && voice.name().startsWith("clone-");
    }
}
