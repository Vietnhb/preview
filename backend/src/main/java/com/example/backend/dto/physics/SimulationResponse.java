package com.example.backend.dto.physics;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SimulationResponse(
        UUID simulationId,
        UUID simulationRunId,
        UUID specificationId,
        String schemaId,
        boolean success,
        boolean validationPassed,
        List<Double> time,
        Map<String, List<Double>> positions,
        Map<String, List<Double>> velocities,
        Map<String, List<Double>> accelerations,
        Map<String, List<Double>> values,
        Map<String, Double> adjustableParams,
        JsonNode visualization,
        ValidationResponse validation,
        JsonNode rawResult,
        ResolvedEnd resolvedEnd,
        double computationTimeMs,
        String message) {
}
