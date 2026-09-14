package com.example.backend.dto.library;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LibraryFolderRequest(
        @NotBlank @Size(max = 120) String name) {
}
