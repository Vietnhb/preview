package com.example.backend.dto.problem;

import java.time.Instant;
import java.util.UUID;

import com.example.backend.entity.enums.AmbiguityStatus;
import com.fasterxml.jackson.databind.JsonNode;

public record AmbiguityResponse(
        UUID id,
        String code,
        String fieldPath,
        String question,
        JsonNode options,
        AmbiguityStatus status,
        String resolution,
        Instant resolvedAt) {
}
