package com.example.backend.dto.admin;

import java.time.Instant;
import java.time.LocalDate;

public record UserStatusResponse(Integer id, String email, String fullName, String role, boolean active,
        String institutionId, Instant lastLogin, LocalDate dateOfBirth) {
}
