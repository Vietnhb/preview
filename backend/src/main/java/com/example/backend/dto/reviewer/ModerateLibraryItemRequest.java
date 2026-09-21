package com.example.backend.dto.reviewer;

import com.example.backend.entity.enums.LibraryModerationStatus;
import jakarta.validation.constraints.Size;

public record ModerateLibraryItemRequest(
        LibraryModerationStatus status,
        @Size(max = 4000) String comment) {
}
