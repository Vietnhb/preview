package com.example.backend.schema.routing.model;

/** Raw heterogeneous retrieval evidence is preserved for auditing; decisions use rank fusion. */
public record RetrievalScore(
        int lexicalRank,
        double lexicalScore,
        int vectorRank,
        double vectorSimilarity,
        double rrfScore) {
    public RetrievalScore {
        if (lexicalRank < 0 || vectorRank < 0) throw new IllegalArgumentException("Retrieval ranks cannot be negative");
        if (!Double.isFinite(lexicalScore) || lexicalScore < 0
                || !Double.isFinite(vectorSimilarity) || !Double.isFinite(rrfScore) || rrfScore < 0) {
            throw new IllegalArgumentException("Retrieval scores must be finite and non-negative except cosine similarity");
        }
    }
}
