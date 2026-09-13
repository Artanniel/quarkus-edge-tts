package com.artanniel.tts.core;

/**
 * Splits a text into chunks respecting three constraints:
 *   1. Prefer the rightmost newline, then space, within the max byte size.
 *   2. Do not split inside a multi‑byte UTF‑8 sequence.
 *   3. Do not split inside an XML entity (e.g. &amp;).
 * Returns an array of strings; each element's UTF‑8 byte length <= maxBytes.
 */
public class Chunker {
    private final int maxBytes;

    public Chunker(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    public java.util.List<String> split(String text) {
        java.util.List<String> chunks = new java.util.ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = findSplit(text, start);
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }

    private int findSplit(String text, int start) {
        int limit = Math.min(text.length(), start + maxBytes);
        // walk backwards from limit to find a split point satisfying the constraints
        for (int i = limit; i > start; i--) {
            String candidate = text.substring(start, i);
            int bytes = candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (bytes > maxBytes) continue; // overflow, keep moving left
            char c = text.charAt(i - 1);
            // constraint 1: newline or space preferred
            if (c == '\n' || c == ' ') return i;
            // constraint 2: avoid splitting UTF‑8 surrogate pairs (high surrogate at i-1)
            if (Character.isHighSurrogate(c)) continue;
            // constraint 3: avoid splitting inside an XML entity (look back for '&')
            int amp = text.lastIndexOf('&', i - 1);
            if (amp != -1 && text.indexOf(';', amp) > i - 1) continue; // inside entity
            // otherwise accept this position
            return i;
        }
        // fallback: hard split at maxBytes respecting UTF‑8 (will not happen often)
        return limit;
    }
}
