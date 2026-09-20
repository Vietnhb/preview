package com.example.backend.service.problem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compiles schema JSON once into immutable lookup structures. */
public final class SchemaCompiler {
    private final ObjectMapper objectMapper;

    public SchemaCompiler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompiledSchema compile(JsonNode definition, String schemaId) {
        if (definition == null || !definition.isObject()) throw invalid(schemaId, "definition must be an object");
        Map<String, CompiledSchema.QuantityDefinition> quantities = new LinkedHashMap<>();
        Map<String, String> aliases = new LinkedHashMap<>();
        compileQuantities(definition.path("requiredQuantities"), quantities, aliases, schemaId);
        compileQuantities(definition.path("optionalQuantities"), quantities, aliases, schemaId);
        Map<String, CompiledSchema.AdjustmentDefinition> adjustments = new LinkedHashMap<>();
        for (JsonNode node : definition.path("adjustableParameters")) {
            String key = node.path("key").asText("").trim();
            double min = node.path("min").asDouble(Double.NaN);
            double max = node.path("max").asDouble(Double.NaN);
            double step = node.path("step").asDouble(Double.NaN);
            if (key.isBlank() || !quantities.containsKey(key) || !finite(min) || !finite(max)
                    || !finite(step) || min > max || step <= 0 || adjustments.putIfAbsent(key,
                    new CompiledSchema.AdjustmentDefinition(key, min, max, step)) != null) {
                throw invalid(schemaId, "invalid adjustable parameter " + key);
            }
        }
        JsonNode execution = definition.path("execution");
        JsonNode validation = definition.path("validation");
        double duration = execution.path("durationSeconds").asDouble(Double.NaN);
        double step = execution.path("stepSeconds").asDouble(Double.NaN);
        double tolerance = validation.path("tolerance").asDouble(Double.NaN);
        List<Double> fractions = new ArrayList<>();
        for (JsonNode node : validation.path("checkpointFractions")) fractions.add(node.asDouble(Double.NaN));
        Set<String> outputs = new HashSet<>();
        for (JsonNode node : definition.path("output").path("probeSeries")) {
            if (!node.isTextual() || node.asText().isBlank() || !outputs.add(node.asText())) {
                throw invalid(schemaId, "duplicate or invalid output key");
            }
        }
        String model = definition.path("model").asText("").trim();
        if (model.isBlank() || !finite(duration) || !finite(step) || !finite(tolerance)) {
            throw invalid(schemaId, "model/execution/validation is invalid");
        }
        return new CompiledSchema(schemaId, definition.path("version").asText("1.0"),
                definition.path("topic").asText(""), model, quantities, aliases, adjustments, outputs,
                new CompiledSchema.ExecutionDefinition(duration, step),
                new CompiledSchema.ValidationDefinition(tolerance, fractions), checksum(definition));
    }

    private void compileQuantities(JsonNode fields, Map<String, CompiledSchema.QuantityDefinition> quantities,
            Map<String, String> aliases, String schemaId) {
        for (JsonNode node : fields) {
            String key = node.path("key").asText("").trim();
            Set<String> allowed = textSet(node.path("allowedUnits"));
            Set<String> aliasSet = new HashSet<>();
            for (JsonNode alias : node.path("aliases")) aliasSet.add(alias.asText("").trim());
            Double defaultValue = node.path("defaultValue").isNumber() ? node.path("defaultValue").asDouble() : null;
            if (key.isBlank() || allowed.isEmpty() || quantities.containsKey(key)
                    || (defaultValue != null && !finite(defaultValue))) throw invalid(schemaId, "invalid quantity " + key);
            quantities.put(key, new CompiledSchema.QuantityDefinition(key, aliasSet, allowed,
                    node.path("positive").asBoolean(false), node.path("nonNegative").asBoolean(false), defaultValue));
            for (String alias : aliasSet) {
                if (alias.isBlank() || alias.equals(key) || aliases.putIfAbsent(alias, key) != null) {
                    throw invalid(schemaId, "alias collision " + alias);
                }
            }
        }
    }

    private Set<String> textSet(JsonNode values) {
        Set<String> result = new HashSet<>();
        for (JsonNode value : values) if (value.isTextual() && !value.asText().isBlank()) result.add(value.asText());
        return result;
    }

    public String checksum(JsonNode definition) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(definition);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot checksum compiled schema", exception);
        }
    }

    private boolean finite(double value) { return Double.isFinite(value); }
    private IllegalArgumentException invalid(String schemaId, String reason) {
        return new IllegalArgumentException("Schema compiler rejected schemaId=" + schemaId + ": " + reason);
    }
}
