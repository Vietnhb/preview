package com.example.backend.dto.reviewer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ArchiveBenchmarkRequest(
        @NotBlank @Size(max = 4000) String reason) {
}
