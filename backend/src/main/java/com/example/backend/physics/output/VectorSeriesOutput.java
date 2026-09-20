package com.example.backend.physics.output;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A vector-valued series where every sample has the declared components. */
public record VectorSeriesOutput(String key, Optional<String> unit, List<Double> timeSeconds,
                                 List<String> componentKeys, List<List<Double>> values) implements PhysicsOutput {
    public VectorSeriesOutput {
        ScalarOutput.requireKey(key);
        unit = PhysicsOutputValues.validateUnit(unit);
        timeSeconds = PhysicsOutputValues.copyFinite(timeSeconds, "vector time axis");
        PhysicsOutputValues.validateTime(timeSeconds);
        if (componentKeys == null || componentKeys.isEmpty()
                || componentKeys.stream().anyMatch(component -> component == null || component.isBlank())
                || componentKeys.stream().distinct().count() != componentKeys.size()) {
            throw new IllegalArgumentException("Vector component keys must be non-empty and unique");
        }
        componentKeys = List.copyOf(componentKeys);
        if (values == null || values.size() != timeSeconds.size()) {
            throw new IllegalArgumentException("Vector values must match the time axis length");
        }
        List<List<Double>> copied = new ArrayList<>(values.size());
        for (List<Double> vector : values) {
            List<Double> finite = PhysicsOutputValues.copyFinite(vector, "vector sample");
            if (finite.size() != componentKeys.size()) {
                throw new IllegalArgumentException("Vector sample shape must match component keys");
            }
            copied.add(finite);
        }
        values = List.copyOf(copied);
    }

    public VectorSeriesOutput(String key, String unit, List<Double> timeSeconds,
                              List<String> componentKeys, List<List<Double>> values) {
        this(key, Optional.of(unit), timeSeconds, componentKeys, values);
    }

    @Override public OutputKind kind() { return OutputKind.VECTOR_SERIES; }
}
