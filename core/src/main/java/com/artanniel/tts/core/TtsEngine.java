package com.artanniel.tts.core;

import java.util.List;
import java.util.stream.Stream;

public interface TtsEngine {
    byte[] synthesize(SynthesisRequest request) throws Exception;

    default Stream<byte[]> synthesizeStream(SynthesisRequest request) throws Exception {
        byte[] audio = synthesize(request);
        return Stream.of(audio);
    }

    default List<Voice> listVoices() {
        return List.of();
    }

    default boolean supports(VoiceRef voice) {
        return true;
    }

    default String getProviderType() {
        return "GENERIC";
    }
}
