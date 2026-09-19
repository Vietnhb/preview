package com.example.backend.dto.problem;

import java.time.Instant;
import java.util.UUID;

import com.example.backend.enums.SourceMode;
import com.example.backend.enums.SubmissionStatus;

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
