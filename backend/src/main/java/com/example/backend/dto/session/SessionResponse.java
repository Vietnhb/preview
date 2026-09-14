package com.example.backend.dto.session;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID specificationId,
        String title,
        JsonNode initialSpecification,
        JsonNode parameterTimeline,
        double durationSeconds,
        Instant createdAt) {
}
