package com.example.backend.dto.session;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SaveSessionRequest(
        @NotNull UUID specificationId,
        @NotBlank String title,
        @NotNull JsonNode initialSpecification,
        @NotNull JsonNode parameterTimeline,
        double durationSeconds) {
}
