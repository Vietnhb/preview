package com.example.backend.dto.reviewer;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.backend.enums.LifecycleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SchemaRequest(
        @NotBlank @jakarta.validation.constraints.Size(max=80) String schemaId,
        @NotBlank @jakarta.validation.constraints.Size(max=120) String name,
        @NotBlank @jakarta.validation.constraints.Size(max=32) String topic,
        @NotBlank @jakarta.validation.constraints.Size(max=16) String version,
        @NotNull JsonNode definition,
        LifecycleStatus lifecycleStatus) {
}
