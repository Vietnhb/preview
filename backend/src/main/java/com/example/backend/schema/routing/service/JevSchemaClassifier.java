package com.example.backend.schema.routing.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.example.backend.config.properties.JevProperties;
import com.example.backend.exception.EmbeddingUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Calls Jev only for typed schema classification; it never creates or edits a schema. */
@Component
public final class JevSchemaClassifier {
    private final RestClient client;
    private final JevProperties properties;
    private final ObjectMapper mapper;

    public JevSchemaClassifier(RestClient.Builder builder, JevProperties properties, ObjectMapper mapper) {
        this.client = builder.baseUrl(properties.baseUrl().toString()).build();
        this.properties = properties;
        this.mapper = mapper;
    }

    public Result classify(String problemText, Map<String, String> schemaCriteria) {
        if (properties.apiKey().isBlank()) {
            throw new EmbeddingUnavailableException("JEV_API_KEY is required for Jev schema routing.");
        }
        if (schemaCriteria == null || schemaCriteria.isEmpty()) {
            throw new IllegalArgumentException("At least one approved schema is required for Jev routing.");
        }
        Map<String, Object> schemaQuestion = new LinkedHashMap<>();
        schemaQuestion.put("type", "choice");
        schemaQuestion.put("instructions", "Choose the single approved physics schema that best matches the user's request. Use only the provided options. Do not invent a schema.");
        schemaQuestion.put("criteria", schemaCriteria);
        Map<String, Object> scopeQuestion = new LinkedHashMap<>();
        scopeQuestion.put("type", "noul");
        scopeQuestion.put("instructions", "Does this request describe a physics simulation problem suitable for the approved high-school physics catalog?");

        Map<String, Object> questions = new LinkedHashMap<>();
        questions.put("schema", schemaQuestion);
        questions.put("in_scope", scopeQuestion);
        Map<String, Object> request = Map.of(
                "state", problemText,
                "model", properties.model(),
                "questions", questions);
        try {
            JsonNode response = client.post()
                    .uri("/systemone")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.path("answers").path("schema").hasNonNull("choice")) {
                throw new IllegalStateException("Jev returned no schema choice.");
            }
            JsonNode answer = response.path("answers").path("schema");
            Map<String, Double> probabilities = new LinkedHashMap<>();
            answer.path("probabilities").fields().forEachRemaining(entry ->
                    probabilities.put(entry.getKey(), entry.getValue().asDouble()));
            return new Result(answer.path("choice").asText(), answer.path("confidence").asDouble(),
                    probabilities, response.path("answers").path("in_scope").path("noul").asDouble(),
                    response.path("model").asText(properties.model()));
        } catch (EmbeddingUnavailableException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new EmbeddingUnavailableException("Jev schema routing request failed: " + failure.getMessage());
        }
    }

    public record Result(String choice, double confidence, Map<String, Double> probabilities,
            double inScope, String model) {
        public Result {
            probabilities = Map.copyOf(probabilities);
        }
    }
}
