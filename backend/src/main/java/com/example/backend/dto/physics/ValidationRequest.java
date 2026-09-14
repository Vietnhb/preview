package com.example.backend.dto.physics;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record ValidationRequest(
        @NotNull JsonNode specification,
        @NotNull String schemaId,
        @NotNull SimulationResponse simulation,
        Map<String, Double> adjustableParams) {
}
