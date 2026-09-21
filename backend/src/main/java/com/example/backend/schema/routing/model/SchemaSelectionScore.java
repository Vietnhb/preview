package com.example.backend.schema.routing.model;

/** Jev selection evidence retained for audit; no vector or lexical retrieval is involved. */
public record SchemaSelectionScore(int rank, double probability) {
    public SchemaSelectionScore {
        if (rank < 1) throw new IllegalArgumentException("Selection rank must be positive");
        if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Selection probability must be in [0, 1]");
        }
    }
}
