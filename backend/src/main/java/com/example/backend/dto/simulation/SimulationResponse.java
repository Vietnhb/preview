package com.example.backend.dto.simulation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.example.backend.physics.model.ScalarField;

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
        Map<String, Double> scalarOutputs,
        Map<String, ScalarField> scalarFields,
        Map<String, Double> adjustableParams,
        JsonNode visualization,
        ValidationResponse validation,
        JsonNode rawResult,
        ResolvedEnd resolvedEnd,
        double computationTimeMs,
        String message) {

    /** Keeps source compatibility for callers that predate standalone scalar outputs. */
    public SimulationResponse(UUID simulationId, UUID simulationRunId, UUID specificationId, String schemaId,
            boolean success, boolean validationPassed, List<Double> time,
            Map<String, List<Double>> positions, Map<String, List<Double>> velocities,
            Map<String, List<Double>> accelerations, Map<String, List<Double>> values,
            Map<String, ScalarField> scalarFields, Map<String, Double> adjustableParams,
            JsonNode visualization, ValidationResponse validation, JsonNode rawResult,
            ResolvedEnd resolvedEnd, double computationTimeMs, String message) {
        this(simulationId, simulationRunId, specificationId, schemaId, success, validationPassed, time,
                positions, velocities, accelerations, values, Map.of(), scalarFields, adjustableParams,
                visualization, validation, rawResult, resolvedEnd, computationTimeMs, message);
    }
}
