package com.example.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "physlive.assets")
public record AssetSelectionProperties(double minimumConfidence) {
    public AssetSelectionProperties {
        if (!Double.isFinite(minimumConfidence) || minimumConfidence < 0 || minimumConfidence > 1) {
            throw new IllegalArgumentException("Asset confidence threshold must be in [0, 1]");
        }
    }
}
