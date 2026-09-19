package com.example.backend.dto.simulation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record SimulationRequest(
        @NotNull UUID specificationId,
        @Valid Map<String, Double> adjustableParams) {
}
