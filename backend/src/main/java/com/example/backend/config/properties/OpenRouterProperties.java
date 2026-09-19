package com.example.backend.config.properties;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "physlive.ai.openrouter")
public record OpenRouterProperties(
        String apiKey,
        URI baseUrl,
        String model,
        String ocrModel,
        Duration connectTimeout,
        Duration readTimeout,
        int maxTokens,
        String systemPromptResource,
        String ocrPromptResource,
        String appUrl,
        String appName) {

    public OpenRouterProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null ? "" : model.trim();
        ocrModel = ocrModel == null ? "" : ocrModel.trim();
        systemPromptResource = systemPromptResource == null ? "" : systemPromptResource.trim();
        ocrPromptResource = ocrPromptResource == null ? "" : ocrPromptResource.trim();
        appUrl = appUrl == null ? "" : appUrl.trim();
        appName = appName == null ? "" : appName.trim();
        if (baseUrl == null || !"https".equalsIgnoreCase(baseUrl.getScheme())) {
            throw new IllegalArgumentException("OpenRouter base URL must be an HTTPS URL");
        }
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("OpenRouter connect timeout must be positive");
        }
        if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("OpenRouter read timeout must be positive");
        }
        if (maxTokens < 256 || maxTokens > 100_000) {
            throw new IllegalArgumentException("OpenRouter max tokens must be between 256 and 100000");
        }
        if (systemPromptResource.isEmpty()) {
            throw new IllegalArgumentException("OpenRouter system prompt resource is required");
        }
        if (ocrPromptResource.isEmpty()) {
            throw new IllegalArgumentException("OpenRouter OCR prompt resource is required");
        }
        if (!appUrl.isEmpty()) {
            URI parsed = URI.create(appUrl);
            if (!"https".equalsIgnoreCase(parsed.getScheme()) && !"http".equalsIgnoreCase(parsed.getScheme())) {
                throw new IllegalArgumentException("OpenRouter application URL must use HTTP or HTTPS");
            }
        }
    }
}
