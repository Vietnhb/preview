package com.example.backend.dto.problem;

import java.time.Instant;
import java.util.UUID;

import com.example.backend.enums.ExtractionOutcome;
import com.example.backend.enums.ExtractionPath;
import com.example.backend.enums.ExtractionRunStatus;

public record ExtractionRunResponse(
        UUID id,
        ExtractionPath extractionPath,
        String providerName,
        String modelVersion,
        ExtractionRunStatus status,
        ExtractionOutcome outcome,
        String errorMessage,
        Instant createdAt) {
}
