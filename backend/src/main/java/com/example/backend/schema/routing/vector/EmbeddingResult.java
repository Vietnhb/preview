package com.example.backend.schema.routing.vector;

import java.util.List;

/** Provider-neutral embedding value. Never carries source text or provider SDK types. */
public record EmbeddingResult(List<Double> values) {
    public EmbeddingResult {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("Embedding vector is required");
        values = List.copyOf(values);
        double squaredNorm = 0.0;
        for (Double value : values) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("Embedding vector values must be finite");
            }
            squaredNorm += value * value;
            if (!Double.isFinite(squaredNorm)) {
                throw new IllegalArgumentException("Embedding vector norm must be finite");
            }
        }
        if (squaredNorm == 0.0) {
            throw new IllegalArgumentException("Embedding vector norm must be greater than zero for cosine retrieval");
        }
    }
}
