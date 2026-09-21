package com.example.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record UpdateManagedUserRequest(
        @NotBlank String fullName,
        @NotBlank String role,
        String institutionId) {
}
