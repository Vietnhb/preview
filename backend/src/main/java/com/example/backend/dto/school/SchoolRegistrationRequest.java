package com.example.backend.dto.school;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SchoolRegistrationRequest(
        @NotBlank String planCode,
        @NotBlank @Size(max = 200) String schoolName,
        @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_-]+") String schoolCode,
        @NotBlank @Size(max = 300) String address,
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(max = 20) String phoneNumber,
        @NotBlank @Size(min = 8, max = 72) String password) {
}
