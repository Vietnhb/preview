package com.example.backend.schema.routing.service;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
        var assetQuestions = new LinkedHashMap<String, String>();
        for (SvgAssetCatalog.Asset asset : catalog.entries()) {
            String questionId = "asset_" + asset.id();
            assetQuestions.put(questionId, asset.id());
            questions.put(questionId, Map.of(
                    "type", "choice",
                    "instructions", Map.of(
                            "question", "How faithfully does this SVG depict any physical object or apparatus "
                                    + "described in the user's text? Judge appearance and the named object, not physics topic. "
                                    + "Unspecified generic bodies require SUBSTITUTE. Respect explicitly requested colors. "
                                    + "Do not add apparatus not described in the text. If the text describes one or more "
                                    + "moving bodies but does not specify their appearance, at least one actor SVG must be "
                                    + "classified SUBSTITUTE; do not classify every actor candidate IRRELEVANT. "
                                    + "A SUBSTITUTE is a catalog choice for teacher approval, not an invented physical fact.",
                            "asset", Map.of("label", asset.label(), "description", asset.description(), "kind", asset.kind())),
                    "criteria", Map.of(
                            "EXACT", "The SVG faithfully depicts an explicitly described object's appearance.",
                            "SUBSTITUTE", "An object is described but this SVG is only a plausible symbolic replacement needing approval.",
                            "IRRELEVANT", "The SVG is unrelated to the objects described or would introduce unrequested apparatus.")));
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
                    new AssetRoutingDecision(catalog.checksum(), candidates));
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

    public record Result(String choice, double confidence, Map<String, Double> probabilities,
            double inScope, String model, AssetRoutingDecision assets) {
        public Result(String choice, double confidence, Map<String, Double> probabilities, double inScope, String model) {
            this(choice, confidence, probabilities, inScope, model, null);
        }

        public Result {
            probabilities = Map.copyOf(probabilities);
        }
    }
}
