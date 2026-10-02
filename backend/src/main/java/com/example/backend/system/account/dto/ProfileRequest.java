package com.example.backend.system.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Validated inputs for the current user's profile operations. */
public final class ProfileRequest {
    private ProfileRequest() { }

    public record Details(@NotBlank @Size(max = 120) String fullName, LocalDate dateOfBirth) { }

    public record Password(@NotBlank String currentPassword,
                           @NotBlank @Size(min = 8, max = 120) String newPassword) { }

    public record Avatar(@NotBlank @Size(max = 2_500_000) String avatarUrl) { }
}
