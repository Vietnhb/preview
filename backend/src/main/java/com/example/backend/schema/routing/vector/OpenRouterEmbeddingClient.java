package com.example.backend.schema.routing.vector;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

import com.example.backend.config.properties.OpenRouterProperties;
import com.example.backend.config.properties.SchemaRoutingProperties;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public final class OpenRouterEmbeddingClient implements EmbeddingClient {
    private static final String PROVIDER_ID = "openrouter";

    private final RestClient restClient;
    private final OpenRouterProperties openRouter;
    private final SchemaRoutingProperties.Embedding settings;
    private final EmbeddingRetryPolicy retryPolicy;

    public OpenRouterEmbeddingClient(RestClient.Builder builder, OpenRouterProperties openRouter,
            SchemaRoutingProperties routing) {
        this.openRouter = openRouter;
        this.settings = routing.embedding();
        this.retryPolicy = new EmbeddingRetryPolicy(settings.maxAttempts(), settings.retryBackoff());
        requireSupportedProvider(settings.provider());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(settings.timeout());
        requestFactory.setReadTimeout(settings.timeout());
        this.restClient = builder.requestFactory(requestFactory).baseUrl(openRouter.baseUrl().toString()).build();
    }

    @Override
    public EmbeddingResult embed(String text) {
        if (!StringUtils.hasText(text)) throw new IllegalArgumentException("Embedding text must not be blank");
        if (!StringUtils.hasText(openRouter.apiKey())) {
            throw new IllegalStateException("Schema routing needs OPENROUTER_API_KEY to generate embeddings");
        }
        try {
            return retryPolicy.execute(() -> requestEmbedding(text), OpenRouterEmbeddingClient::isRetryable,
                    this::retryDelay);
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalStateException state
                    && state.getMessage() != null && state.getMessage().startsWith("Embedding provider ")) throw state;
            throw new IllegalStateException("Embedding request failed for provider=" + settings.provider()
                    + ", model=" + settings.model(), failure);
        }
    }

    private EmbeddingResult requestEmbedding(String text) {
            JsonNode response = restClient.post().uri("/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + openRouter.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", settings.model(), "input", text))
                    .retrieve().body(JsonNode.class);
            JsonNode vector = response == null ? null : response.path("data").path(0).path("embedding");
            if (vector == null || !vector.isArray()) {
                throw new IllegalStateException("Embedding provider returned no vector");
            }
            List<Double> values = new java.util.ArrayList<>(vector.size());
            for (JsonNode component : vector) {
                if (!component.isNumber()) throw new IllegalStateException("Embedding provider returned a non-numeric vector value");
                double value = component.asDouble();
                if (!Double.isFinite(value)) throw new IllegalStateException("Embedding provider returned a non-finite vector value");
                values.add(value);
            }
            if (values.size() != settings.dimension()) {
                throw new IllegalStateException("Embedding dimension mismatch for provider=" + settings.provider()
                        + ", model=" + settings.model() + ": configured=" + settings.dimension()
                        + ", returned=" + values.size());
            }
            return new EmbeddingResult(values);
    }

    static boolean isRetryable(RuntimeException failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ResourceAccessException) return true;
            if (current instanceof HttpServerErrorException) return true;
            if (current instanceof HttpClientErrorException client
                    && client.getStatusCode().value() == 429) return true;
            if (current instanceof RestClientResponseException response
                    && (response.getStatusCode().value() == 429 || response.getStatusCode().is5xxServerError())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Duration retryDelay(RuntimeException failure, int attempt) {
        return retryAfterDelay(failure, settings.retryBackoff(), attempt);
    }

    static Duration retryAfterDelay(RuntimeException failure, Duration configuredBackoff, int attempt) {
        if (configuredBackoff == null || configuredBackoff.isNegative()) {
            throw new IllegalArgumentException("configuredBackoff must be non-negative");
        }
        Throwable current = failure;
        while (current != null) {
            if (current instanceof RestClientResponseException response) {
                String retryAfter = response.getResponseHeaders().getFirst("Retry-After");
                if (retryAfter != null) {
                    try {
                        long seconds = Long.parseLong(retryAfter.trim());
                        if (seconds >= 0) {
                            return Duration.ofSeconds(Math.min(seconds,
                                    SchemaRoutingProperties.Embedding.MAX_RETRY_AFTER.toSeconds()));
                        }
                    } catch (NumberFormatException ignored) {
                        // Date-form values are ignored; the configured bounded backoff remains safe.
                    }
                }
            }
            current = current.getCause();
        }
        return configuredBackoff.multipliedBy(attempt);
    }

    static void requireSupportedProvider(String provider) {
        if (!PROVIDER_ID.equals(provider)) {
            throw new IllegalArgumentException("Embedding provider '" + provider
                    + "' is not implemented; configure physlive.schema-routing.embedding.provider=openrouter.");
        }
    }

    @Override public String providerId() { return PROVIDER_ID; }
    @Override public String modelId() { return settings.model(); }
    @Override public int dimension() { return settings.dimension(); }
}
