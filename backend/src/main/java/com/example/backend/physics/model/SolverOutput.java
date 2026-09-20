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
        Map<String, ScalarField> scalarFields) {

    /** Keeps all existing solvers and callers source-compatible. */
    public SolverOutput(List<Double> time,
            Map<String, List<Double>> positions,
            Map<String, List<Double>> velocities,
            Map<String, List<Double>> accelerations,
            Map<String, List<Double>> values) {
        this(time, positions, velocities, accelerations, values, Map.of());
    }

    public SolverOutput {
        time = time == null ? List.of() : List.copyOf(time);
        positions = copySeries(positions);
        velocities = copySeries(velocities);
        accelerations = copySeries(accelerations);
        values = copySeries(values);
        scalarFields = scalarFields == null ? Map.of() : Map.copyOf(scalarFields);
    }

    private static Map<String, List<Double>> copySeries(Map<String, List<Double>> source) {
        if (source == null || source.isEmpty())
            return Map.of();
        java.util.Map<String, List<Double>> copy = new java.util.LinkedHashMap<>();
        source.forEach((key, series) -> copy.put(key, series == null ? List.of() : List.copyOf(series)));
        return java.util.Collections.unmodifiableMap(copy);
    }
}
