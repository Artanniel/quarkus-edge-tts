package com.artanniel.tts.core;

import java.util.Map;

public record SynthesisRequest(
    String text,
    VoiceRef voice,
    double rate,
    double pitch,
    double volume,
    String targetVoiceId,
    String endpointUrl,
    String apiKey,
    Map<String, Object> configExtra
) {
    public SynthesisRequest(String text, VoiceRef voice, double rate, double pitch, double volume) {
        this(text, voice, rate, pitch, volume, voice != null ? voice.name() : "alloy", null, null, Map.of());
    }

    public SynthesisRequest(String text, String voiceName, double rate) {
        this(text, new VoiceRef(voiceName), rate, 1.0, 1.0, voiceName, null, null, Map.of());
    }
}
