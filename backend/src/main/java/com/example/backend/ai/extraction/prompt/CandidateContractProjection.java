package com.example.backend.ai.extraction.prompt;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Small extraction-facing view of a catalog schema. It deliberately copies only
 * fields needed to identify a candidate and extract quantities/relations.
 */
public record CandidateContractProjection(
        String schemaId,
        String schemaVersion,
        String topic,
        String name,
        String modelId,
        List<QuantityProjection> requiredQuantities,
        List<QuantityProjection> optionalQuantities,
        List<String> relationTypes,
        List<String> endConditionCapabilities,
        List<EntityTypeProjection> entityTypes,
        BigDecimal executionDurationSeconds,
        JsonNode capabilities) {

    private static final Set<String> CONSTRAINT_FIELDS = Set.of(
            "positive", "nonNegative", "integer", "sameUnitAs",
            "min", "max", "minimum", "maximum", "minInclusive", "maxInclusive");
    private static final String REQUIRED_QUANTITIES = "requiredQuantities";
    private static final String OPTIONAL_QUANTITIES = "optionalQuantities";
    private static final String RELATION_TYPES = "relationTypes";
    private static final String END_CONDITION_CAPABILITIES = "endConditionCapabilities";
    private static final String END_CONDITION = "endCondition";
    private static final String EXECUTION = "execution";
    private static final String DURATION_SECONDS = "durationSeconds";
    private static final String TYPES = "types";

    public CandidateContractProjection {
        schemaId = required(schemaId, "schemaId");
        schemaVersion = required(schemaVersion, "schemaVersion");
        topic = required(topic, "topic");
        name = required(name, "name");
        modelId = required(modelId, "modelId");
        requiredQuantities = List.copyOf(Objects.requireNonNull(requiredQuantities, REQUIRED_QUANTITIES));
        optionalQuantities = List.copyOf(Objects.requireNonNull(optionalQuantities, OPTIONAL_QUANTITIES));
        relationTypes = List.copyOf(Objects.requireNonNull(relationTypes, RELATION_TYPES));
        endConditionCapabilities = List.copyOf(Objects.requireNonNull(endConditionCapabilities,
                END_CONDITION_CAPABILITIES));
        entityTypes = List.copyOf(Objects.requireNonNull(entityTypes, "entityTypes"));
        Set<String> entityNames = new LinkedHashSet<>();
        for (EntityTypeProjection entity : entityTypes) {
            if (!entityNames.add(entity.type())) throw new IllegalArgumentException("Duplicate entity type.");
        }
    }

    /**
     * Build a projection using catalog identity supplied by the version record,
     * not identity guessed from the definition JSON.
     */
    public static CandidateContractProjection from(String schemaId, String schemaVersion, String topic,
            String name, JsonNode definition) {
        if (definition == null || !definition.isObject()) {
            throw new IllegalArgumentException("Candidate schema definition must be an object.");
        }
        String modelId = text(definition.get("model"), "model");
        Map<String, List<String>> symbolsByKey = adjustableSymbols(definition.path("adjustableParameters"));
        List<QuantityProjection> required = quantities(definition.get(REQUIRED_QUANTITIES),
                REQUIRED_QUANTITIES, symbolsByKey);
        List<QuantityProjection> optional = quantities(definition.get(OPTIONAL_QUANTITIES),
                OPTIONAL_QUANTITIES, symbolsByKey);
        List<EntityTypeProjection> entities = entityTypes(definition, symbolsByKey);
        return new CandidateContractProjection(schemaId, schemaVersion, topic, name, modelId, required, optional,
                declaredRelationTypes(definition), declaredEndConditionCapabilities(definition),
                entities,
                duration(definition),
                capabilityContext(definition));
    }

    private static JsonNode capabilityContext(JsonNode definition) {
        // Keep the pack's full data contract. New pack fields must reach the LLM
        // without requiring an Understanding Engine code change.
        return definition.deepCopy();
    }

    private static BigDecimal duration(JsonNode definition) {
        JsonNode duration = definition.path(EXECUTION).path(DURATION_SECONDS);
        return duration.isNumber() ? duration.decimalValue() : null;
    }

    private static List<EntityTypeProjection> entityTypes(JsonNode definition,
            Map<String, List<String>> symbolsByKey) {
        JsonNode nodes = entityTypeNodes(definition);
        if (nodes.isMissingNode() || nodes.isNull()) return List.of();
        if (!nodes.isArray()) throw new IllegalArgumentException("Candidate entityContract.types must be an array.");
        List<EntityTypeProjection> result = new ArrayList<>();
        boolean conceptual = "2.0".equals(definition.path("metaSchemaVersion").asText());
        for (JsonNode node : nodes) {
            result.add(entityType(node, conceptual, symbolsByKey));
        }
        return List.copyOf(result);
    }

    private static JsonNode entityTypeNodes(JsonNode definition) {
        JsonNode nodes = definition.path("objectTypes");
        if (nodes.isArray()) return nodes;
        nodes = definition.path("entityContract").path(TYPES);
        return nodes.isArray() ? nodes : definition.path("entityTypes");
    }

    private static EntityTypeProjection entityType(JsonNode node, boolean conceptual,
            Map<String, List<String>> symbolsByKey) {
        if (!node.isObject()) throw new IllegalArgumentException("Candidate entity type must be an object.");
        String type = text(node.get("type"), "entity type");
        int min = integer(first(node, "min", "minCount"), conceptual ? 0 : 1, "entity min");
        int max = integer(first(node, "max", "maxCount"), conceptual ? 512 : min, "entity max");
        if (min < 0 || max < min || max > 512) {
            throw new IllegalArgumentException("Entity count bounds are invalid.");
        }
        List<QuantityProjection> required = quantities(node.get(REQUIRED_QUANTITIES),
                "entity " + REQUIRED_QUANTITIES, symbolsByKey);
        List<QuantityProjection> optional = quantities(node.get(OPTIONAL_QUANTITIES),
                "entity " + OPTIONAL_QUANTITIES, symbolsByKey);
        return new EntityTypeProjection(type, min, max, required, optional);
    }

    private static JsonNode first(JsonNode node, String primary, String fallback) {
        return node.has(primary) ? node.get(primary) : node.get(fallback);
    }

    private static int integer(JsonNode node, int defaultValue, String label) {
        if (node == null || node.isMissingNode() || node.isNull()) return defaultValue;
        if (!node.isIntegralNumber()) throw new IllegalArgumentException(label + " must be an integer.");
        return node.asInt();
    }

    private static List<QuantityProjection> quantities(JsonNode nodes, String field,
            Map<String, List<String>> symbolsByKey) {
        if (nodes == null || nodes.isMissingNode() || nodes.isNull()) return List.of();
        if (!nodes.isArray()) throw new IllegalArgumentException("Candidate " + field + " must be an array.");
        List<QuantityProjection> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            if (!node.isObject()) throw new IllegalArgumentException("Candidate quantity must be an object.");
            String key = text(node.get("key"), "quantity key");
            List<String> aliases = stringArray(node.get("aliases"), "quantity aliases");
            List<String> units = stringArray(node.get("allowedUnits"), "quantity allowedUnits");
            if (units.isEmpty()) throw new IllegalArgumentException("Candidate quantity must declare allowedUnits.");

            LinkedHashSet<String> symbols = new LinkedHashSet<>();
            addText(symbols, node.get("symbol"));
            addTextArray(symbols, node.get("symbols"));
            symbols.addAll(symbolsByKey.getOrDefault(key, List.of()));

            JsonNode defaultNode = node.get("defaultValue");
            BigDecimal defaultValue = null;
            if (defaultNode != null && !defaultNode.isNull()) {
                if (!defaultNode.isNumber()) {
                    throw new IllegalArgumentException("Candidate quantity defaultValue must be numeric.");
                }
                defaultValue = defaultNode.decimalValue();
            }
            result.add(new QuantityProjection(key, aliases, List.copyOf(symbols), units,
                    constraints(node), defaultValue));
        }
        return List.copyOf(result);
    }

    private static Map<String, List<String>> adjustableSymbols(JsonNode parameters) {
        if (parameters == null || parameters.isMissingNode() || parameters.isNull()) return Map.of();
        if (!parameters.isArray()) throw new IllegalArgumentException("Candidate adjustableParameters must be an array.");
        Map<String, List<String>> result = new TreeMap<>();
        for (JsonNode parameter : parameters) {
            addAdjustableSymbol(result, parameter);
        }
        return Collections.unmodifiableMap(result);
    }

    private static void addAdjustableSymbol(Map<String, List<String>> destination, JsonNode parameter) {
        if (!parameter.isObject()) return;
        JsonNode keyNode = parameter.get("key");
        JsonNode symbolNode = parameter.get("symbol");
        if (keyNode == null || !keyNode.isTextual() || symbolNode == null || !symbolNode.isTextual()) return;
        String key = keyNode.asText().trim();
        String symbol = symbolNode.asText().trim();
        if (key.isEmpty() || symbol.isEmpty()) return;
        List<String> values = new ArrayList<>(destination.getOrDefault(key, List.of()));
        if (!values.contains(symbol)) values.add(symbol);
        destination.put(key, List.copyOf(values));
    }

    private static Map<String, Object> constraints(JsonNode quantity) {
        Map<String, Object> result = new TreeMap<>();
        for (String field : CONSTRAINT_FIELDS) {
            copyConstraint(quantity.get(field), field, result);
        }
        JsonNode nested = quantity.get("constraints");
        if (nested != null && !nested.isNull()) {
            if (!nested.isObject()) throw new IllegalArgumentException("Quantity constraints must be an object.");
            nested.fields().forEachRemaining(entry -> {
                if (CONSTRAINT_FIELDS.contains(entry.getKey())) copyConstraint(entry.getValue(), entry.getKey(), result);
            });
        }
        return Collections.unmodifiableMap(result);
    }

    private static void copyConstraint(JsonNode node, String key, Map<String, Object> destination) {
        if (node == null || node.isNull()) return;
        Object value;
        if (node.isBoolean()) value = node.booleanValue();
        else if (node.isNumber()) value = node.decimalValue();
        else if (node.isTextual()) value = node.textValue();
        else throw new IllegalArgumentException("Quantity constraint " + key + " must be scalar.");
        destination.put(key, value);
    }

    private static List<String> declaredRelationTypes(JsonNode definition) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addDeclaredStrings(result, definition.get(RELATION_TYPES));
        addRelationTypeObjects(result, definition.get(RELATION_TYPES));
        addRelationTypeFields(result, definition.path("relations"));
        addRelationTypeFields(result, definition.path("relationContract"));
        JsonNode bindings = definition.path(EXECUTION).path("durationBindings");
        if (bindings.isArray()) {
            for (JsonNode binding : bindings) addDeclaredStrings(result, binding.get(RELATION_TYPES));
        }
        return List.copyOf(result);
    }

    private static void addRelationTypeObjects(Set<String> destination, JsonNode values) {
        if (values == null || !values.isArray()) return;
        for (JsonNode value : values) {
            String type = value.path("type").asText("").trim();
            if (!type.isBlank()) destination.add(type);
        }
    }

    private static void addRelationTypeFields(Set<String> destination, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        if (node.isArray()) {
            addDeclaredStrings(destination, node);
            return;
        }
        if (node.isObject()) {
            addDeclaredStrings(destination, node.get(RELATION_TYPES));
            addDeclaredStrings(destination, node.get("allowedTypes"));
            addDeclaredStrings(destination, node.get(TYPES));
            addRelationTypeObjects(destination, node.get(TYPES));
        }
    }

    public static List<String> declaredEndConditionCapabilities(JsonNode definition) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        boolean explicitlyDeclared = definition.has(END_CONDITION_CAPABILITIES)
                || definition.path(EXECUTION).has(END_CONDITION_CAPABILITIES)
                || definition.path(END_CONDITION).has("capabilities")
                || definition.path(END_CONDITION).has("supportedTypes")
                || definition.has("endConditions");
        addEndCapabilities(result, definition.get(END_CONDITION_CAPABILITIES));
        addEndCapabilities(result, definition.path(EXECUTION).get(END_CONDITION_CAPABILITIES));
        addEndCapabilities(result, definition.path(END_CONDITION).get("capabilities"));
        addEndCapabilities(result, definition.path(END_CONDITION).get("supportedTypes"));
        JsonNode declared = definition.get("endConditions");
        if (declared != null && declared.isArray()) addEndCapabilities(result, declared);
        JsonNode duration = definition.path(EXECUTION).path(DURATION_SECONDS);
        if (!explicitlyDeclared && result.isEmpty() && duration.isNumber() && Double.isFinite(duration.asDouble())
                && duration.asDouble() > 0.0) {
            result.add("time_limit");
        }
        return List.copyOf(result);
    }

    private static void addEndCapabilities(Set<String> destination, JsonNode values) {
        if (values == null || values.isMissingNode() || values.isNull()) return;
        if (!values.isArray()) throw new IllegalArgumentException("Declared end-condition capabilities must be an array.");
        for (JsonNode value : values) {
            JsonNode capability = value.isObject() ? value.get("type") : value;
            if (capability != null && capability.isTextual() && !capability.asText().isBlank()) {
                destination.add(capability.asText().trim());
            }
        }
    }

    private static void addDeclaredStrings(Set<String> destination, JsonNode values) {
        if (values == null || values.isMissingNode() || values.isNull()) return;
        if (values.isTextual()) {
            if (!values.asText().isBlank()) destination.add(values.asText().trim());
            return;
        }
        if (!values.isArray()) return;
        for (JsonNode value : values) {
            if (value.isTextual() && !value.asText().isBlank()) destination.add(value.asText().trim());
        }
    }

    private static List<String> stringArray(JsonNode node, String label) {
        if (node == null || node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray()) throw new IllegalArgumentException(label + " must be an array.");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addTextArray(result, node);
        return List.copyOf(result);
    }

    private static void addTextArray(Set<String> destination, JsonNode values) {
        if (values == null || !values.isArray()) return;
        for (JsonNode value : values) addText(destination, value);
    }

    private static void addText(Set<String> destination, JsonNode value) {
        if (value != null && value.isTextual() && !value.asText().isBlank()) {
            destination.add(value.asText().trim());
        }
    }

    private static String text(JsonNode node, String label) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new IllegalArgumentException("Candidate " + label + " must be non-empty text.");
        }
        return node.asText().trim();
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must be non-empty.");
        return value.trim();
    }

    public record QuantityProjection(String key, List<String> aliases, List<String> symbols,
            List<String> acceptedInputUnits, Map<String, Object> constraints, BigDecimal defaultValue) {
        public QuantityProjection {
            key = required(key, "quantity key");
            aliases = List.copyOf(Objects.requireNonNull(aliases, "aliases"));
            symbols = List.copyOf(Objects.requireNonNull(symbols, "symbols"));
            acceptedInputUnits = List.copyOf(Objects.requireNonNull(acceptedInputUnits, "acceptedInputUnits"));
            constraints = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(constraints, "constraints")));
            if (acceptedInputUnits.isEmpty()) {
                throw new IllegalArgumentException("Candidate quantity must declare accepted input units.");
            }
        }
    }

    public record EntityTypeProjection(String type, int minCount, int maxCount,
            List<QuantityProjection> requiredQuantities, List<QuantityProjection> optionalQuantities) {
        public EntityTypeProjection {
            type = required(type, "entity type");
            if (minCount < 0 || maxCount < minCount || maxCount > 512) {
                throw new IllegalArgumentException("Entity count bounds are invalid.");
            }
            requiredQuantities = List.copyOf(Objects.requireNonNull(requiredQuantities, REQUIRED_QUANTITIES));
            optionalQuantities = List.copyOf(Objects.requireNonNull(optionalQuantities, OPTIONAL_QUANTITIES));
        }

        public List<QuantityProjection> allQuantities() {
            List<QuantityProjection> result = new ArrayList<>(requiredQuantities);
            result.addAll(optionalQuantities);
            return List.copyOf(result);
        }
    }
}
