package com.example.backend.dto.reviewer;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SolverRequest(
        @NotBlank @Size(max = 80) String schemaId,
        @NotBlank @Size(max = 120) String solverId,
        @NotBlank @Size(max = 16) String version,
        @NotNull JsonNode outputDefinition) {
}
