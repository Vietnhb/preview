package com.example.backend.system.problem.dto;

import com.example.backend.system.problem.model.entity.Specification;
import com.example.backend.system.problem.model.enums.AmbiguityStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Specification questions and reviewer queue contracts. */
public final class AmbiguityContracts {
    private AmbiguityContracts() { }
    public record Answer(String answer, String comment) { }
    public record Item(UUID id, String code, String fieldPath, String question, JsonNode options,
                       AmbiguityStatus status, String resolution, Instant resolvedAt) { }
    public record Review(UUID id, UUID specificationId, String code, String fieldPath, String question,
                         JsonNode options, AmbiguityStatus status, String problemText, String topic,
                         JsonNode quantities, JsonNode relations, Integer claimedBy,
                         Instant claimedAt, Instant claimExpiresAt) { }
    public record Page(List<Review> items, int page, int size, long totalElements, int totalPages) { }
}
