package com.example.backend.dto.simulation;

import java.time.Instant;
import java.util.UUID;

/** Lightweight history row used by the empty workspace resource list. */
public record SimulationSummaryResponse(
        UUID simulationId,
        UUID specificationId,
        String schemaId,
        String status,
        Instant createdAt) {
}
