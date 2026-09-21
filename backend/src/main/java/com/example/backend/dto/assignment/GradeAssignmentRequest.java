package com.example.backend.dto.assignment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record GradeAssignmentRequest(
        @NotNull @DecimalMin("0.0") BigDecimal score,
        @Size(max = 4000) String feedback,
        boolean confirm) {
}
