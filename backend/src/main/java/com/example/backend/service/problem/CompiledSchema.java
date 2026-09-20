package com.example.backend.service.problem;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
        ExecutionDefinition execution,
        ValidationDefinition validation,
        String checksum) {
    public CompiledSchema {
        quantities = Map.copyOf(quantities);
        aliases = Map.copyOf(aliases);
        adjustments = Map.copyOf(adjustments);
        outputKeys = Set.copyOf(outputKeys);
    }

    public record QuantityDefinition(String key, Set<String> aliases, Set<String> allowedUnits,
                                     boolean positive, boolean nonNegative, Double defaultValue) {
        public QuantityDefinition {
            aliases = Set.copyOf(aliases);
            allowedUnits = Set.copyOf(allowedUnits);
        }
    }

    public record AdjustmentDefinition(String key, double min, double max, double step) { }
    public record ExecutionDefinition(double durationSeconds, double stepSeconds) { }
    public record ValidationDefinition(double tolerance, List<Double> checkpointFractions) {
        public ValidationDefinition { checkpointFractions = List.copyOf(checkpointFractions); }
    }
}
