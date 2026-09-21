package com.example.backend.dto.school;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SchoolClassRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(10) @Max(12) Integer gradeLevel,
        @NotBlank @Size(max = 20) @Pattern(regexp = "\\d{4}-\\d{4}") String schoolYear,
        @Size(max = 50) String subject) {
}
