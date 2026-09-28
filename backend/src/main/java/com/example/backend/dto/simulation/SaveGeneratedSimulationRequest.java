package com.example.backend.dto.simulation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SaveGeneratedSimulationRequest(
        @NotBlank @Size(max = 160) String title,
        @NotNull UUID folderId,
        @NotNull UUID lessonId,
        @NotNull ObjectNode simulation,
        @NotNull ObjectNode parameters) {}
