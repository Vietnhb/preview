package com.example.backend.physics;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface PhysicsSolver {
    String solverId();

    SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                       double durationSeconds, double stepSeconds);
}
