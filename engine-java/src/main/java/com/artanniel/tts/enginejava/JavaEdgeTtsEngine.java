package com.artanniel.tts.enginejava;

import com.artanniel.tts.core.*;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.WebSocket;
import io.vertx.core.buffer.Buffer;
import java.net.URI;
import java.util.List;
import java.util.concurrent.SubmissionPublisher;
import java.util.stream.Stream;

/**
 * Java implementation of the Edge TTS protocol using Vert.x WebSocket client.
 * It relies on {@link DrmTokenGenerator} for the Sec‑MS‑GEC token and on {@link Chunker}
 * to split the input text respecting protocol limits.
 */
public class JavaEdgeTtsEngine implements TtsEngine {
    private static final String WS_ENDPOINT = "wss://speech.platform.bing.com/edge/v1.0/tts";
    private final Vertx vertx = Vertx.vertx();
    private final HttpClient httpClient = vertx.createHttpClient();
    private final DrmTokenGenerator drmGenerator;
    private final Chunker chunker = new Chunker(3000); // approx 3 KB per frame – safe for Edge limit
    private final VoiceCatalog catalog = new VoiceCatalog();
    private final String trustedToken;

    public JavaEdgeTtsEngine(String trustedToken) {
        this.trustedToken = trustedToken;
        this.drmGenerator = new DrmTokenGenerator(trustedToken);
    }

    @Override
    public Stream<byte[]> synthesize(SynthesisRequest request) {
        // Split the text first – each chunk will be sent as its own SSML frame.
        List<String> parts = chunker.split(request.text());
        SubmissionPublisher<byte[]> publisher = new SubmissionPublisher<>();
        // Open the WebSocket connection (async) and pipe each part.
        httpClient.webSocketAbs(WS_ENDPOINT, wsAsync -> {
            if (wsAsync.succeeded()) {
                WebSocket ws = wsAsync.result();
                // handshake / token exchange (simplified – send token as first text frame)
                ws.textMessage(drmGenerator.generate());
                // Send each part as SSML
                for (String part : parts) {
                    String ssml = "<speak version='1.0' xml:lang='en-US'><voice name='" + request.voice().name() + "'>" + part + "</voice></speak>";
                    ws.textMessage(ssml);
                }
                // Close after sending all parts – Edge will then stream audio frames.
                ws.end();
                // Listen for binary audio frames.
                ws.binaryMessageHandler(buf -> publisher.submit(buf.getBytes()))
                  .closeHandler(v -> publisher.close());
            } else {
                // connection failure – publish nothing and close.
                publisher.close();
            }
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
