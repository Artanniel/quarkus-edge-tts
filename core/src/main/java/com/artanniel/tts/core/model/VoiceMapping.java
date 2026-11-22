package com.artanniel.tts.core.model;

import java.util.UUID;

public record VoiceMapping(
    UUID id,
    String voiceOpenai,
    String provedorTipo,
    String targetVoiceId
) {}
