package com.example.backend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SchoolRequest(
        @NotBlank @Size(max = 80) String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 300) String address,
        boolean active,
        LocalDate licenseStart,
        LocalDate licenseEnd,
        @PositiveOrZero Integer monthlyTokenQuota) {
}
