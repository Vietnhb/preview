package com.example.backend.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateManagedUserRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 120) String password,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank String role,
        String institutionId,
        @PastOrPresent LocalDate dateOfBirth,
        @Size(max = 2_500_000) String avatarUrl,
        String staffType,
        Boolean reviewerCanEdit,
        Boolean reviewerCanReview) {
    public CreateManagedUserRequest(String email, String password, String fullName, String role, String institutionId) {
        this(email, password, fullName, role, institutionId, null, null, null, null, null);
    }

    public CreateManagedUserRequest(String email, String password, String fullName, String role,
                                    String institutionId, LocalDate dateOfBirth, String avatarUrl) {
        this(email, password, fullName, role, institutionId, dateOfBirth, avatarUrl, null, null, null);
    }
}
