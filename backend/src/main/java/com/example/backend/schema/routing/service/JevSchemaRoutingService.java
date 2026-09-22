package com.example.backend.schema.routing.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import io.micrometer.core.instrument.MeterRegistry;

import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.config.properties.JevProperties;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.exception.SchemaRoutingException;
import com.example.backend.schema.routing.model.SchemaCandidate;
import com.example.backend.schema.routing.model.SchemaCandidate.VerificationEvidence;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.example.backend.schema.routing.model.SchemaSelectionScore;
import com.example.backend.service.problem.SchemaDefinitionService;

/** Jev-only schema routing. The database remains the authority for approved contracts. */
@Service
public final class JevSchemaRoutingService {
    private final SchemaDefinitionService schemas;
    private final JevSchemaClassifier classifier;
    private final JevProperties jev;
    private final MeterRegistry meters;

    public JevSchemaRoutingService(SchemaDefinitionService schemas, JevSchemaClassifier classifier,
            JevProperties jev, MeterRegistry meters) {
        this.schemas = schemas;
        this.classifier = classifier;
        this.jev = jev;
        this.meters = meters;
    }

    public SchemaRoutingDecision route(String problemText) {
        if (!StringUtils.hasText(problemText)) throw new IllegalArgumentException("Problem text must not be blank");
        if (problemText.length() > jev.maximumQueryCharacters()) {
            throw new IllegalArgumentException("Problem text exceeds the configured schema-routing query limit");
        }

        List<SchemaVersion> approved = schemas.approvedSchemas();
        if (approved.isEmpty()) throw new SchemaRoutingException("No approved schemas are available for Jev routing.");
        Map<String, SchemaVersion> byIdentity = new LinkedHashMap<>();
        Map<String, String> criteria = new LinkedHashMap<>();
        for (SchemaVersion schema : approved) {
            byIdentity.put(schema.getSchemaId(), schema);
            criteria.put(schema.getSchemaId(), description(schema));
        }

        JevSchemaClassifier.Result result = classifier.classify(problemText, criteria);
        meters.counter("physlive.schema.routing.jev.requests").increment();
        List<Map.Entry<String, Double>> ranked = new ArrayList<>(result.probabilities().entrySet());
        ranked.sort(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry::getKey));
        if (!ranked.stream().anyMatch(item -> item.getKey().equals(result.choice()))) {
            ranked.add(Map.entry(result.choice(), Math.max(result.confidence(), 0d)));
        }

        List<SchemaCandidate> candidates = ranked.stream()
                .filter(item -> byIdentity.containsKey(item.getKey()))
                .limit(jev.candidateTopK())
                .map(item -> candidate(byIdentity.get(item.getKey()), item.getValue(), ranked.indexOf(item) + 1))
                .toList();
        if (candidates.isEmpty()) throw new SchemaRoutingException("Jev returned a schema outside the approved catalog.");

        SchemaCandidate first = candidates.stream()
                .filter(item -> item.schemaId().equals(result.choice()))
                .findFirst().orElse(candidates.getFirst());
        double second = candidates.stream().filter(item -> item != first)
                .mapToDouble(SchemaCandidate::confidence).max().orElse(0d);
        double margin = Math.max(0d, first.confidence() - second);
        boolean selected = result.choice().equals(first.schemaId())
                && result.inScope() >= 0.5
                && first.confidence() >= jev.minimumConfidence()
                && margin >= jev.minimumMargin();
        String reason = selected ? "JEV_SCHEMA_SELECTED" :
                result.inScope() < 0.5 ? "JEV_OUT_OF_SCOPE" :
                margin < jev.minimumMargin() ? "JEV_LOW_MARGIN" : "JEV_LOW_CONFIDENCE";
        if (selected) meters.counter("physlive.schema.routing.jev.selected").increment();
        else meters.counter("physlive.schema.routing.jev.ambiguous").increment();
        return new SchemaRoutingDecision(selected ? SchemaRoutingDecision.Status.SELECTED
                : SchemaRoutingDecision.Status.AMBIGUOUS, reason, candidates,
                clamp(first.confidence()), clamp(margin));
    }

    private SchemaCandidate candidate(SchemaVersion schema, double probability, int rank) {
        CandidateContractProjection contract = CandidateContractProjection.from(schema.getSchemaId(),
                schema.getVersion(), schema.getTopic(), schema.getName(), schema.getDefinition());
        double confidence = clamp(probability);
        return new SchemaCandidate(contract,
                new SchemaSelectionScore(rank, confidence),
                new VerificationEvidence(confidence, 1, confidence, 0,
                        List.of("JEV_CHOICE", "APPROVED_DATABASE_CONTRACT")), confidence);
    }

    private String description(SchemaVersion schema) {
        var definition = schema.getDefinition();
        String quantities = "";
        if (definition != null && definition.path("requiredQuantities").isArray()) {
            quantities = java.util.stream.StreamSupport.stream(definition.path("requiredQuantities").spliterator(), false)
                    .map(item -> item.path("key").asText())
                    .filter(StringUtils::hasText)
                    .sorted().limit(16).toList().toString();
        }
        String entities = "";
        if (definition != null) {
            var types = definition.path("entityContract").path("types");
            if (!types.isArray()) types = definition.path("entityTypes");
            if (types.isArray()) {
                entities = java.util.stream.StreamSupport.stream(types.spliterator(), false)
                        .map(item -> "%s[%s..%s]".formatted(item.path("type").asText(),
                                item.has("min") ? item.path("min").asInt(1) : item.path("minCount").asInt(1),
                                item.has("max") ? item.path("max").asInt(1)
                                        : item.has("maxCount") ? item.path("maxCount").asInt(1)
                                        : item.has("min") ? item.path("min").asInt(1) : item.path("minCount").asInt(1)))
                        .filter(StringUtils::hasText).sorted().limit(16).toList().toString();
            }
        }
        String visualIntent = "";
        if (definition != null) {
            var visualization = definition.path("visualization");
            var series = visualization.path("series");
            var labels = series.isArray()
                    ? java.util.stream.StreamSupport.stream(series.spliterator(), false)
                        .flatMap(item -> java.util.stream.Stream.of(item.path("key").asText(), item.path("label").asText()))
                        .filter(StringUtils::hasText).distinct().limit(16).toList()
                    : List.of();
            var presentation = visualization.path("presentation");
            var actors = presentation.path("actors");
            var actorHints = actors.isArray()
                    ? java.util.stream.StreamSupport.stream(actors.spliterator(), false)
                        .flatMap(item -> java.util.stream.Stream.of(item.path("assetHint").asText(), item.path("asset").asText()))
                        .filter(StringUtils::hasText).distinct().limit(12).toList()
                    : List.of();
            var effects = presentation.path("effects");
            var effectNames = effects.isArray()
                    ? java.util.stream.StreamSupport.stream(effects.spliterator(), false)
                        .map(item -> item.asText()).filter(StringUtils::hasText).distinct().limit(12).toList()
                    : List.of();
            var props = presentation.path("props");
            var propNames = props.isArray()
                    ? java.util.stream.StreamSupport.stream(props.spliterator(), false)
                        .map(item -> item.asText()).filter(StringUtils::hasText).distinct().limit(12).toList()
                    : List.of();
            visualIntent = "%s %s layout=%s actors=%s props=%s effects=%s series=%s".formatted(
                    visualization.path("scene").asText(), presentation.path("environment").asText(),
                    presentation.path("layout").asText(), actorHints, propNames, effectNames, labels);
        }
        return "%s | topic=%s | model=%s | required quantities=%s | entity types=%s | visual intent=%s".formatted(
                schema.getName(), schema.getTopic(), definition == null ? "" : definition.path("model").asText(schema.getSchemaId()),
                quantities, entities, visualIntent);
    }

    private double clamp(double value) {
        if (!Double.isFinite(value)) return 0;
        return Math.max(0, Math.min(1, value));
    }
}
