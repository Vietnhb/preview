package com.example.backend.dto.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupportRequest(
        @NotBlank @Size(max = 180) String subject,
        @NotBlank @Size(max = 10000) String content) {
}
