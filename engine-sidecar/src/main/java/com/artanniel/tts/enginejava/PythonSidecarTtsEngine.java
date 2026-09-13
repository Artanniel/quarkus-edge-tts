package com.artanniel.tts.enginejava;

import com.artanniel.tts.core.*;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.HttpResponse;
import java.util.List;
import java.util.stream.Stream;
import java.util.concurrent.SubmissionPublisher;

/**
 * Wrapper to call a Python FastAPI sidecar that performs the Edge TTS protocol.
 * The sidecar expects JSON payload:
 *   {"text":"...","voice":"...","rate":1.0,"pitch":1.0,"volume":1.0}
 * and returns raw audio bytes (application/octet-stream).
 */
public class PythonSidecarTtsEngine implements TtsEngine {
    private final WebClient client;
    private final String sidecarUrl; // e.g. http://localhost:8000/synthesize
    private final VoiceCatalog catalog = new VoiceCatalog();

    public PythonSidecarTtsEngine(String sidecarUrl) {
        this.sidecarUrl = sidecarUrl;
        this.client = WebClient.create(Vertx.vertx());
    }

    @Override
    public Stream<byte[]> synthesize(SynthesisRequest request) {
        SubmissionPublisher<byte[]> publisher = new SubmissionPublisher<>();
        // Build JSON body
        String json = "{\"text\":\"" + request.text().replace("\"", "\\\"") + "\","
                + "\"voice\":\"" + request.voice().name() + "\","
                + "\"rate\":" + request.rate() + ","
                + "\"pitch\":" + request.pitch() + ","
                + "\"volume\":" + request.volume() + "}";
        client.postAbs(sidecarUrl)
                .putHeader("Content-Type", "application/json")
                .sendBuffer(Buffer.buffer(json), ar -> {
                    if (ar.succeeded()) {
                        HttpResponse<Buffer> resp = ar.result();
                        if (resp.statusCode() == 200) {
                            publisher.submit(resp.body().getBytes());
                        } else {
                            // treat non‑200 as failure – publish nothing
                        }
                    }
                    publisher.close();
                });
        return publisher.consume().stream();
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
