package com.example.backend.physics.model;

import com.example.backend.physics.model.ScalarField;

import java.util.List;
import java.util.Map;

public record SolverOutput(
        List<Double> time,
        Map<String, List<Double>> positions,
        Map<String, List<Double>> velocities,
        Map<String, List<Double>> accelerations,
        Map<String, List<Double>> values,
        Map<String, ScalarField> scalarFields,
        Map<String, Double> scalarOutputs) {

    /** Keeps all existing solvers and callers source-compatible. */
    public SolverOutput(List<Double> time,
            Map<String, List<Double>> positions,
            Map<String, List<Double>> velocities,
            Map<String, List<Double>> accelerations,
            Map<String, List<Double>> values) {
        this(time, positions, velocities, accelerations, values, Map.of(), Map.of());
    }

    /** Keeps callers using the prior six-component contract source-compatible. */
    public SolverOutput(List<Double> time,
            Map<String, List<Double>> positions,
            Map<String, List<Double>> velocities,
            Map<String, List<Double>> accelerations,
            Map<String, List<Double>> values,
            Map<String, ScalarField> scalarFields) {
        this(time, positions, velocities, accelerations, values, scalarFields, Map.of());
    }

    public SolverOutput {
        time = time == null ? List.of() : List.copyOf(time);
        positions = copySeries(positions);
        velocities = copySeries(velocities);
        accelerations = copySeries(accelerations);
        values = copySeries(values);
        scalarFields = scalarFields == null ? Map.of() : Map.copyOf(scalarFields);
        scalarOutputs = copyScalarOutputs(scalarOutputs);
    }

    private static Map<String, List<Double>> copySeries(Map<String, List<Double>> source) {
        if (source == null || source.isEmpty())
            return Map.of();
        java.util.Map<String, List<Double>> copy = new java.util.LinkedHashMap<>();
        source.forEach((key, series) -> copy.put(key, series == null ? List.of() : List.copyOf(series)));
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static Map<String, Double> copyScalarOutputs(Map<String, Double> source) {
        if (source == null || source.isEmpty()) return Map.of();
        java.util.Map<String, Double> copy = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Scalar output key is required");
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("Scalar output must be finite: " + key);
            }
            copy.put(key, value);
        });
        return java.util.Collections.unmodifiableMap(copy);
    }
}
