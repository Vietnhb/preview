package com.example.backend.dto.library;

import com.example.backend.entity.enums.Visibility;
import com.example.backend.entity.enums.LibraryModerationStatus;

import java.time.Instant;
import java.util.UUID;

public record LibraryItemResponse(
        UUID id,
        UUID simulationId,
        UUID folderId,
        UUID lessonId,
        UUID specificationId,
        String title,
        String topic,
        String validationStatus,
        Visibility visibility,
        Instant createdAt,
        LibraryModerationStatus moderationStatus,
        String moderationComment) {
}
