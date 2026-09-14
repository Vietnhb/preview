package com.example.backend.dto.evaluation;

import com.fasterxml.jackson.databind.JsonNode;

public record EvaluationResponse(
        String evaluationType,
        int benchmarkCount,
        double precision,
        double recall,
        double f1,
        double kappa,
        double incorrectRate,
        JsonNode details) {
}
