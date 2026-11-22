package com.artanniel.tts.core.service;

import com.artanniel.tts.core.model.ProvedorConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class TtsDatabaseService {
    private static final Logger LOG = Logger.getLogger(TtsDatabaseService.class);
    private static final long CACHE_TTL_SECONDS = 30;

    @Inject
    AgroalDataSource dataSource;

    private final ObjectMapper mapper = new ObjectMapper();

    private final AtomicReference<List<ProvedorConfig>> cachedProviders = new AtomicReference<>();
    private final AtomicReference<Instant> providersCacheExpiry = new AtomicReference<>(Instant.EPOCH);

    private final Map<String, Map<String, String>> voiceMappingsCache = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> mappingsCacheExpiry = new AtomicReference<>(Instant.EPOCH);

    public void invalidateCache() {
        LOG.info("Manually invalidating TTS database caches...");
        providersCacheExpiry.set(Instant.EPOCH);
        mappingsCacheExpiry.set(Instant.EPOCH);
        cachedProviders.set(null);
        voiceMappingsCache.clear();
    }

    public List<ProvedorConfig> getActiveProviders() {
        Instant now = Instant.now();
        List<ProvedorConfig> cached = cachedProviders.get();
        if (cached != null && now.isBefore(providersCacheExpiry.get())) {
            return cached;
        }

        synchronized (this) {
            cached = cachedProviders.get();
            if (cached != null && now.isBefore(providersCacheExpiry.get())) {
                return cached;
            }

            List<ProvedorConfig> list = new ArrayList<>();
            String sql = "SELECT id, nome, tipo, prioridade, ativo, endpoint_url, api_key, config_extra " +
                         "FROM tts.provedores " +
                         "WHERE ativo = true " +
                         "ORDER BY prioridade ASC;";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    UUID id = rs.getObject("id", UUID.class);
                    String nome = rs.getString("nome");
                    String tipo = rs.getString("tipo");
                    int prioridade = rs.getInt("prioridade");
                    boolean ativo = rs.getBoolean("ativo");
                    String endpointUrl = rs.getString("endpoint_url");
                    String apiKey = rs.getString("api_key");
                    String configExtraJson = rs.getString("config_extra");

                    Map<String, Object> configExtra = Map.of();
                    if (configExtraJson != null && !configExtraJson.isBlank()) {
                        try {
                            configExtra = mapper.readValue(configExtraJson, new TypeReference<Map<String, Object>>() {});
                        } catch (Exception e) {
                            LOG.warnf("Failed to parse config_extra for provider %s: %s", nome, e.getMessage());
                        }
                    }

                    list.add(new ProvedorConfig(id, nome, tipo, prioridade, ativo, endpointUrl, apiKey, configExtra));
                }

                cachedProviders.set(list);
                providersCacheExpiry.set(now.plusSeconds(CACHE_TTL_SECONDS));
                LOG.infof("Loaded %d active TTS providers from database", list.size());
                return list;
            } catch (Exception e) {
                LOG.errorf(e, "Error loading TTS providers from database: %s", e.getMessage());
                return cached != null ? cached : List.of();
            }
        }
    }

    public Map<String, String> getVoiceMappings(String voiceOpenai) {
        if (voiceOpenai == null) {
            return Map.of();
        }
        String key = voiceOpenai.trim().toLowerCase();

        Instant now = Instant.now();
        if (now.isAfter(mappingsCacheExpiry.get())) {
            voiceMappingsCache.clear();
            mappingsCacheExpiry.set(now.plusSeconds(CACHE_TTL_SECONDS));
        }

        Map<String, String> mapping = voiceMappingsCache.get(key);
        if (mapping != null) {
            return mapping;
        }

        String sql = "SELECT provedor_tipo, target_voice_id FROM tts.voice_mappings WHERE LOWER(voice_openai) = LOWER(?);";
        Map<String, String> result = new HashMap<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String provedorTipo = rs.getString("provedor_tipo");
                    String targetVoiceId = rs.getString("target_voice_id");
                    if (provedorTipo != null && targetVoiceId != null) {
                        result.put(provedorTipo.toUpperCase(), targetVoiceId);
                    }
                }
            }

            voiceMappingsCache.put(key, result);
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error loading voice mappings for %s: %s", voiceOpenai, e.getMessage());
            return Map.of();
        }
    }
}
