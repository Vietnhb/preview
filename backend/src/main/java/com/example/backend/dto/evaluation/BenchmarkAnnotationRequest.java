package com.example.backend.dto.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BenchmarkAnnotationRequest(
        @NotNull JsonNode specification,
        @Size(max = 120) String schemaCatalogChecksum,
        @Size(max = 120) String promptVersion,
        @Size(max = 120) String modelVersion) {
}
