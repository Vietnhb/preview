package com.example.backend.ai.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.ai.extraction.prompt.CandidateContractProjection.EntityTypeProjection;
import com.example.backend.ai.extraction.prompt.CandidateContractProjection.QuantityProjection;
import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.schema.routing.model.SchemaCandidate;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates the provider JSON shape before Jackson binds it to domain records. */
public final class StrictSpecificationValidator {
    private static final Set<String> ROOT_FIELDS = Set.of("contractVersion", "schemaVersion", "topic", "schemaId", "objects",
            "quantities", "relations", "endCondition", "confidence", "ambiguities", "visualBindings",
            "resolutionDecisions");
    private static final Set<String> OBJECT_FIELDS = Set.of("id", "label", "type", "quantities");
    private static final Set<String> QUANTITY_FIELDS = Set.of("name", "symbol", "value", "originalUnit",
            "confidence", "sourceText");
    private static final Set<String> RELATION_FIELDS = Set.of("type", "subject", "object", "value", "unit", "sourceText");
    private static final Set<String> AMBIGUITY_FIELDS = Set.of("code", "fieldPath", "question", "options");
    private static final Set<String> END_CONDITION_FIELDS = Set.of("type", "duration", "maxTime", "quantity",
            "operator", "value", "count", "event");
    private static final Set<String> EVENT_FIELDS = Set.of("type", "entities", "quantity", "operator", "value",
            "firstQuantity", "secondQuantity", "markerQuantity");
    private static final int MAX_ITEMS = 512;
    private static final int MAX_TEXT = 2_000;

    private StrictSpecificationValidator() {
    }

    public static void validate(JsonNode root) {
        if (root == null || !root.isObject()) fail("root must be an object");
        rejectUnknown(root, ROOT_FIELDS, "root");
        requiredText(root, "contractVersion");
        if (!"1.0".equals(root.path("contractVersion").asText())) fail("contractVersion must be 1.0");
        requiredText(root, "schemaVersion");
        requiredText(root, "schemaId");
        optionalTextOrNull(root, "topic");
        array(root, "objects");
        array(root, "quantities");
        array(root, "relations");
        array(root, "ambiguities");
        if (!root.path("confidence").isNumber() || !finite(root.path("confidence"))) {
            fail("confidence must be a finite JSON number");
        }
        validateObjects(root.path("objects"));
        validateQuantities(root.path("quantities"));
        validateRelations(root.path("relations"));
        validateAmbiguities(root.path("ambiguities"));
        validateResolutionDecisions(root.get("resolutionDecisions"));
        if (root.has("visualBindings")) {
            array(root, "visualBindings");
            Set<String> targets = new HashSet<>();
            Set<String> objectIds = new HashSet<>();
            root.path("objects").forEach(object -> objectIds.add(object.path("id").asText()));
            for (JsonNode binding : root.path("visualBindings")) {
                rejectUnknown(binding, Set.of("targetId", "entityId", "assetId", "match", "visualDifference"), "visual binding");
                requiredText(binding, "targetId");
                if (!targets.add(binding.path("targetId").asText())) fail("duplicate visual target");
                optionalTextOrNull(binding, "entityId");
                optionalTextOrNull(binding, "assetId");
                optionalTextOrNull(binding, "visualDifference");
                String match = binding.path("match").asText();
                if (!Set.of("EXACT", "SUBSTITUTE", "UNSUPPORTED", "OMITTED").contains(match)) {
                    fail("invalid visual match classification");
                }
                boolean explanationRequired = "SUBSTITUTE".equals(match) || "UNSUPPORTED".equals(match);
                if (explanationRequired && (!binding.path("visualDifference").isTextual()
                        || binding.path("visualDifference").asText().isBlank())) {
                    fail("A substitute or unsupported visual binding must explain its visual limitation.");
                }
                if (!"OMITTED".equals(match)) {
                    String entityId = binding.path("entityId").asText("");
                    if (entityId.isBlank()) {
                        fail("Every non-omitted visual target must reference a physical object via entityId");
                    }
                    if (!objectIds.contains(entityId)) {
                        fail("Visual binding entityId must exactly match an objects[].id in the same response");
                    }
                }
            }
        }
        if (!root.path("endCondition").isObject()) fail("endCondition must be an object");
        validateEndCondition(root.path("endCondition"));
    }

    /** Verify catalog-version membership and quantity vocabulary before Jackson binding. */
    public static SchemaCandidate validateCandidateMembership(JsonNode root, SchemaRoutingDecision decision) {
        return validateCandidateMembership(root, decision, null);
    }

    /**
     * Verify candidate membership and, when a unit catalog is supplied, reject
     * units before the JSON tree is bound to extraction POJOs.  The two-argument
     * overload remains structural for callers that do not own the application
     * unit catalog; production provider paths must use this overload.
     */
    public static SchemaCandidate validateCandidateMembership(JsonNode root, SchemaRoutingDecision decision,
            UnitNormalizer unitNormalizer) {
        if (root == null || !root.isObject() || decision == null) fail("candidate routing decision is required");
        String schemaId = requiredTextValue(root, "schemaId");
        String schemaVersion = requiredTextValue(root, "schemaVersion");
        SchemaCandidate candidate = decision.candidates().stream()
                .filter(item -> item.schemaId().equals(schemaId) && item.schemaVersion().equals(schemaVersion))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Invalid AI specification: schemaId/schemaVersion is outside the routed candidate set"));
        if (decision.status() == SchemaRoutingDecision.Status.SELECTED) {
            SchemaCandidate selected = decision.selectedCandidate().orElseThrow(() ->
                    new IllegalArgumentException("Invalid AI specification: selected route has no pinned candidate"));
            if (!selected.schemaId().equals(candidate.schemaId())
                    || !selected.schemaVersion().equals(candidate.schemaVersion())) {
                fail("schemaId/schemaVersion does not match the backend-selected candidate");
            }
        }
        Set<String> candidateLabels = decision.candidates().stream()
                .map(item -> item.schemaId() + "@" + item.schemaVersion()).collect(Collectors.toUnmodifiableSet());
        Set<String> candidateIds = decision.candidates().stream().map(SchemaCandidate::schemaId)
                .collect(Collectors.toUnmodifiableSet());
        CandidateContractProjection contract = candidate.contract();
        JsonNode topic = root.get("topic");
        if (topic != null && !topic.isNull() && !contract.topic().equals(topic.asText())) {
            fail("topic does not match the selected candidate");
        }
        Set<String> quantityNames = new HashSet<>();
        for (QuantityProjection quantity : contract.requiredQuantities()) addAcceptedNames(quantity, quantityNames);
        for (QuantityProjection quantity : contract.optionalQuantities()) addAcceptedNames(quantity, quantityNames);
        Set<String> canonicalQuantityNames = new HashSet<>();
        for (JsonNode quantity : root.path("quantities")) {
            String name = quantity.path("name").asText("");
            if (!quantityNames.contains(name)) fail("quantity name is outside the selected candidate contract");
            String canonicalName = canonicalQuantityName(contract, name);
            if (canonicalName == null) fail("quantity name is ambiguous in the selected candidate contract");
            if (!canonicalQuantityNames.add(canonicalName)) {
                fail("duplicate quantity resolves to the same canonical schema key");
            }
            if (unitNormalizer != null) validateQuantityUnit(quantity, canonicalName,
                    "quantities." + canonicalName, contract.requiredQuantities(), contract.optionalQuantities(),
                    unitNormalizer);
        }
        Map<String, EntityTypeProjection> entityById = new HashMap<>();
        Map<String, Set<String>> entityQuantityNames = new HashMap<>();
        Map<String, Integer> entityCounts = new HashMap<>();
        if (!contract.entityTypes().isEmpty()) {
            for (JsonNode object : root.path("objects")) {
                String id = object.path("id").asText("");
                EntityTypeProjection entity = contract.entityTypes().stream()
                        .filter(item -> item.type().equals(object.path("type").asText(""))).findFirst().orElse(null);
                if (entity == null) fail("object type is outside the selected entity contract");
                entityById.put(id, entity);
                entityCounts.merge(entity.type(), 1, Integer::sum);
                Set<String> accepted = new HashSet<>();
                entity.requiredQuantities().forEach(item -> addAcceptedNames(item, accepted));
                entity.optionalQuantities().forEach(item -> addAcceptedNames(item, accepted));
                Set<String> supplied = new HashSet<>();
                for (JsonNode quantity : object.path("quantities")) {
                    String name = quantity.path("name").asText("");
                    if (!accepted.contains(name)) fail("entity quantity name is outside the selected candidate contract");
                    String canonical = canonicalQuantityName(entity.requiredQuantities(), entity.optionalQuantities(), name);
                    if (canonical == null || !supplied.add(canonical)) {
                        fail("duplicate entity quantity resolves to the same canonical schema key");
                    }
                    if (unitNormalizer != null) {
                        validateQuantityUnit(quantity, canonical,
                                "objects." + id + ".quantities." + canonical,
                                entity.requiredQuantities(), entity.optionalQuantities(), unitNormalizer);
                    }
                }
                entityQuantityNames.put(id, supplied);
            }
            for (EntityTypeProjection entity : contract.entityTypes()) {
                int count = entityCounts.getOrDefault(entity.type(), 0);
                if (count < entity.minCount() || count > entity.maxCount()) {
                    fail("entity count is outside the selected candidate contract for type " + entity.type());
                }
            }
        } else {
            for (JsonNode object : root.path("objects")) {
                if (object.has("quantities") && object.path("quantities").size() > 0) {
                    fail("entity-local quantities require an entity contract");
                }
            }
        }
        for (JsonNode relation : root.path("relations")) {
            String type = relation.path("type").asText("");
            if (!contract.relationTypes().contains(type)) {
                fail("relation type is outside the selected candidate contract");
            }
        }
        List<String> capabilities = contract.endConditionCapabilities();
        if (!capabilities.isEmpty()) {
            String endConditionType = root.path("endCondition").path("type").asText("").trim()
                    .toLowerCase(java.util.Locale.ROOT);
            boolean supported = capabilities.stream().map(value -> value.trim().toLowerCase(java.util.Locale.ROOT))
                    .anyMatch(endConditionType::equals);
            if (!supported) fail("endCondition type is outside the selected candidate capabilities");
        }
        Set<String> ambiguityQuantityNames = new HashSet<>();
        Map<String, Set<String>> entityAmbiguities = new HashMap<>();
        for (JsonNode ambiguity : root.path("ambiguities")) {
            String fieldPath = ambiguity.path("fieldPath").asText("");
            if (fieldPath.startsWith("quantities.")) {
                String key = fieldPath.substring("quantities.".length());
                boolean required = contract.requiredQuantities().stream().anyMatch(item -> item.key().equals(key));
                boolean optional = contract.optionalQuantities().stream().anyMatch(item -> item.key().equals(key));
                if (!required && !optional) fail("quantity ambiguity fieldPath is outside the selected candidate contract");
                if (optional) fail("ambiguity must not request an optional quantity");
                ambiguityQuantityNames.add(key);
            }
            int entityMarker = fieldPath.indexOf(".quantities.");
            if (fieldPath.startsWith("objects.") && entityMarker > "objects.".length()) {
                String objectId = fieldPath.substring("objects.".length(), entityMarker);
                String key = fieldPath.substring(entityMarker + ".quantities.".length());
                EntityTypeProjection entity = entityById.get(objectId);
                if (entity == null) fail("entity quantity ambiguity references an unknown object");
                boolean required = entity.requiredQuantities().stream().anyMatch(item -> item.key().equals(key));
                boolean optional = entity.optionalQuantities().stream().anyMatch(item -> item.key().equals(key));
                if (!required && !optional) fail("entity quantity ambiguity is outside the selected candidate contract");
                if (optional) fail("ambiguity must not request an optional entity quantity");
                entityAmbiguities.computeIfAbsent(objectId, ignored -> new HashSet<>()).add(key);
            }
            if ("schemaId".equals(fieldPath) || "schema.schemaId".equals(fieldPath)) {
                List<String> options = new java.util.ArrayList<>();
                ambiguity.path("options").forEach(option -> options.add(option.asText()));
                if (options.isEmpty() || options.stream().anyMatch(option ->
                        !candidateLabels.contains(option) && !candidateIds.contains(option))) {
                    fail("schema ambiguity options are outside the routed candidate set");
                }
            }
        }
        for (QuantityProjection required : contract.requiredQuantities()) {
            boolean supplied = canonicalQuantityNames.contains(required.key());
            boolean questioned = ambiguityQuantityNames.contains(required.key());
            if (supplied && questioned) fail("required quantity cannot be both supplied and ambiguous: " + required.key());
            if (!supplied && !questioned) {
                fail("missing required quantity or ambiguity: " + required.key());
            }
        }
        for (Map.Entry<String, EntityTypeProjection> entry : entityById.entrySet()) {
            Set<String> supplied = entityQuantityNames.getOrDefault(entry.getKey(), Set.of());
            Set<String> questioned = entityAmbiguities.getOrDefault(entry.getKey(), Set.of());
            for (QuantityProjection required : entry.getValue().requiredQuantities()) {
                boolean hasValue = supplied.contains(required.key());
                boolean hasQuestion = questioned.contains(required.key());
                if (hasValue && hasQuestion) fail("entity quantity cannot be both supplied and ambiguous: " + required.key());
                if (!hasValue && !hasQuestion) {
                    fail("missing required entity quantity or ambiguity: objects." + entry.getKey()
                            + ".quantities." + required.key());
                }
            }
        }
        return candidate;
    }

    private static void validateQuantityUnit(JsonNode quantity, String canonicalName, String fieldPath,
            List<QuantityProjection> required, List<QuantityProjection> optional, UnitNormalizer unitNormalizer) {
        JsonNode originalUnit = quantity.get("originalUnit");
        String suppliedUnit = originalUnit != null && originalUnit.isTextual() ? originalUnit.asText() : "";
        BigDecimal value = quantity.path("value").isNumber() ? quantity.path("value").decimalValue() : null;
        QuantityProjection projection = quantityProjection(required, optional, canonicalName);
        List<String> expectedUnits = projection == null ? List.of() : projection.acceptedInputUnits();
        if (originalUnit == null || !originalUnit.isTextual() || originalUnit.asText().isBlank()) {
            throw new com.example.backend.ai.extraction.model.QuantityContractViolation(
                    fieldPath, value, suppliedUnit, expectedUnits, "A quantity input requires a unit.");
        }
        UnitNormalizer.NormalizedQuantity normalized = unitNormalizer.normalize(BigDecimal.ONE,
                originalUnit.asText());
        if (!normalized.knownUnit()) {
            throw new com.example.backend.ai.extraction.model.QuantityContractViolation(
                    fieldPath, value, suppliedUnit, expectedUnits, "The supplied unit is not recognized.");
        }
        if (projection == null || projection.acceptedInputUnits().stream()
                .map(allowed -> unitNormalizer.normalize(BigDecimal.ONE, allowed))
                .filter(UnitNormalizer.NormalizedQuantity::knownUnit)
                .noneMatch(allowed -> allowed.normalizedUnit().equals(normalized.normalizedUnit()))) {
            throw new com.example.backend.ai.extraction.model.QuantityContractViolation(
                    fieldPath, value, suppliedUnit, expectedUnits,
                    "The supplied unit does not match the selected schema input.");
        }
    }

    private static QuantityProjection quantityProjection(CandidateContractProjection contract, String key) {
        return quantityProjection(contract.requiredQuantities(), contract.optionalQuantities(), key);
    }

    private static QuantityProjection quantityProjection(List<QuantityProjection> required,
            List<QuantityProjection> optional, String key) {
        return java.util.stream.Stream.concat(required.stream(), optional.stream())
                .filter(item -> item.key().equals(key)).findFirst().orElse(null);
    }

    private static void addAcceptedNames(QuantityProjection quantity, Set<String> accepted) {
        accepted.add(quantity.key());
        accepted.addAll(quantity.aliases());
        accepted.addAll(quantity.symbols());
    }

    private static String canonicalQuantityName(CandidateContractProjection contract, String suppliedName) {
        return canonicalQuantityName(contract.requiredQuantities(), contract.optionalQuantities(), suppliedName);
    }

    private static String canonicalQuantityName(List<QuantityProjection> required,
            List<QuantityProjection> optional, String suppliedName) {
        Set<String> matches = new HashSet<>();
        for (QuantityProjection quantity : required) {
            if (accepts(quantity, suppliedName)) matches.add(quantity.key());
        }
        for (QuantityProjection quantity : optional) {
            if (accepts(quantity, suppliedName)) matches.add(quantity.key());
        }
        return matches.size() == 1 ? matches.iterator().next() : null;
    }

    private static boolean accepts(QuantityProjection quantity, String suppliedName) {
        return quantity.key().equals(suppliedName) || quantity.aliases().contains(suppliedName)
                || quantity.symbols().contains(suppliedName);
    }

    private static String requiredTextValue(JsonNode root, String key) {
        JsonNode value = root.get(key);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > MAX_TEXT) {
            fail(key + " must be a non-empty text value");
        }
        return value.asText();
    }

    private static void validateObjects(JsonNode values) {
        limit(values, "objects");
        Set<String> ids = new HashSet<>();
        for (JsonNode item : values) {
            object(item, "object");
            rejectUnknown(item, OBJECT_FIELDS, "object");
            requiredText(item, "id"); requiredText(item, "label"); requiredText(item, "type");
            if (!ids.add(item.path("id").asText())) fail("duplicate object id");
            JsonNode quantities = item.get("quantities");
            if (quantities != null && !quantities.isNull()) {
                if (!quantities.isArray()) fail("object.quantities must be an array");
                validateQuantities(quantities);
            }
        }
    }

    private static void validateQuantities(JsonNode values) {
        limit(values, "quantities");
        Set<String> names = new HashSet<>();
        for (JsonNode item : values) {
            object(item, "quantity");
            rejectUnknown(item, QUANTITY_FIELDS, "quantity");
            requiredText(item, "name");
            if (!names.add(item.path("name").asText())) fail("duplicate quantity name");
            requiredNumber(item, "value");
            requiredText(item, "originalUnit");
            optionalNumber(item, "confidence");
            optionalText(item, "symbol"); optionalText(item, "sourceText");
        }
    }

    private static void validateRelations(JsonNode values) {
        limit(values, "relations");
        for (JsonNode item : values) {
            object(item, "relation");
            rejectUnknown(item, RELATION_FIELDS, "relation");
            requiredText(item, "type"); requiredText(item, "subject");
            optionalText(item, "object"); optionalText(item, "unit"); optionalText(item, "sourceText");
            JsonNode value = item.get("value");
            if (value != null && !value.isNull() && !value.isNumber() && !value.isTextual() && !value.isBoolean()) {
                fail("relation.value must be number, string, boolean or null");
            }
            if (value != null && value.isTextual() && value.asText().length() > MAX_TEXT) {
                fail("relation.value exceeds the text limit");
            }
        }
    }

    private static void validateAmbiguities(JsonNode values) {
        limit(values, "ambiguities");
        Set<String> codes = new HashSet<>();
        Set<String> paths = new HashSet<>();
        for (JsonNode item : values) {
            object(item, "ambiguity");
            rejectUnknown(item, AMBIGUITY_FIELDS, "ambiguity");
            requiredText(item, "code"); requiredText(item, "fieldPath"); requiredText(item, "question");
            if (!codes.add(item.path("code").asText())) fail("duplicate ambiguity code");
            if (!paths.add(item.path("fieldPath").asText())) fail("duplicate ambiguity fieldPath");
            JsonNode options = item.get("options");
            if (options == null || !options.isArray() || options.size() > MAX_ITEMS) fail("ambiguity.options must be an array");
            for (JsonNode option : options) if (!option.isTextual() || option.asText().length() > MAX_TEXT) fail("ambiguity option must be text");
        }
    }

    private static void validateResolutionDecisions(JsonNode values) {
        if (values == null) return;
        if (!values.isArray() || values.size() > MAX_ITEMS) fail("resolutionDecisions must be a bounded array");
        Set<String> codes = new HashSet<>();
        Set<String> outcomes = Set.of("ANSWERED", "ACCEPT_SIMPLIFICATION", "DECLINE_SIMPLIFICATION",
                "REVISE_REQUEST", "START_NEW_PROBLEM", "UNRESOLVED");
        for (JsonNode decision : values) {
            object(decision, "resolution decision");
            rejectUnknown(decision, Set.of("code", "outcome", "omittedObjectIds"), "resolution decision");
            requiredText(decision, "code");
            requiredText(decision, "outcome");
            if (!codes.add(decision.path("code").asText()) || !outcomes.contains(decision.path("outcome").asText())) {
                fail("resolution decision code must be unique and outcome must be supported");
            }
            JsonNode omittedIds = decision.get("omittedObjectIds");
            if (omittedIds == null || !omittedIds.isArray()) fail("resolution decision omittedObjectIds must be an array");
            Set<String> ids = new HashSet<>();
            for (JsonNode id : omittedIds) {
                if (!id.isTextual() || id.asText().isBlank() || !ids.add(id.asText())) {
                    fail("resolution decision object IDs must be unique non-empty text");
                }
            }
        }
    }

    private static void validateEndCondition(JsonNode condition) {
        rejectUnknown(condition, END_CONDITION_FIELDS, "endCondition");
        requiredText(condition, "type");
        for (String key : Set.of("duration", "maxTime", "value", "count")) {
            JsonNode value = condition.get(key);
            if (value != null && !value.isNull() && (!value.isNumber() || !finite(value))) {
                fail("endCondition." + key + " must be a finite JSON number");
            }
        }
        JsonNode event = condition.get("event");
        if (event != null && !event.isNull()) {
            object(event, "endCondition.event");
            rejectUnknown(event, EVENT_FIELDS, "endCondition.event");
            requiredText(event, "type");
            JsonNode entities = event.get("entities");
            if (entities != null && !entities.isNull() && (!entities.isArray() || entities.size() > MAX_ITEMS)) {
                fail("endCondition.event.entities must be an array");
            }
            for (String key : Set.of("quantity", "firstQuantity", "secondQuantity", "markerQuantity", "operator")) {
                optionalText(event, key);
            }
        }
    }

    private static void rejectUnknown(JsonNode object, Set<String> allowed, String label) {
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            if (!allowed.contains(field)) fail("Unknown field '" + field + "' in " + label);
        }
    }

    private static void requiredText(JsonNode object, String key) {
        JsonNode value = object.get(key);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > MAX_TEXT) {
            fail(key + " must be a non-empty text value");
        }
    }

    private static void optionalText(JsonNode object, String key) {
        JsonNode value = object.get(key);
        if (value != null && !value.isNull() && (!value.isTextual() || value.asText().length() > MAX_TEXT)) fail(key + " must be text or null");
    }

    private static void optionalTextOrNull(JsonNode object, String key) {
        JsonNode value = object.get(key);
        if (value != null && !value.isNull() && (!value.isTextual() || value.asText().length() > MAX_TEXT)) {
            fail(key + " must be text or null");
        }
    }

    private static void requiredNumber(JsonNode object, String key) {
        JsonNode value = object.get(key);
        if (value == null || !value.isNumber() || !finite(value)) fail(key + " must be a finite JSON number");
    }

    private static void optionalNumber(JsonNode object, String key) {
        JsonNode value = object.get(key);
        if (value != null && !value.isNull() && (!value.isNumber() || !finite(value))) fail(key + " must be a finite JSON number or null");
    }

    private static void array(JsonNode object, String key) {
        if (!object.path(key).isArray()) fail(key + " must be an array");
    }

    private static void object(JsonNode value, String label) {
        if (value == null || !value.isObject()) fail(label + " must be an object");
    }

    private static void limit(JsonNode value, String label) {
        if (value.size() > MAX_ITEMS) fail(label + " exceeds the item limit");
    }

    private static boolean finite(JsonNode value) {
        return Double.isFinite(value.asDouble());
    }

    private static void fail(String message) {
        throw new IllegalArgumentException("Invalid AI specification: " + message);
    }
}
