package com.example.backend.config.properties;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "physlive.upload")
public record UploadProperties(long maxImageBytes, Set<String> allowedImageTypes) {
    public UploadProperties {
        if (maxImageBytes <= 0) throw new IllegalArgumentException("Maximum image size must be positive");
        allowedImageTypes = allowedImageTypes == null
                ? Set.of()
                : allowedImageTypes.stream().map(String::trim).filter(value -> !value.isEmpty())
                        .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (allowedImageTypes.isEmpty()) throw new IllegalArgumentException("Allowed image types are required");
    }
}
