package com.example.backend.physics.model;

import java.util.List;
import java.util.Map;

public record SolverOutput(
        List<Double> time,
        Map<String, List<Double>> positions,
        Map<String, List<Double>> velocities,
        Map<String, List<Double>> accelerations,
        Map<String, List<Double>> values) {
}
