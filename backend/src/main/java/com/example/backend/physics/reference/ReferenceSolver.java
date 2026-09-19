package com.example.backend.physics.reference;

import com.example.backend.physics.model.AnalyticalPoint;

import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

public interface ReferenceSolver {
    String solverId();
    AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds);
}
