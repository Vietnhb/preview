package com.example.backend.system.library.dto;

import com.example.backend.system.library.model.enums.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Input contracts for personal library items; validation is shared by all HTTP handlers. */
public final class LibraryRequests {
    private LibraryRequests() { }
    public record Save(@NotNull UUID simulationId, @NotNull UUID folderId, @NotNull UUID lessonId,
                       @NotBlank @Size(max = 160) String title, Visibility visibility) { }
    public record Clone(@NotNull UUID folderId, @Size(max = 160) String title) { }
    public record Rename(@NotBlank @Size(max = 160) String title) { }
    public record Move(@NotNull UUID folderId) { }
    public record Share(@NotNull Visibility visibility) { }
}
