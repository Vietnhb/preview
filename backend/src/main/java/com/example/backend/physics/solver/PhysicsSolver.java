package com.example.backend.physics.solver;

import com.example.backend.physics.model.SolverOutput;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface PhysicsSolver {
    String solverId();

    SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                       double durationSeconds, double stepSeconds);
}
