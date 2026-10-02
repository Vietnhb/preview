package com.example.backend.system.support.dto;

import com.example.backend.system.support.model.enums.SupportKind;
import com.example.backend.system.support.model.enums.SupportStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Requests and responses for feedback and support messages. */
public final class SupportContracts {
    private SupportContracts() { }

    public record CreateSupportRequest(
            @NotBlank @Size(max = 180) String subject,
            @NotBlank @Size(max = 10000) String content) {
    }

    public record UpdateSupportRequest(
            SupportStatus status,
            @Size(max = 10000) String response) {
    }

    public record SupportView(UUID id, SupportKind kind, Integer senderId, String senderName, String senderEmail,
                              String subject, String content, SupportStatus status, String adminResponse,
                              Instant createdAt, Instant respondedAt) { }
}
