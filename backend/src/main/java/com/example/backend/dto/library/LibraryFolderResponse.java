package com.example.backend.dto.library;

import java.time.Instant;
import java.util.UUID;

public record LibraryFolderResponse(
        UUID id,
        String name,
        long itemCount,
        Instant createdAt,
        Instant updatedAt) {
}
