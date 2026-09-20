package com.example.backend.schema.routing.vector;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.example.backend.config.properties.OpenRouterProperties;
import com.example.backend.config.properties.SchemaRoutingProperties;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public final class OpenRouterEmbeddingClient implements EmbeddingClient {
    private static final String PROVIDER_ID = "openrouter";

    private final RestClient restClient;
    private final OpenRouterProperties openRouter;
    private final SchemaRoutingProperties.Embedding settings;

    public OpenRouterEmbeddingClient(RestClient.Builder builder, OpenRouterProperties openRouter,
            SchemaRoutingProperties routing) {
        this.openRouter = openRouter;
        this.settings = routing.embedding();
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
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalStateException state
                    && state.getMessage() != null && state.getMessage().startsWith("Embedding ")) throw state;
            throw new IllegalStateException("Embedding request failed for provider=" + settings.provider()
                    + ", model=" + settings.model(), failure);
        }
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
