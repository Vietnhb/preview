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
import com.example.backend.schema.routing.model.SchemaCandidate.VerificationEvidence;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.example.backend.schema.routing.model.SchemaSelectionScore;
import com.example.backend.service.problem.SchemaDefinitionService;

/** Routes approved physics contracts and SVG candidates together from the source text. */
@Service
public final class JevSchemaRoutingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JevSchemaRoutingService.class);

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

        Map<String, String> entityCounts = entityCountCriteria(approved);
        JevSchemaClassifier.Result result = classifier.classifySchemas(problemText, criteria, entityCounts);
        meters.counter("physlive.schema.routing.jev.requests").increment();
        List<Map.Entry<String, Double>> ranked = new ArrayList<>(result.probabilities().entrySet());
        ranked.sort(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry::getKey));
        if (!ranked.stream().anyMatch(item -> item.getKey().equals(result.choice()))) {
            ranked.add(Map.entry(result.choice(), Math.max(result.confidence(), 0d)));
        }

        List<SchemaCandidate> candidates = ranked.stream()
                .filter(item -> byIdentity.containsKey(item.getKey()))
                .limit(jev.candidateTopK())
                .map(item -> candidate(byIdentity.get(item.getKey()), item.getValue(), ranked.indexOf(item) + 1,
                        result.entityCountChoice()))
                .toList();
        if (candidates.isEmpty()) throw new SchemaRoutingException("Jev returned a schema outside the approved catalog.");

        SchemaCandidate first = candidates.stream()
                .filter(item -> item.schemaId().equals(result.choice()))
                .findFirst().orElse(candidates.getFirst());
        double second = candidates.stream().filter(item -> item != first)
                .mapToDouble(SchemaCandidate::confidence).max().orElse(0d);
        double margin = Math.max(0d, first.confidence() - second);
        SchemaVersion firstSchema = byIdentity.get(first.schemaId());
        Integer routedCount = parseEntityCount(result.entityCountChoice());
        int capacity = firstSchema == null ? 0 : visualActorCapacity(firstSchema.getDefinition());
        if (capacity > 0 && routedCount != null && routedCount > capacity) {
            meters.counter("physlive.schema.routing.jev.capacity_exceeded").increment();
            meters.counter("physlive.schema.routing.jev.ambiguous").increment();
            log.warn("JEV route exceeds visual actor capacity: schemaId={}, capacity={}, routedCount={}",
                    first.schemaId(), capacity, routedCount);
            return new SchemaRoutingDecision(SchemaRoutingDecision.Status.AMBIGUOUS,
                    "JEV_CAPACITY_EXCEEDED", candidates, clamp(first.confidence()), clamp(margin), result.assets());
        }
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
                clamp(first.confidence()), clamp(margin), result.assets());
    }

    public SchemaRoutingDecision routeAssets(String problemText, SchemaRoutingDecision schemaRoute,
            SpecificationDocument document, List<ConversationTurn> conversation,
            com.fasterxml.jackson.databind.JsonNode assetRequestSummary) {
        if (schemaRoute == null || document == null) {
            throw new IllegalArgumentException("A confirmed schema specification is required before asset routing.");
        }
        if (assetRequestSummary == null || !assetRequestSummary.path("assetRequests").isArray()) {
            throw new IllegalArgumentException("A validated LLM asset request summary is required before JEV classification.");
        }
        SchemaCandidate pinned = schemaRoute.candidates().stream()
                .filter(candidate -> candidate.schemaId().equals(document.schemaId())
                        && candidate.schemaVersion().equals(document.schemaVersion()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "The confirmed schema is outside the JEV candidate set."));
        SchemaVersion schema = schemas.requireCurrentApproved(document.schemaId(), document.schemaVersion());
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
                "JEV_ASSETS_ROUTED", List.of(pinned), schemaRoute.confidence(), schemaRoute.margin(), assets);
    }

    /**
     * Routes catalog assets only after the extracted schema has been pinned and
     * accepted. Asset selection must not trigger a second, competing schema
     * decision from the original problem text.
     */
    public SchemaRoutingDecision routeAssets(String problemText, SpecificationDocument document,
            List<ConversationTurn> conversation, com.fasterxml.jackson.databind.JsonNode assetRequestSummary) {
        if (document == null) throw new IllegalArgumentException("A confirmed specification is required.");
        SchemaVersion schema = schemas.requireCurrentApproved(document.schemaId(), document.schemaVersion());
        SchemaCandidate pinned = candidate(schema, 1, 1, null);
        SchemaRoutingDecision pinnedRoute = new SchemaRoutingDecision(SchemaRoutingDecision.Status.SELECTED,
                "PINNED_CONFIRMED_SCHEMA", List.of(pinned), 1, 1);
        return routeAssets(problemText, pinnedRoute, document, conversation, assetRequestSummary);
    }

    /**
     * Converts a route-level visual-capacity decision into a bounded finding
     * that the extraction provider can turn into a user-facing consent question.
     * Capacity is read from the approved schema definition; no schema id or
     * object count is embedded in this policy.
     */
    public List<String> capacityFindings(SchemaRoutingDecision routing) {
        if (routing == null || !"JEV_CAPACITY_EXCEEDED".equals(routing.reasonCode())
                || routing.candidates().isEmpty()) {
            return List.of();
        }
        SchemaCandidate candidate = routing.candidates().getFirst();
        SchemaVersion schema = schemas.requireCurrentApproved(candidate.schemaId(), candidate.schemaVersion());
        int actorCapacity = visualActorCapacity(schema.getDefinition());
        Integer routedCount = routedEntityCount(candidate);
        if (actorCapacity <= 0 || routedCount == null || routedCount <= actorCapacity) return List.of();
        return List.of("issue=JEV_CAPACITY_EXCEEDED; fieldPath=schemaId; routedCount=" + routedCount
                + "; actorCapacity=" + actorCapacity
                + "; guidance=Explain the concrete mismatch between the stated request and the selected scene. If a meaningful simplified representation is possible, describe what it will simplify or omit and ask for explicit consent before applying that simplification; otherwise offer revision or stopping. Preserve every object as a separate object until the teacher explicitly decides. Never ask for the known count again, merge objects, drop an object, or imply that the simplified result is exact.");
    }

    /**
     * JEV's post-extraction contract gate. It is deliberately deterministic:
     * the model may understand language, but it cannot change the approved
     * schema, object identity, renderer slots, or catalog candidates.
     */
    public Verification verify(String problemText, SchemaRoutingDecision routing, SpecificationDocument document) {
        return verify(problemText, routing, document, false);
    }

    private Verification verify(String problemText, SchemaRoutingDecision routing,
            SpecificationDocument document, boolean compatibilityResolved) {
        if (routing == null || document == null) {
            return new Verification(List.of("A routed schema and extracted specification are required."));
        }
        List<String> findings = new ArrayList<>();
        boolean candidate = routing.candidates().stream().anyMatch(item ->
                item.schemaId().equals(document.schemaId()) && item.schemaVersion().equals(document.schemaVersion()));
        if (!candidate) {
            findings.add("The extracted schema is outside the pinned JEV candidate set.");
            return new Verification(List.copyOf(findings));
        }

        Integer routedEntityCount = routedEntityCount(routing, document.schemaId(), document.schemaVersion());
        boolean routedEntityCountSatisfied = routedEntityCount != null && routedEntityCount >= 0
                && document.objects().size() == routedEntityCount;
        if (routedEntityCountSatisfied) {
            document.ambiguities().stream().filter(item -> "objects".equals(item.fieldPath())).findAny()
                    .ifPresent(ignored -> findings.add(
                            "issue=REDUNDANT_ENTITY_AMBIGUITY; fieldPath=objects; guidance=The JEV entity count is already satisfied; remove this question without replacement."));
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
        int actorTargets = 0;
        for (VisualTargets.Target target : targets) {
            if ("actor".equals(target.kind())) actorTargets++;
        }
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
        boolean visualCapacityExceeded = actorTargets > 0
                && (objectIds.size() > actorTargets
                        || (routedEntityCount != null && routedEntityCount > actorTargets));
        if (visualCapacityExceeded) {
            int knownObjects = Math.max(objectIds.size(), routedEntityCount == null ? 0 : routedEntityCount);
            findings.add("issue=VISUAL_CAPACITY_EXCEEDED; fieldPath=schemaId; knownObjects=" + knownObjects
                    + "; actorCapacity=" + actorTargets
                    + "; guidance=Explain which part of the stated request the current representation cannot preserve. If a meaningful simplified representation is possible, describe what it simplifies or omits and ask for explicit consent to continue with that simplification; otherwise offer revision or stopping. Never ask for a count already established by the source, merge objects, drop an object, or imply that the result would remain exact.");
        } else if (actorTargets > objectIds.size()) {
            findings.add("issue=EXTRACTED_ENTITY_MISSING; fieldPath=objects; actorCapacity=" + actorTargets
                    + "; extractedObjects=" + objectIds.size()
                    + "; guidance=Preserve every explicit source object and ask only for a genuinely unidentified object.");
        }
        if (!visualCapacityExceeded && routedEntityCount != null && routedEntityCount >= 0
                && document.objects().size() != routedEntityCount) {
            findings.add("issue=ENTITY_COUNT_MISMATCH; fieldPath=objects; expectedObjects=" + routedEntityCount
                    + "; extractedObjects=" + document.objects().size()
                    + "; guidance=Preserve the explicit object count already established by JEV. Do not ask the count again.");
        }
        return new Verification(findings.stream().distinct().limit(8).toList());
    }

    /** Re-checks a pinned document after teacher answers without re-routing it. */
    public Verification verifyPinned(String problemText, SpecificationDocument document) {
        return verifyPinned(problemText, document, false);
    }

    public Verification verifyPinned(String problemText, SpecificationDocument document,
            boolean compatibilityResolved) {
        if (document == null || !StringUtils.hasText(document.schemaId())
                || !StringUtils.hasText(document.schemaVersion())) {
            return new Verification(List.of("The confirmed specification is missing its pinned schema."));
        }
        SchemaVersion schema = schemas.requireCurrentApproved(document.schemaId(), document.schemaVersion());
        SchemaCandidate pinned = candidate(schema, 1, 1, null);
        return verify(problemText, new SchemaRoutingDecision(SchemaRoutingDecision.Status.SELECTED,
                "PINNED_AFTER_CONFIRMATION", List.of(pinned), 1, 1), document, compatibilityResolved);
    }

    public record Verification(List<String> findings) {
        public Verification {
            findings = List.copyOf(findings == null ? List.of() : findings);
        }

        public boolean passed() { return findings.isEmpty(); }
    }

    private SchemaCandidate candidate(SchemaVersion schema, double probability, int rank, String entityCountChoice) {
        CandidateContractProjection contract = CandidateContractProjection.from(schema.getSchemaId(),
                schema.getVersion(), schema.getTopic(), schema.getName(), schema.getDefinition());
        double confidence = clamp(probability);
        List<String> signals = new ArrayList<>(List.of("JEV_CHOICE", "APPROVED_DATABASE_CONTRACT"));
        if (StringUtils.hasText(entityCountChoice)) signals.add("JEV_ENTITY_COUNT:" + entityCountChoice);
        return new SchemaCandidate(contract,
                new SchemaSelectionScore(rank, confidence),
                new VerificationEvidence(confidence, 1, confidence, 0,
                        List.copyOf(signals)), confidence);
    }

    private Map<String, String> entityCountCriteria(List<SchemaVersion> approved) {
        java.util.SortedSet<Integer> capacities = new java.util.TreeSet<>();
        approved.stream().map(schema -> visualActorCapacity(schema.getDefinition()))
                .filter(value -> value > 0).forEach(capacities::add);
        Map<String, String> criteria = new LinkedHashMap<>();
        capacities.forEach(value -> criteria.put(Integer.toString(value), value + " distinct physical bodies"));
        if (!capacities.isEmpty()) {
            int maximum = capacities.last();
            criteria.put("MORE_THAN_" + maximum, "More than " + maximum + " distinct physical bodies");
        }
        return criteria;
    }

    private Integer routedEntityCount(SchemaRoutingDecision routing, String schemaId, String schemaVersion) {
        return routing.candidates().stream()
                .filter(item -> item.schemaId().equals(schemaId) && item.schemaVersion().equals(schemaVersion))
                .flatMap(item -> item.verificationEvidence().evidenceCodes().stream())
                .filter(signal -> signal.startsWith("JEV_ENTITY_COUNT:"))
                .map(signal -> parseEntityCount(signal.substring("JEV_ENTITY_COUNT:".length())))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private Integer routedEntityCount(SchemaCandidate candidate) {
        return candidate.verificationEvidence().evidenceCodes().stream()
                .filter(signal -> signal.startsWith("JEV_ENTITY_COUNT:"))
                .map(signal -> parseEntityCount(signal.substring("JEV_ENTITY_COUNT:".length())))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private Integer parseEntityCount(String choice) {
        if (!StringUtils.hasText(choice)) return null;
        String normalized = choice.trim();
        try {
            if (normalized.startsWith("MORE_THAN_")) {
                int lowerBound = Integer.parseInt(normalized.substring("MORE_THAN_".length()));
                return lowerBound == Integer.MAX_VALUE ? null : lowerBound + 1;
            }
            return Integer.valueOf(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
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
        return "%s | topic=%s | model=%s | required quantities=%s | entity types=%s".formatted(
                schema.getName(), schema.getTopic(), definition == null ? "" : definition.path("model").asText(schema.getSchemaId()),
                quantities, entities) + " | visual actor capacity=" + visualActorCapacity(definition);
    }

    /**
     * Exposes the renderer capacity to JEV without maintaining a schema-to-asset
     * mapping. This lets routing reject a one-actor scene for a multi-body request
     * before extraction can collapse several bodies into one label.
     */
    private int visualActorCapacity(com.fasterxml.jackson.databind.JsonNode definition) {
        if (definition == null) return 0;
        var presentation = definition.path("visualization").path("presentation");
        if (presentation.path("actors").isArray()) return presentation.path("actors").size();
        return countBodyNodes(presentation.path("sceneGraph").path("nodes"));
    }

    private int countBodyNodes(com.fasterxml.jackson.databind.JsonNode nodes) {
        if (!nodes.isArray()) return 0;
        int count = 0;
        for (var node : nodes) {
            if ("body".equals(node.path("type").asText())) count++;
            count += countBodyNodes(node.path("children"));
        }
        return count;
    }

    private double clamp(double value) {
        if (!Double.isFinite(value)) return 0;
        return Math.max(0, Math.min(1, value));
    }
}
