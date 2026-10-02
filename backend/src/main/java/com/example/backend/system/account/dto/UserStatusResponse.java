package com.example.backend.system.account.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserStatusResponse(Integer id, String email, String fullName, String role, boolean active,
        String institutionId, Instant lastLogin, LocalDate dateOfBirth, String avatarUrl,
        UUID schoolId, String schoolName, boolean mustChangePassword,
        java.util.List<String> permissions) {
}
