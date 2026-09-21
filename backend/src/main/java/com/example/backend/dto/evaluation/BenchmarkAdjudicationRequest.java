package com.example.backend.dto.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BenchmarkAdjudicationRequest(
        @NotNull JsonNode specification,
        @NotBlank @Size(max = 4000) String rationale,
        @Size(max = 1000) String disagreementCategories) {
}
