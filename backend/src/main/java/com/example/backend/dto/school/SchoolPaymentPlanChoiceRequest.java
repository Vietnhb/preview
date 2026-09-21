package com.example.backend.dto.school;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record SchoolPaymentPlanChoiceRequest(
        @NotBlank String planCode,
        @Positive Long expectedAmountVnd) {
}
