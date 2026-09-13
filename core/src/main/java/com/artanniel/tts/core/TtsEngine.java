package com.artanniel.tts.core;

public interface TtsEngine {
    // Synthesizes text and returns a stream of audio chunks (byte[])
    java.util.stream.Stream<byte[]> synthesize(SynthesisRequest request);
    java.util.List<Voice> listVoices();
    boolean supports(VoiceRef voice);
}
