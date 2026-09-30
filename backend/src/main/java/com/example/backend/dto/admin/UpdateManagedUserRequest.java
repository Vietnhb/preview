package com.example.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateManagedUserRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank String role,
        String institutionId,
        @PastOrPresent LocalDate dateOfBirth,
        @Size(max = 2_500_000) String avatarUrl,
        String staffType,
        Boolean reviewerCanEdit,
        Boolean reviewerCanReview) {
    public UpdateManagedUserRequest(String fullName, String role, String institutionId) {
        this(fullName, role, institutionId, null, null, null, null, null);
    }
}
