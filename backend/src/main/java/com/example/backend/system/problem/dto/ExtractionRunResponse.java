package com.example.backend.system.problem.dto;

import com.example.backend.system.problem.model.enums.ExtractionOutcome;
import com.example.backend.system.problem.model.enums.ExtractionPath;
import com.example.backend.system.problem.model.enums.ExtractionRunStatus;
import java.time.Instant;
import java.util.UUID;

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
