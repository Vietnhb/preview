package com.example.backend.simulation.assets;

import java.util.List;

/** Catalog candidates judged by JEV against the original text, before entity extraction. */
public record AssetRoutingDecision(String catalogChecksum, List<Candidate> candidates) {
    public AssetRoutingDecision {
        if (catalogChecksum == null || catalogChecksum.isBlank()) {
            throw new IllegalArgumentException("Asset catalog checksum is required");
        }
        candidates = List.copyOf(candidates);
        if (candidates.stream().map(Candidate::assetId).distinct().count() != candidates.size()) {
            throw new IllegalArgumentException("Duplicate routed asset ID");
        }
    }

    public record Candidate(String assetId, String match, double confidence) {
        public Candidate {
            if (assetId == null || assetId.isBlank()
                    || !("EXACT".equals(match) || "SUBSTITUTE".equals(match))) {
                throw new IllegalArgumentException("Invalid routed asset candidate");
            }
            if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                throw new IllegalArgumentException("Asset confidence must be in [0, 1]");
            }
        }
    }
}
