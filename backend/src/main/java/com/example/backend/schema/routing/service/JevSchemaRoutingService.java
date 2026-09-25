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
import com.example.backend.ai.extraction.model.ConversationTurn;
import com.example.backend.simulation.assets.VisualBinding;
import com.example.backend.simulation.assets.VisualTargets;
import com.example.backend.config.properties.JevProperties;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.exception.SchemaRoutingException;
import com.example.backend.schema.routing.model.SchemaCandidate;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.example.backend.service.problem.SchemaDefinitionService;

/** Classifies approved schemas first; routes catalog assets only after spec confirmation. */
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
        if (!ranked.stream().anyMatch(item -> item.getKey().equals(result.choice()))) {
            ranked.add(Map.entry(result.choice(), Math.max(result.confidence(), 0d)));
        }

        List<SchemaCandidate> candidates = ranked.stream()
                .filter(item -> byIdentity.containsKey(item.getKey()))
                .limit(jev.candidateTopK())
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
        String reason = selected ? "JEV_SCHEMA_SELECTED" :
                result.inScope() < 0.5 ? "JEV_OUT_OF_SCOPE" :
                margin < jev.minimumMargin() ? "JEV_LOW_MARGIN" : "JEV_LOW_CONFIDENCE";
        if (selected) meters.counter("physlive.schema.routing.jev.selected").increment();
        else meters.counter("physlive.schema.routing.jev.ambiguous").increment();
        return new SchemaRoutingDecision(selected ? SchemaRoutingDecision.Status.SELECTED
                : SchemaRoutingDecision.Status.AMBIGUOUS, reason, candidates,
                clamp(first.confidence()), clamp(margin));
    }

    /** Classify assets only after the confirmed specification pins its approved schema. */
    public SchemaRoutingDecision routeAssets(String problemText, SpecificationDocument document,
            List<ConversationTurn> conversation, com.fasterxml.jackson.databind.JsonNode assetRequestSummary) {
        if (document == null) {
            throw new IllegalArgumentException("A confirmed schema specification is required before asset routing.");
        }
        if (assetRequestSummary == null || !assetRequestSummary.path("assetRequests").isArray()) {
            throw new IllegalArgumentException("A validated LLM asset request summary is required before JEV classification.");
        }
        SchemaVersion schema = schemas.requireCurrentApproved(document.schemaId(), document.schemaVersion());
        SchemaCandidate pinned = candidate(schema, 1);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("schema", Map.of("schemaId", document.schemaId(), "schemaVersion", document.schemaVersion()));
        context.put("objects", document.objects().stream().map(object -> Map.of(
                "id", object.id(), "label", object.label(), "type", object.type())).toList());
        context.put("assetRequestSummary", assetRequestSummary);
        context.put("relations", document.relations());
        context.put("visualTargets", VisualTargets.read(schemas.visualization(schema.getDefinition())));
        context.put("conversation", conversation == null ? List.of() : conversation);
        var assets = classifier.classifyAssets(problemText, context);
        return new SchemaRoutingDecision(SchemaRoutingDecision.Status.SELECTED,
                "JEV_ASSETS_ROUTED", List.of(pinned), 1, 1, assets);
    }

    /**
     * Backend contract gate for the selected schema, extracted objects, renderer
     * slots, and JEV-routed catalog candidates.
     */
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

        SchemaVersion schema = schemas.requireCurrentApproved(document.schemaId(), document.schemaVersion());
        List<VisualTargets.Target> targets = VisualTargets.read(schemas.visualization(schema.getDefinition()));
        Set<String> expectedTargets = targets.stream().map(VisualTargets.Target::targetId).collect(java.util.stream.Collectors.toSet());
        Set<String> objectIds = new HashSet<>();
        document.objects().forEach(object -> {
            if (object == null || !StringUtils.hasText(object.id()) || !objectIds.add(object.id())) {
                findings.add("Every physical object must have a unique non-empty id.");
            }
        });

        List<VisualBinding> bindings = document.visualBindings();
        boolean visualRoutingSupplied = routing.assets() != null || !bindings.isEmpty();
        Set<String> actualTargets = new HashSet<>();
        Set<String> actorEntities = new HashSet<>();
        for (VisualBinding binding : bindings) {
            if (binding == null || !actualTargets.add(binding.targetId())) {
                findings.add("Each renderer target must have exactly one visual binding.");
                continue;
            }
            if (!expectedTargets.contains(binding.targetId())) {
                findings.add("Visual binding target is not declared by the selected schema: " + binding.targetId());
                continue;
            }
            VisualTargets.Target target = targets.stream()
                    .filter(item -> item.targetId().equals(binding.targetId())).findFirst().orElse(null);
            if (target != null && "actor".equals(target.kind())) {
                if (!StringUtils.hasText(binding.entityId()) || !actorEntities.add(binding.entityId())) {
                    findings.add("Each moving actor target must reference a different extracted physical object.");
                } else if (!objectIds.contains(binding.entityId())) {
                    findings.add("Visual actor " + binding.targetId() + " references an object that was not extracted.");
                }
            }
            if (!StringUtils.hasText(binding.assetId()) && !"OMITTED".equals(binding.match())
                    && !"UNSUPPORTED".equals(binding.match())) {
                findings.add("A visual binding without a catalog asset must be marked UNSUPPORTED.");
            }
            if (routing.assets() != null && StringUtils.hasText(binding.assetId())
                    && routing.assets().candidates().stream().noneMatch(item -> item.assetId().equals(binding.assetId()))) {
                findings.add("Visual binding asset is not one of the JEV-routed catalog candidates: " + binding.assetId());
            }
        }
        if (visualRoutingSupplied && !expectedTargets.equals(actualTargets)) {
            findings.add("Visual bindings must cover the selected schema renderer targets exactly.");
        }
        return new Verification(findings.stream().distinct().limit(8).toList());
    }

    /** Re-checks a pinned document after teacher answers without re-routing it. */
    public Verification verifyPinned(SpecificationDocument document) {
        if (document == null || !StringUtils.hasText(document.schemaId())
                || !StringUtils.hasText(document.schemaVersion())) {
            return new Verification(List.of("The confirmed specification is missing its pinned schema."));
        }
        SchemaVersion schema = schemas.requireCurrentApproved(document.schemaId(), document.schemaVersion());
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
                schema.getVersion(), schema.getTopic(), schema.getName(), schema.getDefinition());
        double confidence = clamp(probability);
        List<String> signals = new ArrayList<>(List.of("JEV_CHOICE", "APPROVED_DATABASE_CONTRACT"));
        return new SchemaCandidate(contract, signals, confidence);
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
                        .map(item -> item.path("type").asText())
                        .filter(StringUtils::hasText).sorted().limit(16).toList().toString();
            }
        }
        return "%s | topic=%s | model=%s | required quantities=%s | entity types=%s".formatted(
                schema.getName(), schema.getTopic(), definition == null ? "" : definition.path("model").asText(schema.getSchemaId()),
                quantities, entities);
    }

    private double clamp(double value) {
        if (!Double.isFinite(value)) return 0;
        return Math.max(0, Math.min(1, value));
    }
}
