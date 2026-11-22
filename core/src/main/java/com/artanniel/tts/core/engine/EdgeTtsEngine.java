package com.artanniel.tts.core.engine;

import com.artanniel.tts.core.SynthesisRequest;
import com.artanniel.tts.core.TtsEngine;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class EdgeTtsEngine implements TtsEngine {
    private static final Logger LOG = Logger.getLogger(EdgeTtsEngine.class);

    @Override
    public String getProviderType() {
        return "EDGE_TTS";
    }

    @Override
    public byte[] synthesize(SynthesisRequest request) throws Exception {
        String voice = request.targetVoiceId();
        if (voice == null || voice.isBlank()) {
            voice = "pt-BR-FranciscaNeural";
        }

        String binPath = System.getenv("EDGE_TTS_BIN");
        if (binPath == null || binPath.isBlank()) {
            binPath = "edge-tts";
        }

        File tempMp3 = File.createTempFile("edge-tts-", ".mp3");
        try {
            List<String> command = new ArrayList<>();
            command.add(binPath);
            command.add("--voice");
            command.add(voice);
            command.add("--text");
            command.add(request.text());
            command.add("--write-media");
            command.add(tempMp3.getAbsolutePath());

            if (request.rate() > 0 && request.rate() != 1.0) {
                int percent = (int) Math.round((request.rate() - 1.0) * 100);
                String rateParam = (percent >= 0 ? "+" : "") + percent + "%";
                command.add("--rate=" + rateParam);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Edge-TTS process timed out after 20 seconds");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorOutput = new String(process.getInputStream().readAllBytes());
                LOG.warnf("Edge-TTS process failed with exit code %d: %s", exitCode, errorOutput);
                throw new RuntimeException("Edge-TTS exited with " + exitCode + ": " + errorOutput);
            }

            if (!tempMp3.exists() || tempMp3.length() == 0) {
                throw new RuntimeException("Edge-TTS generated empty audio file");
            }

            return Files.readAllBytes(tempMp3.toPath());
        } finally {
            if (tempMp3.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempMp3.delete();
            }
        }
    }
}
