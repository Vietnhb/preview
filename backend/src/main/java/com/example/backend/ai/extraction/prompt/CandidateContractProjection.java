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
        BigDecimal executionDurationSeconds) {

    private static final Set<String> CONSTRAINT_FIELDS = Set.of(
            "positive", "nonNegative", "integer", "sameUnitAs",
            "min", "max", "minimum", "maximum", "minInclusive", "maxInclusive");

    public CandidateContractProjection {
        schemaId = required(schemaId, "schemaId");
        schemaVersion = required(schemaVersion, "schemaVersion");
        topic = required(topic, "topic");
        name = required(name, "name");
        modelId = required(modelId, "modelId");
        requiredQuantities = List.copyOf(Objects.requireNonNull(requiredQuantities, "requiredQuantities"));
        optionalQuantities = List.copyOf(Objects.requireNonNull(optionalQuantities, "optionalQuantities"));
        relationTypes = List.copyOf(Objects.requireNonNull(relationTypes, "relationTypes"));
        endConditionCapabilities = List.copyOf(Objects.requireNonNull(endConditionCapabilities,
                "endConditionCapabilities"));
        entityTypes = List.copyOf(Objects.requireNonNull(entityTypes, "entityTypes"));
        Set<String> entityNames = new LinkedHashSet<>();
        for (EntityTypeProjection entity : entityTypes) {
            if (!entityNames.add(entity.type())) throw new IllegalArgumentException("Duplicate entity type.");
        }
        if (requiredQuantities.isEmpty() && optionalQuantities.isEmpty() && entityTypes.isEmpty()) {
            throw new IllegalArgumentException("Candidate contract must declare at least one quantity.");
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
        List<QuantityProjection> required = quantities(definition.get("requiredQuantities"),
                "requiredQuantities", symbolsByKey);
        List<QuantityProjection> optional = quantities(definition.get("optionalQuantities"),
                "optionalQuantities", symbolsByKey);
        List<EntityTypeProjection> entities = entityTypes(definition, symbolsByKey);
        if (required.isEmpty() && optional.isEmpty() && entities.isEmpty()) {
            throw new IllegalArgumentException("Candidate contract must declare at least one quantity or entity type.");
        }
        return new CandidateContractProjection(schemaId, schemaVersion, topic, name, modelId, required, optional,
                declaredRelationTypes(definition), declaredEndConditionCapabilities(definition),
                entities,
                definition.path("execution").path("durationSeconds").isNumber()
                        ? definition.path("execution").path("durationSeconds").decimalValue() : null);
    }

    private static List<EntityTypeProjection> entityTypes(JsonNode definition,
            Map<String, List<String>> symbolsByKey) {
        JsonNode contract = definition.path("entityContract");
        JsonNode nodes = contract.path("types");
        if (!nodes.isArray()) nodes = definition.path("entityTypes");
        if (nodes.isMissingNode() || nodes.isNull()) return List.of();
        if (!nodes.isArray()) throw new IllegalArgumentException("Candidate entityContract.types must be an array.");
        List<EntityTypeProjection> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            if (!node.isObject()) throw new IllegalArgumentException("Candidate entity type must be an object.");
            String type = text(node.get("type"), "entity type");
            int min = integer(node.has("min") ? node.get("min") : node.get("minCount"), 1, "entity min");
            int max = integer(node.has("max") ? node.get("max") : node.get("maxCount"), min, "entity max");
            if (min < 0 || max < min || max > 512) throw new IllegalArgumentException("Entity count bounds are invalid.");
            List<QuantityProjection> required = quantities(node.get("requiredQuantities"),
                    "entity requiredQuantities", symbolsByKey);
            List<QuantityProjection> optional = quantities(node.get("optionalQuantities"),
                    "entity optionalQuantities", symbolsByKey);
            result.add(new EntityTypeProjection(type, min, max, required, optional));
        }
        return List.copyOf(result);
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
            if (!parameter.isObject()) continue;
            JsonNode keyNode = parameter.get("key");
            JsonNode symbolNode = parameter.get("symbol");
            if (keyNode == null || !keyNode.isTextual() || symbolNode == null || !symbolNode.isTextual()) continue;
            String key = keyNode.asText().trim();
            String symbol = symbolNode.asText().trim();
            if (!key.isEmpty() && !symbol.isEmpty()) {
                List<String> values = new ArrayList<>(result.getOrDefault(key, List.of()));
                if (!values.contains(symbol)) values.add(symbol);
                result.put(key, List.copyOf(values));
            }
        }
        return Collections.unmodifiableMap(result);
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
        addDeclaredStrings(result, definition.get("relationTypes"));
        addRelationTypeFields(result, definition.path("relations"));
        addRelationTypeFields(result, definition.path("relationContract"));
        JsonNode bindings = definition.path("execution").path("durationBindings");
        if (bindings.isArray()) {
            for (JsonNode binding : bindings) addDeclaredStrings(result, binding.get("relationTypes"));
        }
        return List.copyOf(result);
    }

    private static void addRelationTypeFields(Set<String> destination, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        if (node.isArray()) {
            addDeclaredStrings(destination, node);
            return;
        }
        if (node.isObject()) {
            addDeclaredStrings(destination, node.get("relationTypes"));
            addDeclaredStrings(destination, node.get("allowedTypes"));
            addDeclaredStrings(destination, node.get("types"));
        }
    }

    public static List<String> declaredEndConditionCapabilities(JsonNode definition) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addEndCapabilities(result, definition.get("endConditionCapabilities"));
        addEndCapabilities(result, definition.path("execution").get("endConditionCapabilities"));
        addEndCapabilities(result, definition.path("endCondition").get("capabilities"));
        addEndCapabilities(result, definition.path("endCondition").get("supportedTypes"));
        JsonNode declared = definition.get("endConditions");
        if (declared != null && declared.isArray()) addEndCapabilities(result, declared);
        JsonNode duration = definition.path("execution").path("durationSeconds");
        if (result.isEmpty() && duration.isNumber() && Double.isFinite(duration.asDouble())
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
            requiredQuantities = List.copyOf(Objects.requireNonNull(requiredQuantities, "requiredQuantities"));
            optionalQuantities = List.copyOf(Objects.requireNonNull(optionalQuantities, "optionalQuantities"));
        }

        public List<QuantityProjection> allQuantities() {
            List<QuantityProjection> result = new ArrayList<>(requiredQuantities);
            result.addAll(optionalQuantities);
            return List.copyOf(result);
        }
    }
}
