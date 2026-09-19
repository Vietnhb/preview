package com.example.backend.dto.assignment;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;
import com.example.backend.entity.enums.GradingStatus;

public record AssignmentSubmissionResponse(UUID id, UUID assignmentId, Integer studentId,
                                           String studentName, JsonNode predictions, Instant submittedAt,
                                           BigDecimal score, BigDecimal maxScore, String feedback,
                                           GradingStatus gradingStatus, Instant gradedAt, boolean retryAllowed) {
}
