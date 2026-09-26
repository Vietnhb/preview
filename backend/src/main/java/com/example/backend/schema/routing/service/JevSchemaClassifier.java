package com.example.backend.schema.routing.service;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.example.backend.config.properties.JevProperties;
import com.example.backend.exception.EmbeddingUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;

/** Classifies approved physics schemas. */
@Component
public final class JevSchemaClassifier {
    private final RestClient client;
    private final JevProperties properties;

    public JevSchemaClassifier(RestClient.Builder builder, JevProperties properties) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.timeout()).build());
        factory.setReadTimeout(properties.timeout());
        this.client = builder.requestFactory(factory).baseUrl(properties.baseUrl().toString()).build();
        this.properties = properties;
    }

    public Result classifySchemas(String problemText, Map<String, String> schemaCriteria) {
        if (properties.apiKey().isBlank()) {
            throw new EmbeddingUnavailableException("JEV_API_KEY is required for Jev schema routing.");
        }
        if (schemaCriteria == null || schemaCriteria.isEmpty()) {
            throw new IllegalArgumentException("At least one approved schema is required for Jev routing.");
        }
        Map<String, Object> schemaQuestion = new LinkedHashMap<>();
        schemaQuestion.put("type", "choice");
        schemaQuestion.put("instructions", "Choose the best approved schema for the physics described. Use only the provided options; express uncertainty in confidence.");
        schemaQuestion.put("criteria", schemaCriteria);
        Map<String, Object> scopeQuestion = new LinkedHashMap<>();
        scopeQuestion.put("type", "noul");
        scopeQuestion.put("instructions", "Does this request seek a simulation of a physical system or phenomenon? Judge its intent against the supplied capability descriptions. Incomplete values, contradictions, unfamiliar objects, and missing equations do not make a physics request out of scope.");

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
            if (response == null) throw new IllegalStateException("Jev returned no response.");
            JsonNode answers = response.path("answers");
            var schema = choice(answers.path("schema"), schemaCriteria.keySet());
            JsonNode scope = answers.path("in_scope");
            if (!"noul".equals(scope.path("type").asText())) {
                throw new IllegalStateException("Jev returned an invalid scope answer.");
            }
            return new Result(schema.value(), schema.confidence(), schema.probabilities(),
                    probability(scope.path("noul")));
        } catch (EmbeddingUnavailableException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new EmbeddingUnavailableException("Jev schema routing request failed: " + failure.getMessage());
        }
    }

    private Choice choice(JsonNode answer, Set<String> options) {
        String value = answer.path("choice").asText();
        if (!"choice".equals(answer.path("type").asText()) || !options.contains(value)
                || !answer.path("probabilities").isObject()) {
            throw new IllegalStateException("Jev returned an invalid catalog choice.");
        }
        Map<String, Double> probabilities = new LinkedHashMap<>();
        answer.path("probabilities").fields().forEachRemaining(entry ->
                probabilities.put(entry.getKey(), probability(entry.getValue())));
        if (!probabilities.keySet().equals(options)) {
            throw new IllegalStateException("Jev choice probabilities do not match the supplied options.");
        }
        return new Choice(value, probability(answer.path("confidence")), probabilities);
    }

    private double probability(JsonNode value) {
        double probability = value.asDouble(Double.NaN);
        if (!value.isNumber() || !Double.isFinite(probability) || probability < 0 || probability > 1) {
            throw new IllegalStateException("Jev returned an invalid probability.");
        }
        return probability;
    }

    private record Choice(String value, double confidence, Map<String, Double> probabilities) {}

    public record Result(String choice, double confidence, Map<String, Double> probabilities, double inScope) {
        public Result {
            probabilities = Map.copyOf(probabilities);
        }
    }
}
