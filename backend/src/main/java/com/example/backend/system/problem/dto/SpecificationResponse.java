package com.example.backend.system.problem.dto;

import com.example.backend.system.problem.model.enums.ConfirmationState;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        List<String> endConditionCapabilities,
        JsonNode ambiguity,
        ConfirmationState confirmationState,
        List<AmbiguityContracts.Item> ambiguityCases,
        String schemaId,
        String validationStatus,
        JsonNode validationResult,
        Instant createdAt) {
}
