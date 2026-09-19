package com.example.backend.dto.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.backend.entity.AssignmentStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;
import com.example.backend.entity.GradingStatus;

public record AssignmentResponse(
        UUID id,
        UUID libraryItemId,
        UUID specificationId,
        UUID simulationRunId,
        String title,
        String description,
        JsonNode questions,
        Set<Integer> studentIds,
        AssignmentStatus status,
        Instant assignedAt,
        Instant dueAt,
        boolean predictionSubmitted,
        JsonNode gradingCriteria,
        BigDecimal maxScore,
        boolean autoGrade,
        BigDecimal score,
        String feedback,
        GradingStatus gradingStatus,
        boolean retryAllowed) {
}
