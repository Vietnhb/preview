package com.example.backend.dto.problem;

import java.time.Instant;
import java.util.UUID;

import com.example.backend.entity.SourceMode;
import com.example.backend.entity.SubmissionStatus;

public record ProblemSummaryResponse(
        UUID id,
        SourceMode sourceMode,
        SubmissionStatus status,
        String editableText,
        UUID lessonId,
        UUID currentSpecificationId,
        Instant createdAt,
        Instant updatedAt) {
}
