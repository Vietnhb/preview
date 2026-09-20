package com.example.backend.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Validated runtime limits for schema retrieval and candidate projection. */
@ConfigurationProperties(prefix = "physlive.schema-routing")
public record SchemaRoutingProperties(
        boolean enabled,
        int lexicalTopK,
        int vectorTopK,
        int candidateTopK,
        int rrfK,
        double minimumScore,
        double minimumMargin,
        int maximumQueryCharacters,
        int maximumPromptCharacters,
        int quantityAssociationWindowCharacters,
        double bm25K1,
        double bm25B,
        double minimumEvidenceScore,
        double requiredQuantityWeight,
        double unitCompatibilityWeight,
        double metadataWeight,
        double contradictionPenaltyWeight,
        Embedding embedding) {

    public SchemaRoutingProperties {
        if (lexicalTopK < 1 || lexicalTopK > 500) throw invalid("lexical-top-k must be in [1, 500]");
        if (vectorTopK < 1 || vectorTopK > 500) throw invalid("vector-top-k must be in [1, 500]");
        if (candidateTopK < 1 || candidateTopK > 20) throw invalid("candidate-top-k must be in [1, 20]");
        if (candidateTopK > lexicalTopK || candidateTopK > vectorTopK) {
            throw invalid("candidate-top-k cannot exceed either retrieval top-k");
        }
        if (rrfK < 1 || rrfK > 10_000) throw invalid("rrf-k must be in [1, 10000]");
        if (!unitInterval(minimumScore)) throw invalid("minimum-score must be in [0, 1]");
        if (!unitInterval(minimumMargin)) throw invalid("minimum-margin must be in [0, 1]");
        if (maximumQueryCharacters < 1 || maximumQueryCharacters > 100_000) {
            throw invalid("maximum-query-characters must be in [1, 100000]");
        }
        if (maximumPromptCharacters < 1_000 || maximumPromptCharacters > 100_000) {
            throw invalid("maximum-prompt-characters must be in [1000, 100000]");
        }
        if (quantityAssociationWindowCharacters < 1 || quantityAssociationWindowCharacters > 1_000) {
            throw invalid("quantity-association-window-characters must be in [1, 1000]");
        }
        if (!Double.isFinite(bm25K1) || bm25K1 <= 0) throw invalid("bm25-k1 must be finite and positive");
        if (!Double.isFinite(bm25B) || bm25B < 0 || bm25B > 1) throw invalid("bm25-b must be in [0, 1]");
        if (!unitInterval(minimumEvidenceScore)) {
            throw invalid("minimum evidence score must be in [0, 1]");
        }
        if (!unitInterval(requiredQuantityWeight) || !unitInterval(unitCompatibilityWeight) || !unitInterval(metadataWeight)
                || requiredQuantityWeight + unitCompatibilityWeight + metadataWeight <= 0) {
            throw invalid("verification weights must be in [0, 1] and at least one must be positive");
        }
        if (!unitInterval(contradictionPenaltyWeight)) {
            throw invalid("contradiction-penalty-weight must be in [0, 1]");
        }
        if (embedding == null) throw invalid("embedding configuration is required");
    }

    private static boolean unitInterval(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid physlive.schema-routing configuration: " + message);
    }

    public record Embedding(String provider, String model, int dimension, Duration timeout) {
        public Embedding {
            provider = provider == null ? "" : provider.trim();
            model = model == null ? "" : model.trim();
            if (provider.isEmpty()) throw invalid("embedding.provider is required");
            if (model.isEmpty()) throw invalid("embedding.model is required");
            if (dimension < 1 || dimension > 16_384) throw invalid("embedding.dimension must be in [1, 16384]");
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw invalid("embedding.timeout must be positive");
            }
        }
    }
}
