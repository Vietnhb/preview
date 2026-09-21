package com.example.backend.config.properties;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime configuration for Jev schema classification. */
@ConfigurationProperties(prefix = "physlive.ai.jev")
public record JevProperties(
        String apiKey,
        URI baseUrl,
        String model,
        Duration timeout) {

    public JevProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null || model.isBlank() ? "jev-latest" : model.trim();
        if (baseUrl == null || !"https".equalsIgnoreCase(baseUrl.getScheme())) {
            throw new IllegalArgumentException("Jev base URL must be an HTTPS URL");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Jev timeout must be positive");
        }
    }
}
