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
        Duration timeout,
        int candidateTopK,
        double minimumConfidence,
        double minimumMargin,
        int maximumQueryCharacters,
        int maximumPromptCharacters) {

    public JevProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null || model.isBlank() ? "jev-latest" : model.trim();
        if (baseUrl == null || !"https".equalsIgnoreCase(baseUrl.getScheme())) {
            throw new IllegalArgumentException("Jev base URL must be an HTTPS URL");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Jev timeout must be positive");
        }
        if (candidateTopK < 1 || candidateTopK > 20) throw new IllegalArgumentException("Jev candidate top-k must be in [1, 20]");
        if (!betweenZeroAndOne(minimumConfidence) || !betweenZeroAndOne(minimumMargin)) {
            throw new IllegalArgumentException("Jev confidence thresholds must be in [0, 1]");
        }
        if (maximumQueryCharacters < 1 || maximumQueryCharacters > 100_000) {
            throw new IllegalArgumentException("Jev maximum query characters must be in [1, 100000]");
        }
        if (maximumPromptCharacters < 1_000 || maximumPromptCharacters > 100_000) {
            throw new IllegalArgumentException("Jev maximum prompt characters must be in [1000, 100000]");
        }
    }

    private static boolean betweenZeroAndOne(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }
}
