package com.example.backend.dto.reviewer;

import com.example.backend.entity.enums.AmbiguityStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record ReviewerAmbiguityResponse(
        UUID id,
        UUID specificationId,
        String code,
        String fieldPath,
        String question,
        JsonNode options,
        AmbiguityStatus status,
        String problemText, String topic, com.fasterxml.jackson.databind.JsonNode quantities,
        com.fasterxml.jackson.databind.JsonNode relations) {
}
