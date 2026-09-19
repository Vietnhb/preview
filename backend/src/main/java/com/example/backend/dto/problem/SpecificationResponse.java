package com.example.backend.dto.problem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.backend.entity.enums.ConfirmationState;
import com.fasterxml.jackson.databind.JsonNode;

public record SpecificationResponse(
        UUID id,
        String contractVersion,
        String schemaVersion,
        String topic,
        BigDecimal confidence,
        JsonNode objects,
        JsonNode quantities,
        JsonNode relations,
        JsonNode endCondition,
        JsonNode ambiguity,
        ConfirmationState confirmationState,
        List<AmbiguityResponse> ambiguityCases,
        String schemaId,
        String validationStatus,
        JsonNode validationResult,
        Instant createdAt) {
}
