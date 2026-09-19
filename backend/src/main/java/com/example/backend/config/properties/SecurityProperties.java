package com.example.backend.config.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "physlive.security")
public record SecurityProperties(List<String> allowedOrigins, int passwordStrength) {
    public SecurityProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        if (allowedOrigins.stream().anyMatch(origin -> origin.equals("*") || origin.contains("*"))) {
            throw new IllegalArgumentException("CORS origins must be explicit when credentials are enabled");
        }
        if (passwordStrength < 10 || passwordStrength > 16) {
            throw new IllegalArgumentException("BCrypt strength must be between 10 and 16");
        }
    }
}
