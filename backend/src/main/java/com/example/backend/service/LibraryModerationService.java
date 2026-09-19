package com.example.backend.service;

import com.example.backend.dto.library.LibraryItemResponse;
import com.example.backend.entity.LibraryItem;
import com.example.backend.entity.LibraryModerationAudit;
import com.example.backend.entity.LibraryModerationStatus;
import com.example.backend.entity.Visibility;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.LibraryItemRepository;
import com.example.backend.repository.LibraryModerationAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LibraryModerationService {
    public record ModerationAuditView(UUID id, UUID itemId, String reviewerEmail, LibraryModerationStatus fromStatus,
                                      LibraryModerationStatus toStatus, String comment, Instant decidedAt) { }
    private final LibraryItemRepository items;
    private final LibraryModerationAuditRepository audits;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public List<LibraryItemResponse> queue(LibraryModerationStatus status) {
        return items.findByVisibilityAndModerationStatusOrderByCreatedAtAsc(Visibility.SHARED,
                        status == null ? LibraryModerationStatus.PENDING : status)
                .stream().map(this::response).toList();
    }

    @Transactional
    public LibraryItemResponse moderate(UUID id, LibraryModerationStatus status, String comment) {
        var actor = currentUser.requireCurrentUser();
        LibraryItem item = items.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Shared library item not found"));
        if (item.getVisibility() != Visibility.SHARED)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only shared library items can be moderated");
        if (status == null || status == LibraryModerationStatus.PENDING)
            throw new ApiException(HttpStatus.BAD_REQUEST, "A final moderation status is required");
        LibraryModerationAudit audit = new LibraryModerationAudit(); audit.setLibraryItem(item); audit.setReviewer(actor);
        audit.setFromStatus(item.getModerationStatus()); audit.setToStatus(status); audit.setComment(comment == null ? null : comment.trim()); audits.save(audit);
        item.setModerationStatus(status);
        item.setModerationComment(comment == null ? null : comment.trim());
        item.setModeratedAt(Instant.now()); item.setModeratedBy(actor);
        item.setActive(status != LibraryModerationStatus.REMOVED);
        return response(items.save(item));
    }

    @Transactional(readOnly = true)
    public List<ModerationAuditView> history(UUID id) { return audits.findTop100ByLibraryItemIdOrderByDecidedAtDesc(id).stream().map(item -> new ModerationAuditView(item.getId(), item.getLibraryItem().getId(), item.getReviewer().getEmail(), item.getFromStatus(), item.getToStatus(), item.getComment(), item.getDecidedAt())).toList(); }

    private LibraryItemResponse response(LibraryItem item) {
        return new LibraryItemResponse(item.getId(), item.getSimulation() == null ? null : item.getSimulation().getId(),
                item.getFolder() == null ? null : item.getFolder().getId(), item.getLesson() == null ? null : item.getLesson().getId(),
                item.getSpecification().getId(), item.getTitle(), item.getSpecification().getTopic(), item.getSpecification().getValidationStatus(),
                item.getVisibility(), item.getCreatedAt(), item.getModerationStatus(), item.getModerationComment());
    }
}
