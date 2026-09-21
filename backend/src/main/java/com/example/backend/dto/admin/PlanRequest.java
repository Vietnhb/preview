package com.example.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PlanRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Z0-9_-]+") String code,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String description,
        @NotNull @Positive Long annualPriceVnd,
        @NotNull @Positive Integer studentQuota,
        @PositiveOrZero Integer monthlyTokenQuota,
        boolean active) {
}
