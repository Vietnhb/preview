package com.example.backend.system.problem.dto;

import com.example.backend.system.problem.model.enums.SourceMode;
import com.example.backend.system.problem.model.enums.SubmissionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProblemResponse(
        UUID id,
        Integer ownerId,
        UUID lessonId,
        SourceMode sourceMode,
        String originalText,
        String editableText,
        SubmissionStatus status,
        SpecificationResponse currentSpecification,
        List<SourceAssetResponse> sourceAssets,
        List<ExtractionRunResponse> extractionRuns,
        Instant createdAt,
        Instant updatedAt) {
}
