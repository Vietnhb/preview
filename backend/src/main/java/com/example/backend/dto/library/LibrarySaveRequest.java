package com.example.backend.dto.library;

import com.example.backend.entity.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LibrarySaveRequest(
        @NotNull UUID simulationId,
        @NotNull UUID folderId,
        @NotNull UUID lessonId,
        @NotBlank String title,
        Visibility visibility) {
}
