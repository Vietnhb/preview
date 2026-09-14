package com.example.backend.dto.assignment;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AssignmentSubmissionResponse(UUID id, UUID assignmentId, Integer studentId,
                                           String studentName, JsonNode predictions, Instant submittedAt) {
}
