package com.example.backend.dto.admin;

public record UserStatusResponse(Integer id, String email, String fullName, String role, boolean active, String institutionId) {
}
