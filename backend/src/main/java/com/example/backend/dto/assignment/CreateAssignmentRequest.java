package com.example.backend.dto.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;

public record CreateAssignmentRequest(
        @NotNull UUID libraryItemId,
        @NotBlank @Size(max = 160) String title,
        String description,
        @NotNull JsonNode questions,
        @NotEmpty Set<Integer> studentIds,
        Instant dueAt,
        JsonNode gradingCriteria,
        @DecimalMin("0.001") @Digits(integer = 5, fraction = 3) BigDecimal maxScore,
        Boolean autoGrade) {
    public CreateAssignmentRequest(UUID libraryItemId, String title, String description, JsonNode questions,
                                   Set<Integer> studentIds, Instant dueAt) {
        this(libraryItemId, title, description, questions, studentIds, dueAt, null, null, false);
    }
}
