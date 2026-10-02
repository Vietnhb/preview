package com.example.backend.system.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Folder input and output contracts, independent of persistence and service implementations. */
public final class LibraryFolders {
    private LibraryFolders() { }
    public record Request(@NotBlank @Size(max = 120) String name) { }
    public record Response(UUID id, String name, long itemCount, Instant createdAt, Instant updatedAt) { }
}
