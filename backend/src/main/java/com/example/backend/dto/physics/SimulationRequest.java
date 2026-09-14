package com.example.backend.dto.physics;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.util.Map;
import java.util.UUID;

public record SimulationRequest(
        UUID specificationId,
        String schemaId,
        JsonNode specification,
        Map<String, Double> adjustableParams,
        @DecimalMin("1.0") @DecimalMax("60.0") Double durationSeconds,
        @DecimalMin("0.001") @DecimalMax("0.2") Double stepSeconds) {
}
