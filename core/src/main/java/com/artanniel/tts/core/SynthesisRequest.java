package com.artanniel.tts.core;

public record SynthesisRequest(String text, VoiceRef voice, double rate, double pitch, double volume) {}
