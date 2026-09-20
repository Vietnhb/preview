package com.example.backend.schema.routing.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bounded route decision. Candidate identities stay pinned through extraction retries. */
public record SchemaRoutingDecision(
        Status status,
        String reasonCode,
        List<SchemaCandidate> candidates,
        double confidence,
        double margin) {
    public SchemaRoutingDecision {
        Objects.requireNonNull(status, "status");
        if (reasonCode == null || reasonCode.isBlank()) throw new IllegalArgumentException("reasonCode is required");
        candidates = List.copyOf(candidates);
        if (candidates.isEmpty()) throw new IllegalArgumentException("At least one candidate must be pinned");
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1
                || !Double.isFinite(margin) || margin < 0 || margin > 1) {
            throw new IllegalArgumentException("Decision confidence and margin must be in [0, 1]");
        }
    }

    public Optional<SchemaCandidate> selectedCandidate() {
        return status == Status.SELECTED ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    public enum Status { SELECTED, AMBIGUOUS }
}
