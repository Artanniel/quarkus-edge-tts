package com.artanniel.tts.enginecloning;

import com.artanniel.tts.core.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Simple wrapper around a F5‑TTS cloning service – expected to receive a
 * base64‑encoded audio sample of the target speaker and a text to synthesize.
 * The service responds with a base64‑encoded audio payload that we unpack.
 */
public class VoiceCloningEngine implements TtsEngine {
    private final String cloningServiceUrl;

    public VoiceCloningEngine(String cloningServiceUrl) {
        this.cloningServiceUrl = cloningServiceUrl;
    }

    @Override
    public Stream<byte[]> synthesize(SynthesisRequest request) {
        // For now we stub the request – a real implementation would POST the
        // base64 audio sample + text to `cloningServiceUrl` and stream the
        // response bytes back. Here we just return an empty stream to keep the
        // compilation happy.
        return Stream.empty();
    }

    @Override
    public List<Voice> listVoices() {
        // Cloning engine does not expose a static catalog – defer to core.
        return List.of();
    }

    @Override
    public boolean supports(VoiceRef voice) {
        // Assume it can synthesize any voice ID prefixed with "clone-".
        return voice.id().startsWith("clone-");
    }
}
