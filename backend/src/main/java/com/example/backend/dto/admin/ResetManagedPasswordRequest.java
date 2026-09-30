package com.example.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetManagedPasswordRequest(@NotBlank @Size(min = 8, max = 120) String newPassword) {
}
