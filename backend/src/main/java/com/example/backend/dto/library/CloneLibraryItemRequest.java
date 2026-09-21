package com.example.backend.dto.library;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CloneLibraryItemRequest(
        @NotNull UUID folderId,
        @Size(max = 160) String title) {
}
