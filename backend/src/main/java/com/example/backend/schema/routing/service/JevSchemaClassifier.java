package com.example.backend.schema.routing.service;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.example.backend.config.properties.JevProperties;
import com.example.backend.exception.EmbeddingUnavailableException;
import com.example.backend.simulation.assets.AssetRoutingDecision;
import com.example.backend.simulation.assets.SvgAssetCatalog;
import com.fasterxml.jackson.databind.JsonNode;

/** One typed JEV request routes physics and independently evaluates existing SVGs. */
@Component
public final class JevSchemaClassifier {
    private final RestClient client;
    private final JevProperties properties;
    private final SvgAssetCatalog catalog;

    public JevSchemaClassifier(RestClient.Builder builder, JevProperties properties, SvgAssetCatalog catalog) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.timeout()).build());
        factory.setReadTimeout(properties.timeout());
        this.client = builder.requestFactory(factory).baseUrl(properties.baseUrl().toString()).build();
        this.properties = properties;
        this.catalog = catalog;
    }

    public Result classify(String problemText, Map<String, String> schemaCriteria,
            Map<String, String> entityCountCriteria) {
        return classify(problemText, schemaCriteria, entityCountCriteria, true);
    }

    public Result classifySchemas(String problemText, Map<String, String> schemaCriteria,
            Map<String, String> entityCountCriteria) {
        return classify(problemText, schemaCriteria, entityCountCriteria, false);
    }

    private Result classify(String problemText, Map<String, String> schemaCriteria,
            Map<String, String> entityCountCriteria, boolean includeAssets) {
        if (properties.apiKey().isBlank()) {
            throw new EmbeddingUnavailableException("JEV_API_KEY is required for Jev schema routing.");
        }
        if (schemaCriteria == null || schemaCriteria.isEmpty()) {
            throw new IllegalArgumentException("At least one approved schema is required for Jev routing.");
        }
        Map<String, Object> schemaQuestion = new LinkedHashMap<>();
        schemaQuestion.put("type", "choice");
        schemaQuestion.put("instructions", "Choose the single approved physics schema that best matches the user's request. Use only the provided options. Do not invent a schema. Respect the declared entity and visual actor capacity: never choose a one-body scene for an explicitly multi-body request; return the lowest-confidence/ambiguous route when no approved candidate can represent the stated bodies.");
        schemaQuestion.put("criteria", schemaCriteria);
        Map<String, Object> scopeQuestion = new LinkedHashMap<>();
        scopeQuestion.put("type", "noul");
        scopeQuestion.put("instructions", "Does this request describe a physics simulation problem suitable for the approved high-school physics catalog?");

        Map<String, Object> questions = new LinkedHashMap<>();
        questions.put("schema", schemaQuestion);
        questions.put("in_scope", scopeQuestion);
        if (entityCountCriteria != null && !entityCountCriteria.isEmpty()) {
            questions.put("entity_count", Map.of(
                    "type", "choice",
                    "instructions", "Choose how many distinct physical bodies the user explicitly requests. Count generic numbered bodies as distinct. Do not count coordinate axes, fields, environments, or decorative apparatus. Use only the supplied catalog-derived choices.",
                    "criteria", entityCountCriteria));
        }
        var assetQuestions = new LinkedHashMap<String, String>();
        if (includeAssets) {
            for (SvgAssetCatalog.Asset asset : catalog.entries()) {
                String questionId = "asset_" + asset.id();
                assetQuestions.put(questionId, asset.id());
                questions.put(questionId, assetQuestion(asset));
            }
        }
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
            Choice entityCount = entityCountCriteria == null || entityCountCriteria.isEmpty() ? null
                    : choice(answers.path("entity_count"), entityCountCriteria.keySet());
            var candidates = new ArrayList<AssetRoutingDecision.Candidate>();
            for (var entry : assetQuestions.entrySet()) {
                var asset = choice(answers.path(entry.getKey()), Set.of("EXACT", "SUBSTITUTE", "IRRELEVANT"));
                if (!"IRRELEVANT".equals(asset.value())) {
                    candidates.add(new AssetRoutingDecision.Candidate(entry.getValue(), asset.value(), asset.confidence()));
                }
            }
            JsonNode scope = answers.path("in_scope");
            if (!"noul".equals(scope.path("type").asText())) {
                throw new IllegalStateException("Jev returned an invalid scope answer.");
            }
            return new Result(schema.value(), schema.confidence(), schema.probabilities(),
                    probability(scope.path("noul")), response.path("model").asText(properties.model()),
                    includeAssets ? new AssetRoutingDecision(catalog.checksum(), candidates) : null,
                    entityCount == null ? null : entityCount.value());
        } catch (EmbeddingUnavailableException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new EmbeddingUnavailableException("Jev schema routing request failed: " + failure.getMessage());
        }
    }

    public AssetRoutingDecision classifyAssets(String problemText, Map<String, Object> specificationContext) {
        if (properties.apiKey().isBlank()) {
            throw new EmbeddingUnavailableException("JEV_API_KEY is required for asset routing.");
        }
        Map<String, String> assetQuestions = new LinkedHashMap<>();
        Map<String, Object> questions = new LinkedHashMap<>();
        for (SvgAssetCatalog.Asset asset : catalog.entries()) {
            String questionId = "asset_" + asset.id();
            assetQuestions.put(questionId, asset.id());
            questions.put(questionId, assetQuestion(asset));
        }
        Map<String, Object> request = Map.of(
                "state", Map.of("problem", problemText, "confirmedSpecification", specificationContext),
                "model", properties.model(),
                "questions", questions);
        try {
            JsonNode response = client.post().uri("/systemone")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(JsonNode.class);
            if (response == null) throw new IllegalStateException("JEV returned no asset response.");
            JsonNode answers = response.path("answers");
            var candidates = new ArrayList<AssetRoutingDecision.Candidate>();
            for (var entry : assetQuestions.entrySet()) {
                var asset = choice(answers.path(entry.getKey()), Set.of("EXACT", "SUBSTITUTE", "IRRELEVANT"));
                if (!"IRRELEVANT".equals(asset.value())) {
                    candidates.add(new AssetRoutingDecision.Candidate(entry.getValue(), asset.value(), asset.confidence()));
                }
            }
            return new AssetRoutingDecision(catalog.checksum(), candidates);
        } catch (EmbeddingUnavailableException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new EmbeddingUnavailableException("JEV asset routing request failed: " + failure.getMessage());
        }
    }

    private Map<String, Object> assetQuestion(SvgAssetCatalog.Asset asset) {
        return Map.of(
                "type", "choice",
                "instructions", Map.of(
                        "question", "Evaluate whether this catalog SVG can depict a physical object in the confirmed specification. "
                                + "Judge its actual appearance and named object, not the physics topic. Mark EXACT only for a faithful depiction; "
                                + "mark SUBSTITUTE when it can serve as an explicitly reviewable visual replacement; otherwise mark IRRELEVANT. "
                                + "Do not infer appearance or add apparatus.",
                        "asset", Map.of("label", asset.label(), "description", asset.description(), "kind", asset.kind())),
                "criteria", Map.of(
                        "EXACT", "Faithfully depicts the described object.",
                        "SUBSTITUTE", "Can be used as an explicitly reviewed symbolic representation.",
                        "IRRELEVANT", "Does not represent an object in the confirmed specification."));
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

    public record Result(String choice, double confidence, Map<String, Double> probabilities,
            double inScope, String model, AssetRoutingDecision assets, String entityCountChoice) {
        public Result(String choice, double confidence, Map<String, Double> probabilities, double inScope, String model) {
            this(choice, confidence, probabilities, inScope, model, null, null);
        }

        public Result {
            probabilities = Map.copyOf(probabilities);
        }
    }
}
