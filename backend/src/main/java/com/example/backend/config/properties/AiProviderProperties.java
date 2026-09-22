package com.example.backend.config.properties;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "physlive.ai.provider")
public record AiProviderProperties(
        String name,
        String apiKey,
        URI baseUrl,
        String textModel,
        String visionModel,
        String reasoningEffort,
        double temperature,
        int maxAttempts,
        boolean strictStructuredOutput,
        Duration connectTimeout,
        Duration readTimeout,
        int maxCompletionTokens,
        String systemPromptResource,
        String ocrPromptResource) {

    public AiProviderProperties {
        name = name == null ? "" : name.trim();
        apiKey = apiKey == null ? "" : apiKey.trim();
        textModel = textModel == null ? "" : textModel.trim();
        visionModel = visionModel == null ? "" : visionModel.trim();
        reasoningEffort = reasoningEffort == null ? "" : reasoningEffort.trim().toLowerCase(java.util.Locale.ROOT);
        systemPromptResource = systemPromptResource == null ? "" : systemPromptResource.trim();
        ocrPromptResource = ocrPromptResource == null ? "" : ocrPromptResource.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("AI provider name is required");
        if (baseUrl == null || !"https".equalsIgnoreCase(baseUrl.getScheme())) {
            throw new IllegalArgumentException("AI provider base URL must be an HTTPS URL");
        }
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("AI provider connect timeout must be positive");
        }
        if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("AI provider read timeout must be positive");
        }
        if (maxCompletionTokens < 256 || maxCompletionTokens > 100_000) {
            throw new IllegalArgumentException("AI max completion tokens must be between 256 and 100000");
        }
        if (!java.util.Set.of("low", "medium", "high").contains(reasoningEffort)) {
            throw new IllegalArgumentException("AI reasoning effort must be low, medium, or high");
        }
        if (!Double.isFinite(temperature) || temperature < 0 || temperature > 2) {
            throw new IllegalArgumentException("AI temperature must be between 0 and 2");
        }
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("AI max attempts must be between 1 and 3");
        }
        if (systemPromptResource.isEmpty()) {
            throw new IllegalArgumentException("AI system prompt resource is required");
        }
        if (ocrPromptResource.isEmpty()) {
            throw new IllegalArgumentException("AI OCR prompt resource is required");
        }
    }
}
