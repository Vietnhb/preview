package com.example.backend.schema.routing.fusion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic Reciprocal Rank Fusion for rankings from independent retrievers. */
public final class ReciprocalRankFusion {
    private final int k;

    public ReciprocalRankFusion(int k) {
        if (k < 1 || k > 10_000) {
            throw new IllegalArgumentException("RRF k must be in [1, 10000]");
        }
        this.k = k;
    }

    /**
     * Fuses named retriever rankings. Retriever names are used only for deterministic iteration;
     * each ranking contributes {@code 1 / (k + rank)} for every candidate it contains.
     */
    public List<FusedCandidate> fuse(
            Map<String, ? extends List<RankedItem>> rankings,
            int topK) {
        Objects.requireNonNull(rankings, "rankings");
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be at least 1");
        }

        List<String> retrieverNames = new ArrayList<>(rankings.keySet());
        if (retrieverNames.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("rankings must not contain a null retriever name");
        }
        retrieverNames.sort(String::compareTo);

        Map<Identity, Double> scores = new HashMap<>();
        for (String retrieverName : retrieverNames) {
            List<RankedItem> items = Objects.requireNonNull(rankings.get(retrieverName),
                    "ranking list for " + retrieverName);
            Set<Identity> seen = new HashSet<>();
            int priorRank = 0;
            for (RankedItem item : items) {
                Objects.requireNonNull(item, "ranking must not contain null items");
                if (item.rank() <= priorRank) {
                    throw new IllegalArgumentException("ranks must be strictly increasing in "
                            + retrieverName);
                }
                priorRank = item.rank();
                Identity identity = new Identity(item.schemaId(), item.schemaVersion());
                if (!seen.add(identity)) {
                    throw new IllegalArgumentException("duplicate candidate in ranking "
                            + retrieverName + ": " + item.schemaId() + "@" + item.schemaVersion());
                }
                scores.merge(identity, 1.0 / (k + (double) item.rank()), Double::sum);
            }
        }

        List<FusedCandidate> candidates = scores.entrySet().stream()
                .map(entry -> new FusedCandidate(
                        entry.getKey().schemaId,
                        entry.getKey().schemaVersion,
                        entry.getValue()))
                .sorted(Comparator.comparingDouble(FusedCandidate::rrfScore).reversed()
                        .thenComparing(FusedCandidate::schemaId)
                        .thenComparing(FusedCandidate::schemaVersion))
                .toList();
        return List.copyOf(candidates.subList(0, Math.min(topK, candidates.size())));
    }

    public record RankedItem(String schemaId, String schemaVersion, int rank) {
        public RankedItem {
            schemaId = requireText(schemaId, "schemaId");
            schemaVersion = requireText(schemaVersion, "schemaVersion");
            if (rank < 1) {
                throw new IllegalArgumentException("rank must be at least 1");
            }
        }
    }

    public record FusedCandidate(String schemaId, String schemaVersion, double rrfScore) {
        public FusedCandidate {
            schemaId = requireText(schemaId, "schemaId");
            schemaVersion = requireText(schemaVersion, "schemaVersion");
            if (!Double.isFinite(rrfScore) || rrfScore <= 0.0) {
                throw new IllegalArgumentException("rrfScore must be finite and positive");
            }
        }
    }

    private record Identity(String schemaId, String schemaVersion) {
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
