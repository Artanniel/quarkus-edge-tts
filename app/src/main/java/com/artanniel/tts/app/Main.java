package com.artanniel.tts.app;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Bootstrap class for the Quarkus application.
 * It does not contain any business logic – all work is delegated to the
 * modules (core, engine‑java, api‑rest, …). The class only starts Quarkus
 * and optionally logs the version on boot.
 */
public class Main {

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
    String version;

    public static void main(String[] args) {
        Quarkus.run();
    }

    void onStart(@Observes StartupEvent ev) {
        System.out.println("🚀 Quarkus Edge‑TTS starting – version=" + version);
    }
}
