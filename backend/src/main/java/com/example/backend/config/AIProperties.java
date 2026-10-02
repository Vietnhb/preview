package com.example.backend.config;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Provider-neutral settings; visual.provider selects the actual wire protocol. */
@ConfigurationProperties(prefix = "physlive.ai")
public record AIProperties(Provider provider, Visual visual) {
    public AIProperties {
        if (provider == null || visual == null)
            throw new IllegalArgumentException("AI provider and visual settings are required");
    }

    public record Provider(String apiKey, URI baseUrl, String textModel, String visionModel,
            String ocrPromptResource, Duration connectTimeout, Duration readTimeout,
            double understandingTemperature) {
        public Provider {
            apiKey = clean(apiKey);
            textModel = required(textModel, "AI text model");
            visionModel = required(visionModel, "AI vision model");
            ocrPromptResource = required(ocrPromptResource, "AI OCR prompt resource");
            requireHttpUrl(baseUrl, "AI base URL");
            requirePositive(connectTimeout, "AI connect timeout");
            requirePositive(readTimeout, "AI read timeout");
            requireTemperature(understandingTemperature, "AI understanding temperature");
        }
    }

    public record Visual(String apiKey, String provider, URI baseUrl, String model, double temperature,
            Duration readTimeout, boolean strictStructuredOutput, boolean supportsResponseFormat,
            int maxCompletionTokens, String reasoningEffort) {
        public Visual {
            apiKey = clean(apiKey);
            provider = clean(provider).isEmpty() ? "openai_compatible" : provider.trim().toLowerCase(java.util.Locale.ROOT);
            model = required(model, "AI visual model");
            requireHttpUrl(baseUrl, "AI visual base URL");
            requirePositive(readTimeout, "AI visual read timeout");
            requireTemperature(temperature, "AI visual temperature");
            if (maxCompletionTokens < 1)
                throw new IllegalArgumentException("AI visual max completion tokens must be positive");
            reasoningEffort = clean(reasoningEffort);
        }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String required(String value, String label) {
        String result = clean(value);
        if (result.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return result;
    }
    private static void requireHttpUrl(URI value, String label) {
        if (value == null || value.getHost() == null || !Set.of("http", "https").contains(value.getScheme()))
            throw new IllegalArgumentException(label + " must be an HTTP(S) URL");
    }
    private static void requirePositive(Duration value, String label) {
        if (value == null || value.isNegative() || value.isZero())
            throw new IllegalArgumentException(label + " must be positive");
    }
    private static void requireTemperature(double value, String label) {
        if (!Double.isFinite(value) || value < 0 || value > 2)
            throw new IllegalArgumentException(label + " must be in [0, 2]");
    }
}
