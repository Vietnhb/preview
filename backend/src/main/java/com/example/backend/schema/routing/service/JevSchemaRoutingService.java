package com.example.backend.schema.routing.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import io.micrometer.core.instrument.MeterRegistry;

import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.ai.extraction.model.SpecificationDocument;
import com.example.backend.config.properties.JevProperties;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.exception.SchemaRoutingException;
import com.example.backend.schema.routing.model.SchemaCandidate;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.example.backend.service.problem.SchemaDefinitionService;

/** Classifies approved schemas and verifies the pinned extraction contract. */
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

        JevSchemaClassifier.Result result = classifier.classifySchemas(problemText, criteria);
        meters.counter("physlive.schema.routing.jev.requests").increment();
        List<Map.Entry<String, Double>> ranked = new ArrayList<>(result.probabilities().entrySet());
        ranked.sort(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry::getKey));
        if (ranked.stream().noneMatch(item -> item.getKey().equals(result.choice()))) {
            ranked.add(Map.entry(result.choice(), Math.max(result.confidence(), 0d)));
        }

        List<SchemaCandidate> candidates = ranked.stream()
                .filter(item -> byIdentity.containsKey(item.getKey()))
                .map(item -> candidate(byIdentity.get(item.getKey()), item.getValue()))
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
        String reason;
        if (selected) {
            reason = "JEV_SCHEMA_SELECTED";
        } else if (result.inScope() < 0.5) {
            reason = "JEV_OUT_OF_SCOPE";
        } else if (margin < jev.minimumMargin()) {
            reason = "JEV_LOW_MARGIN";
        } else {
            reason = "JEV_LOW_CONFIDENCE";
        }
        if (selected) meters.counter("physlive.schema.routing.jev.selected").increment();
        else meters.counter("physlive.schema.routing.jev.ambiguous").increment();
        return new SchemaRoutingDecision(selected ? SchemaRoutingDecision.Status.SELECTED
                : SchemaRoutingDecision.Status.AMBIGUOUS, reason, candidates,
                clamp(first.confidence()), clamp(margin));
    }

    /** Backend contract gate for the selected schema and extracted objects. */
    public Verification verify(SchemaRoutingDecision routing, SpecificationDocument document) {
        if (routing == null || document == null) {
            return new Verification(List.of("A routed schema and extracted specification are required."));
        }
        List<String> findings = new ArrayList<>();
        SchemaCandidate pinned = routing.candidates().stream().filter(item ->
                item.schemaId().equals(document.schemaId()) && item.schemaVersion().equals(document.schemaVersion()))
                .findFirst().orElse(null);
        if (pinned == null) {
            findings.add("The extracted schema is outside the pinned JEV candidate set.");
            return new Verification(List.copyOf(findings));
        }

        Set<String> objectIds = new HashSet<>();
        document.objects().forEach(object -> {
            if (object == null || !StringUtils.hasText(object.id()) || !objectIds.add(object.id())) {
                findings.add("Every physical object must have a unique non-empty id.");
            }
        });

        return new Verification(findings.stream().distinct().limit(8).toList());
    }

    /** Re-checks a pinned document after teacher answers without re-routing it. */
    public Verification verifyPinned(SpecificationDocument document) {
        if (document == null || !StringUtils.hasText(document.schemaId())
                || !StringUtils.hasText(document.schemaVersion())) {
            return new Verification(List.of("The confirmed specification is missing its pinned schema."));
        }
        SchemaVersion schema = schemas.requirePublishedVersion(document.schemaId(), document.schemaVersion());
        SchemaCandidate pinned = candidate(schema, 1);
        return verify(new SchemaRoutingDecision(SchemaRoutingDecision.Status.SELECTED,
                "PINNED_AFTER_CONFIRMATION", List.of(pinned), 1, 1), document);
    }

    public record Verification(List<String> findings) {
        public Verification {
            findings = List.copyOf(findings == null ? List.of() : findings);
        }

        public boolean passed() { return findings.isEmpty(); }
    }

    private SchemaCandidate candidate(SchemaVersion schema, double probability) {
        CandidateContractProjection contract = CandidateContractProjection.from(schema.getSchemaId(),
                schema.getVersion(), schema.getTopic(), schema.getName(), schemas.projectCoreTypes(schema.getDefinition()));
        double confidence = clamp(probability);
        List<String> signals = new ArrayList<>(List.of("JEV_CHOICE", "APPROVED_DATABASE_CONTRACT"));
        return new SchemaCandidate(contract, signals, confidence);
    }

    private String description(SchemaVersion schema) {
        var definition = schema.getDefinition();
        return "schemaId=%s | version=%s | name=%s | topic=%s | capabilityPack=%s".formatted(
                schema.getSchemaId(), schema.getVersion(), schema.getName(), schema.getTopic(),
                definition == null ? "{}" : definition.toString());
    }

    private double clamp(double value) {
        if (!Double.isFinite(value)) return 0;
        return Math.clamp(value, 0, 1);
    }
}
