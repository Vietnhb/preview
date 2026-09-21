package com.example.backend.dto.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StudentActionLogRequest(
        @NotNull UUID assignmentId,
        @NotBlank @Size(max = 64) String action,
        JsonNode payload) {
}
