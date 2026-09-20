package com.example.backend.service.problem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.exception.SchemaCompilationException;
import com.example.backend.physics.output.PhysicsOutput;
import com.example.backend.physics.output.PhysicsOutputContract;
import com.example.backend.physics.validation.OutputSourceBinding;

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
    private final UnitNormalizer unitNormalizer;

    public SchemaCompiler(ObjectMapper objectMapper) {
        this(objectMapper, new UnitNormalizer(objectMapper));
    }

    public SchemaCompiler(ObjectMapper objectMapper, UnitNormalizer unitNormalizer) {
        this.objectMapper = objectMapper;
        this.unitNormalizer = unitNormalizer;
    }

    public CompiledSchema compile(JsonNode definition, String schemaId) {
        String version = definition == null ? "" : definition.path("version").asText("1.0");
        String topic = definition == null ? "" : definition.path("topic").asText("");
        return compile(definition, schemaId, version, topic, false);
    }

    public CompiledSchema compile(JsonNode definition, String schemaId, String version, String topic) {
        return compile(definition, schemaId, version, topic, true);
    }

    private CompiledSchema compile(JsonNode definition, String schemaId, String version, String topic,
                                   boolean requireTopicIdentity) {
        if (definition == null || !definition.isObject()) throw invalid(schemaId, "definition must be an object");
        if (version == null || version.isBlank()) {
            throw invalid(schemaId, "schema version identity is required");
        }
        if (requireTopicIdentity && (topic == null || topic.isBlank())) {
            throw invalid(schemaId, "schema topic identity is required");
        }
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
        Map<String, CompiledSchema.OutputValidationDefinition> outputTolerances =
                compileOutputTolerances(validation.path("outputs"), tolerance, schemaId);
        Set<String> outputs = new java.util.LinkedHashSet<>();
        JsonNode outputDefinition = definition.path("output");
        for (JsonNode node : outputDefinition.path("probeSeries")) {
            if (!node.isTextual() || node.asText().isBlank() || !outputs.add(node.asText())) {
                throw invalid(schemaId, "duplicate or invalid output key");
            }
        }
        Map<String, PhysicsOutputContract.OutputDefinition> outputDefinitions = new LinkedHashMap<>();
        JsonNode typedOutputs = outputDefinition.path("definitions");
        if (!typedOutputs.isMissingNode() && !typedOutputs.isNull()) {
            if (!typedOutputs.isArray() || typedOutputs.isEmpty()) {
                throw invalid(schemaId, "output.definitions must be a non-empty array");
            }
            for (JsonNode node : typedOutputs) {
                String key = node.path("key").asText("").trim();
                String kindToken = node.path("kind").asText("").trim();
                String unit = node.path("unit").asText("").trim();
                PhysicsOutput.OutputKind kind;
                try {
                    kind = PhysicsOutput.OutputKind.valueOf(kindToken.toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException invalidKind) {
                    throw invalid(schemaId, "unsupported output kind for " + key + ": " + kindToken);
                }
                if (key.isBlank() || unit.isBlank() || !unitNormalizer.isKnownUnit(unit)
                        || !node.path("required").isBoolean()
                        || outputDefinitions.putIfAbsent(key, new PhysicsOutputContract.OutputDefinition(
                                kind, unit, node.path("required").asBoolean())) != null) {
                    throw invalid(schemaId, "duplicate or invalid typed output definition: " + key);
                }
                outputs.add(key);
            }
            if (outputDefinitions.size() != outputs.size()) {
                throw invalid(schemaId, "output.definitions must cover every declared output key");
            }
        }
        String model = definition.path("model").asText("").trim();
        if (model.isBlank() || !finite(duration) || !finite(step) || !finite(tolerance) || tolerance < 0) {
            throw invalid(schemaId, "model/execution/validation is invalid");
        }
        Map<String, List<OutputSourceBinding>> endConditionSources = compileEndConditionSources(
                definition, outputs, outputDefinitions);
        return new CompiledSchema(schemaId, version, topic == null ? "" : topic, model, quantities, aliases, adjustments, outputs,
                outputDefinitions, endConditionSources,
                new CompiledSchema.ExecutionDefinition(duration, step),
                new CompiledSchema.ValidationDefinition(tolerance, fractions, outputTolerances), checksum(definition));
    }

    /**
     * Compile output references once from the approved output and visualization declarations.
     * Unqualified references remain ambiguous if the catalog declares the same key in multiple
     * output groups; callers must then provide an explicit group-qualified reference.
     */
    private Map<String, List<OutputSourceBinding>> compileEndConditionSources(JsonNode definition,
            Set<String> declaredOutputs,
            Map<String, PhysicsOutputContract.OutputDefinition> outputDefinitions) {
        Map<String, java.util.LinkedHashSet<OutputSourceBinding>> sources = new LinkedHashMap<>();
        JsonNode visualizationSeries = definition.path("visualization").path("series");
        if (visualizationSeries.isArray()) {
            for (JsonNode series : visualizationSeries) {
                String source = series.path("source").asText("").trim();
                int separator = source.lastIndexOf('.');
                if (separator <= 0 || separator == source.length() - 1) continue;
                OutputSourceBinding.Group group = sourceGroup(source.substring(0, separator));
                String key = source.substring(separator + 1).trim();
                if (group != null && declaredOutputs.contains(key)) {
                    sources.computeIfAbsent(key, ignored -> new java.util.LinkedHashSet<>())
                            .add(OutputSourceBinding.declared(group, key));
                }
            }
        }

        boolean timeSeriesOutput = "timeseries".equalsIgnoreCase(
                definition.path("output").path("type").asText("").trim());
        for (String key : declaredOutputs) {
            boolean explicitlyTypedTimeSeries = outputDefinitions.containsKey(key)
                    && outputDefinitions.get(key).kind() == PhysicsOutput.OutputKind.TIME_SERIES;
            if ((timeSeriesOutput || explicitlyTypedTimeSeries)
                    && !sources.containsKey(key)) {
                sources.computeIfAbsent(key, ignored -> new java.util.LinkedHashSet<>())
                        .add(OutputSourceBinding.declared(OutputSourceBinding.Group.VALUES, key));
            }
        }

        Map<String, List<OutputSourceBinding>> compiled = new LinkedHashMap<>();
        sources.forEach((key, bindings) -> compiled.put(key, List.copyOf(bindings)));
        return java.util.Collections.unmodifiableMap(compiled);
    }

    private OutputSourceBinding.Group sourceGroup(String source) {
        return switch (source.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "value", "values" -> OutputSourceBinding.Group.VALUES;
            case "position", "positions" -> OutputSourceBinding.Group.POSITIONS;
            case "velocity", "velocities" -> OutputSourceBinding.Group.VELOCITIES;
            case "acceleration", "accelerations" -> OutputSourceBinding.Group.ACCELERATIONS;
            default -> null;
        };
    }

    private Map<String, CompiledSchema.OutputValidationDefinition> compileOutputTolerances(
            JsonNode definitions, double fallback, String schemaId) {
        Map<String, CompiledSchema.OutputValidationDefinition> result = new LinkedHashMap<>();
        if (definitions == null || definitions.isMissingNode() || definitions.isNull()) return Map.of();
        if (definitions.isObject()) {
            definitions.fields().forEachRemaining(entry -> putOutputTolerance(result, entry.getKey(),
                    entry.getValue(), fallback, schemaId));
        } else if (definitions.isArray()) {
            for (JsonNode node : definitions) {
                putOutputTolerance(result, node.path("key").asText(""), node, fallback, schemaId);
            }
        } else {
            throw invalid(schemaId, "validation.outputs must be an object or array");
        }
        return result;
    }

    private void putOutputTolerance(Map<String, CompiledSchema.OutputValidationDefinition> destination,
            String key, JsonNode node, double fallback, String schemaId) {
        String outputKey = key == null ? "" : key.trim();
        double absolute = node.path("absoluteTolerance").isNumber()
                ? node.path("absoluteTolerance").asDouble() : fallback;
        double relative = node.path("relativeTolerance").isNumber()
                ? node.path("relativeTolerance").asDouble() : fallback;
        String comparison = node.path("comparison").asText("numeric").trim().toLowerCase(java.util.Locale.ROOT);
        if (outputKey.isBlank() || !finite(absolute) || absolute < 0 || !finite(relative) || relative < 0
                || !Set.of("numeric", "exact", "discrete").contains(comparison)
                || destination.putIfAbsent(outputKey, new CompiledSchema.OutputValidationDefinition(
                        absolute, relative, comparison)) != null) {
            throw invalid(schemaId, "invalid or duplicate output validation contract for " + outputKey);
        }
    }

    private void compileQuantities(JsonNode fields, Map<String, CompiledSchema.QuantityDefinition> quantities,
            Map<String, String> aliases, String schemaId) {
        for (JsonNode node : fields) {
            String key = node.path("key").asText("").trim();
            Set<String> allowed = textSet(node.path("allowedUnits"));
            if (allowed.stream().anyMatch(unit -> !unitNormalizer.isKnownUnit(unit))) {
                throw invalid(schemaId, "unknown allowed unit for quantity " + node.path("key").asText(""));
            }
            Set<String> aliasSet = new HashSet<>();
            for (JsonNode alias : node.path("aliases")) aliasSet.add(alias.asText("").trim());
            Set<String> symbols = new HashSet<>(textSet(node.path("symbols")));
            String singularSymbol = node.path("symbol").asText("").trim();
            if (!singularSymbol.isBlank()) symbols.add(singularSymbol);
            Double defaultValue = node.path("defaultValue").isNumber() ? node.path("defaultValue").asDouble() : null;
            Double minimum = node.path("min").isNumber() ? node.path("min").asDouble() : null;
            Double maximum = node.path("max").isNumber() ? node.path("max").asDouble() : null;
            String defaultUnit = node.path("defaultUnit").asText(firstText(node.path("allowedUnits")));
            boolean integer = node.path("integer").asBoolean(false);
            boolean positive = node.path("positive").asBoolean(false);
            boolean nonNegative = node.path("nonNegative").asBoolean(false);
            if (key.isBlank() || allowed.isEmpty() || quantities.containsKey(key)
                    || (defaultValue != null && (!finite(defaultValue) || !allowed.contains(defaultUnit)
                    || (integer && defaultValue % 1.0 != 0.0)
                    || (positive && defaultValue <= 0.0) || (nonNegative && defaultValue < 0.0)
                    || (minimum != null && defaultValue < minimum)
                    || (maximum != null && defaultValue > maximum)))
                    || (minimum != null && !finite(minimum)) || (maximum != null && !finite(maximum))
                    || (minimum != null && maximum != null && minimum > maximum)) {
                throw invalid(schemaId, "invalid quantity " + key);
            }
            quantities.put(key, new CompiledSchema.QuantityDefinition(key, aliasSet, allowed, symbols,
                    positive, nonNegative, integer,
                    minimum, maximum, defaultValue, defaultUnit));
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

    private String firstText(JsonNode values) {
        if (values != null && values.isArray()) {
            for (JsonNode value : values) if (value.isTextual() && !value.asText().isBlank()) return value.asText();
        }
        return "";
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
    private SchemaCompilationException invalid(String schemaId, String reason) {
        return new SchemaCompilationException("Schema compiler rejected schemaId=" + schemaId + ": " + reason);
    }
}
