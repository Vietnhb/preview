package com.example.backend.dto.school;

import jakarta.validation.constraints.NotNull;

public record AssignTeacherRequest(@NotNull Integer teacherId) {
}
