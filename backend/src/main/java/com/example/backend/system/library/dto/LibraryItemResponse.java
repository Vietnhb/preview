package com.example.backend.system.library.dto;

import com.example.backend.system.library.model.entity.LibraryItem;
import com.example.backend.system.library.model.enums.LibraryModerationStatus;
import com.example.backend.system.library.model.enums.Visibility;
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
        String moderationComment,
        Integer sharedById,
        String sharedByName,
        UUID schoolId,
        String schoolName) {
    public static LibraryItemResponse from(LibraryItem item) {
        var owner = item.getOwner();
        var school = owner == null ? null : owner.getSchool();
        return new LibraryItemResponse(item.getId(), item.getSimulation() == null ? null : item.getSimulation().getId(),
                item.getFolder() == null ? null : item.getFolder().getId(), item.getLesson() == null ? null : item.getLesson().getId(),
                item.getSpecification().getId(), item.getTitle(), item.getSpecification().getTopic(), item.getSpecification().getValidationStatus(),
                item.getVisibility(), item.getCreatedAt(), item.getModerationStatus(), item.getModerationComment(),
                owner == null ? null : owner.getId(), owner == null ? null : owner.getFullName(),
                school == null ? null : school.getId(), school == null ? null : school.getName());
    }
}
