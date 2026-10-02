package com.example.backend.system.problem.dto;

import com.example.backend.system.problem.model.enums.SourceMode;
import com.example.backend.system.problem.model.enums.SubmissionStatus;
import java.time.Instant;
import java.util.UUID;

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
