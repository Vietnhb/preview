package com.example.backend.dto.physics;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;
import java.util.UUID;

public record ParameterAdjustmentRequest(
        UUID simulationId,
        @NotEmpty Map<String, Double> adjustableParams) {
}
