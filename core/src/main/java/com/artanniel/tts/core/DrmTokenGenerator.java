package com.artanniel.tts.core;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Utility to generate the Sec‑MS‑GEC DRM token required by the Edge TTS WebSocket protocol.
 * The algorithm follows the specification in the design document:
 *   1. Take the current Unix epoch seconds.
 *   2. Apply an optional clock‑skew correction (handled externally).
 *   3. Floor the value to a 300‑second boundary.
 *   4. Add the Windows epoch offset (11644473600 seconds).
 *   5. Convert to 100‑nanosecond ticks (multiply by 10_000_000).
 *   6. Concatenate the trusted client token (from config) to the decimal tick string.
 *   7. Compute SHA‑256 over the UTF‑8 bytes of that concatenated string.
 *   8. Return the hash as an uppercase hexadecimal string.
 */
@RegisterForReflection
public class DrmTokenGenerator {

    private final String trustedClientToken;
    private final long clockSkewSeconds;

    public DrmTokenGenerator(String trustedClientToken) {
        this(trustedClientToken, 0);
    }

    public DrmTokenGenerator(String trustedClientToken, long clockSkewSeconds) {
        this.trustedClientToken = trustedClientToken;
        this.clockSkewSeconds = clockSkewSeconds;
    }

    /**
     * Generates the token for the current moment (taking clock skew into account).
     */
    public String generate() {
        long unixSec = Instant.now().getEpochSecond() + clockSkewSeconds;
        // floor to 300‑second boundary
        long floored = unixSec - (unixSec % 300);
        // Windows epoch offset (seconds)
        long windowsSec = floored + 11644473600L;
        // convert to 100‑ns ticks
        long ticks = windowsSec * 10_000_000L;
        String concatenated = ticks + trustedClientToken;
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(concatenated.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
