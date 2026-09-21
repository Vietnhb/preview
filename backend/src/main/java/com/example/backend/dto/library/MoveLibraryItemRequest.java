package com.example.backend.dto.library;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoveLibraryItemRequest(@NotNull UUID folderId) {
}
