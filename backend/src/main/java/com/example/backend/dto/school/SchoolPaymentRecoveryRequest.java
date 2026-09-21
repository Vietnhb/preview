package com.example.backend.dto.school;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SchoolPaymentRecoveryRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {
}
