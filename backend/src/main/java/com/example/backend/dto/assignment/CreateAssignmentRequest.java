package com.example.backend.dto.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;

public record CreateAssignmentRequest(
        @NotNull UUID libraryItemId,
        @NotBlank String title,
        String description,
        @NotNull JsonNode questions,
        @NotEmpty Set<Integer> studentIds,
        Instant dueAt,
        JsonNode gradingCriteria,
        BigDecimal maxScore,
        Boolean autoGrade) {
    public CreateAssignmentRequest(UUID libraryItemId, String title, String description, JsonNode questions,
                                   Set<Integer> studentIds, Instant dueAt) {
        this(libraryItemId, title, description, questions, studentIds, dueAt, null, null, false);
    }
}
