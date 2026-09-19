package com.example.backend.dto.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.backend.entity.enums.AssignmentStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;
import com.example.backend.entity.enums.GradingStatus;

public record AssignmentResponse(
        UUID id,
        UUID libraryItemId,
        String libraryItemTitle,
        UUID classId,
        String className,
        Integer classGradeLevel,
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
        boolean submissionCompleted,
        Instant completedAt,
        JsonNode gradingCriteria,
        BigDecimal maxScore,
        boolean autoGrade,
        BigDecimal score,
        String feedback,
        GradingStatus gradingStatus,
        boolean retryAllowed,
        JsonNode predictions) {
}
