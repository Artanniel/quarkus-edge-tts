package com.artanniel.tts.enginejava;

import com.artanniel.tts.core.*;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PythonSidecarTtsEngine implements TtsEngine {
    private final WebClient client;
    private final String sidecarUrl;
    private final VoiceCatalog catalog = new VoiceCatalog();

    public PythonSidecarTtsEngine(String sidecarUrl) {
        this.sidecarUrl = sidecarUrl;
        this.client = WebClient.create(Vertx.vertx());
    }

    @Override
    public byte[] synthesize(SynthesisRequest request) throws Exception {
        String json = "{\"text\":\"" + request.text().replace("\"", "\\\"") + "\","
                + "\"voice\":\"" + request.voice().name() + "\","
                + "\"rate\":" + request.rate() + ","
                + "\"pitch\":" + request.pitch() + ","
                + "\"volume\":" + request.volume() + "}";

        CompletableFuture<byte[]> future = new CompletableFuture<>();
        client.postAbs(sidecarUrl)
                .putHeader("Content-Type", "application/json")
                .sendBuffer(Buffer.buffer(json), ar -> {
                    if (ar.succeeded()) {
                        HttpResponse<Buffer> resp = ar.result();
                        if (resp.statusCode() == 200) {
                            future.complete(resp.body().getBytes());
                        } else {
                            future.completeExceptionally(new RuntimeException("Sidecar returned HTTP " + resp.statusCode()));
                        }
                    } else {
                        future.completeExceptionally(ar.cause());
                    }
                });
        return future.get(15, TimeUnit.SECONDS);
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
