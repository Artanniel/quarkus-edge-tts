package com.artanniel.tts.core.model;

import java.util.Map;
import java.util.UUID;

public record ProvedorConfig(
    UUID id,
    String nome,
    String tipo,
    int prioridade,
    boolean ativo,
    String endpointUrl,
    String apiKey,
    Map<String, Object> configExtra
) {}
