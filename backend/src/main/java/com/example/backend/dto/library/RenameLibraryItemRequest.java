package com.example.backend.dto.library;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameLibraryItemRequest(
        @NotBlank @Size(max = 160) String title) {
}
