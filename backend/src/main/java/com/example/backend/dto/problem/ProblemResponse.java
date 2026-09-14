package com.example.backend.dto.problem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.backend.entity.SourceMode;
import com.example.backend.entity.SubmissionStatus;

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
