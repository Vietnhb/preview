package com.example.backend.service.problem;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import com.example.backend.physics.output.PhysicsOutputContract;
import com.example.backend.physics.validation.OutputSourceBinding;

/** Immutable runtime schema snapshot consumed by boundary services. */
public record CompiledSchema(
        String schemaId,
        String version,
        String topic,
        String modelId,
        Map<String, QuantityDefinition> quantities,
        Map<String, String> aliases,
        Map<String, AdjustmentDefinition> adjustments,
        Set<String> outputKeys,
        Map<String, PhysicsOutputContract.OutputDefinition> outputDefinitions,
        Map<String, List<OutputSourceBinding>> endConditionSources,
        ExecutionDefinition execution,
        ValidationDefinition validation,
        String checksum) {
    public CompiledSchema {
        quantities = Map.copyOf(quantities);
        aliases = Map.copyOf(aliases);
        adjustments = Map.copyOf(adjustments);
        outputKeys = Set.copyOf(outputKeys);
        outputDefinitions = outputDefinitions == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(outputDefinitions));
        if (endConditionSources == null) {
            endConditionSources = Map.of();
        } else {
            LinkedHashMap<String, List<OutputSourceBinding>> sources = new LinkedHashMap<>();
            endConditionSources.forEach((key, value) -> sources.put(key, List.copyOf(value)));
            endConditionSources = java.util.Collections.unmodifiableMap(sources);
        }
    }

    /** Retains source compatibility for compiled schemas created before typed output declarations. */
    public CompiledSchema(String schemaId, String version, String topic, String modelId,
            Map<String, QuantityDefinition> quantities, Map<String, String> aliases,
            Map<String, AdjustmentDefinition> adjustments, Set<String> outputKeys,
            ExecutionDefinition execution, ValidationDefinition validation, String checksum) {
        this(schemaId, version, topic, modelId, quantities, aliases, adjustments, outputKeys,
                Map.of(), Map.of(), execution, validation, checksum);
    }

    /** Retains source compatibility for callers created before compiled end-condition bindings. */
    public CompiledSchema(String schemaId, String version, String topic, String modelId,
            Map<String, QuantityDefinition> quantities, Map<String, String> aliases,
            Map<String, AdjustmentDefinition> adjustments, Set<String> outputKeys,
            Map<String, PhysicsOutputContract.OutputDefinition> outputDefinitions,
            ExecutionDefinition execution, ValidationDefinition validation, String checksum) {
        this(schemaId, version, topic, modelId, quantities, aliases, adjustments, outputKeys,
                outputDefinitions, Map.of(), execution, validation, checksum);
    }

    public PhysicsOutputContract outputContract(int maxSamples) {
        return outputDefinitions.isEmpty() ? null
                : new PhysicsOutputContract(schemaId, version, modelId, outputDefinitions, maxSamples);
    }

    public record QuantityDefinition(String key, Set<String> aliases, Set<String> allowedUnits,
                                     Set<String> symbols,
                                     boolean positive, boolean nonNegative, boolean integer,
                                     Double minimum, Double maximum,
                                     Double defaultValue, String defaultUnit) {
        public QuantityDefinition {
            aliases = Set.copyOf(aliases);
            allowedUnits = Set.copyOf(allowedUnits);
            symbols = symbols == null ? Set.of() : Set.copyOf(symbols);
            defaultUnit = defaultUnit == null ? "" : defaultUnit;
        }

        public QuantityDefinition(String key, Set<String> aliases, Set<String> allowedUnits,
                boolean positive, boolean nonNegative, boolean integer,
                Double minimum, Double maximum, Double defaultValue, String defaultUnit) {
            this(key, aliases, allowedUnits, Set.of(), positive, nonNegative, integer,
                    minimum, maximum, defaultValue, defaultUnit);
        }
    }

    public record AdjustmentDefinition(String key, double min, double max, double step) { }
    public record ExecutionDefinition(double durationSeconds, double stepSeconds) { }
    public record OutputValidationDefinition(double absoluteTolerance, double relativeTolerance, String comparison) {
        public OutputValidationDefinition {
            comparison = comparison == null ? "numeric" : comparison;
        }
    }
    public record ValidationDefinition(double tolerance, List<Double> checkpointFractions,
                                       Map<String, OutputValidationDefinition> outputTolerances) {
        public ValidationDefinition {
            checkpointFractions = List.copyOf(checkpointFractions);
            outputTolerances = outputTolerances == null ? Map.of() : Map.copyOf(outputTolerances);
        }

        public ValidationDefinition(double tolerance, List<Double> checkpointFractions) {
            this(tolerance, checkpointFractions, Map.of());
        }
    }
}
