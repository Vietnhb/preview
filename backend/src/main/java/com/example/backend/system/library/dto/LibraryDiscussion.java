package com.example.backend.system.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response types for one resource's discussion. */
public final class LibraryDiscussion {
    private LibraryDiscussion() { }

    public record CommentRequest(@NotBlank String body) { }

    public record ReactionRequest(@NotNull Boolean liked) { }

    public record Comment(UUID id, Integer authorId, String authorName, String avatarUrl,
                          String body, Instant createdAt, boolean canDelete) { }

    public record Reaction(long likes, boolean liked) { }

    public record Discussion(long likes, boolean liked, long commentCount, List<Comment> comments,
                             int page, boolean hasMore, boolean canInteract, int commentMaxLength) { }
}
