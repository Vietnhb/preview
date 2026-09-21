package com.example.backend.dto.evaluation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BenchmarkUpdateRequest(
        @NotBlank @Size(max = 10000) String problemText,
        @NotBlank @Size(max = 32) String topic,
        @NotBlank @Size(max = 32) String gradeScope,
        @NotBlank @Size(max = 80) String sourceCategory) {
}
