package com.artanniel.tts.enginejava;

import com.artanniel.tts.core.*;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class JavaEdgeTtsEngine implements TtsEngine {
    private final String trustedToken;
    private final VoiceCatalog catalog = new VoiceCatalog();

    public JavaEdgeTtsEngine(String trustedToken) {
        this.trustedToken = trustedToken;
    }

    @Override
    public byte[] synthesize(SynthesisRequest request) throws Exception {
        String voice = request.voice() != null ? request.voice().name() : "pt-BR-FranciscaNeural";
        File tempMp3 = File.createTempFile("edge-tts-", ".mp3");
        try {
            List<String> command = new ArrayList<>();
            command.add("edge-tts");
            command.add("--voice");
            command.add(voice);
            command.add("--text");
            command.add(request.text());
            command.add("--write-media");
            command.add(tempMp3.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Edge-TTS timed out");
            }
            if (process.exitValue() != 0) {
                String error = new String(process.getInputStream().readAllBytes());
                throw new RuntimeException("Edge-TTS error: " + error);
            }
            return Files.readAllBytes(tempMp3.toPath());
        } finally {
            if (tempMp3.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempMp3.delete();
            }
        }
    }

    @Override
    public List<Voice> listVoices() {
        return catalog.listVoices();
    }

    @Override
    public boolean supports(VoiceRef voice) {
        return catalog.isSupported(voice);
    }
}
