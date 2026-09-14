package com.example.backend.dto.curriculum;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record CurriculumNodeRequest(
        @NotBlank String name,
        String slug,
        @PositiveOrZero Integer sortOrder) {
}
