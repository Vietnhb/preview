package com.example.backend.system.library.dto;

import com.example.backend.system.library.model.enums.LibraryModerationStatus;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Moderation contracts shared by platform and school controllers. */
public final class LibraryModeration {
    private LibraryModeration() { }
    public record Request(LibraryModerationStatus status, @Size(max = 4000) String comment) { }
    public record Audit(UUID id, UUID itemId, String reviewerEmail, LibraryModerationStatus fromStatus,
                        LibraryModerationStatus toStatus, String comment, Instant decidedAt) { }
    public record Page(List<LibraryItemResponse> items, int page, int size, long totalElements, int totalPages) { }
}
