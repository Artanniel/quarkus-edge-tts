package com.artanniel.tts.core;

public class VoiceCatalog {
    // Placeholder – a real implementation will fetch the Edge voice catalog (HTTP GET)
    // and cache it with a TTL. For now we expose a minimal static list used by unit tests.
    public java.util.List<Voice> listVoices() {
        return java.util.List.of(
            new Voice("en-US-AriaNeural", "Aria (US)", "en-US", false),
            new Voice("pt-BR-FranciscaNeural", "Francisca (BR)", "pt-BR", false)
        );
    }

    public boolean isSupported(VoiceRef ref) {
        return listVoices().stream().anyMatch(v -> v.id().equals(ref.name()));
    }
}
