package com.example.backend.schema.routing.fusion;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReciprocalRankFusionTest {
    @Test
    void computesHandCalculatedRrfWithCandidatesMissingFromSomeRankers() {
        ReciprocalRankFusion fusion = new ReciprocalRankFusion(60);
        Map<String, List<ReciprocalRankFusion.RankedItem>> rankings = Map.of(
                "lexical", List.of(item("alpha", "1", 1), item("beta", "1", 2)),
                "vector", List.of(item("beta", "1", 1), item("gamma", "1", 2)));

        List<ReciprocalRankFusion.FusedCandidate> fused = fusion.fuse(rankings, 10);

        assertEquals(List.of("beta", "alpha", "gamma"), fused.stream()
                .map(ReciprocalRankFusion.FusedCandidate::schemaId).toList());
        assertEquals(1.0 / 61.0 + 1.0 / 62.0, fused.get(0).rrfScore(), 1.0e-15);
        assertEquals(1.0 / 61.0, fused.get(1).rrfScore(), 1.0e-15);
        assertEquals(1.0 / 62.0, fused.get(2).rrfScore(), 1.0e-15);
    }

    @Test
    void matchesThePromptRrfFixtureWithOneCandidateMissingFromBothRankPositions() {
        ReciprocalRankFusion fusion = new ReciprocalRankFusion(60);
        Map<String, List<ReciprocalRankFusion.RankedItem>> rankings = Map.of(
                "lexical", List.of(item("alpha", "1", 1), item("beta", "1", 2)),
                "vector", List.of(item("beta", "1", 1), item("alpha", "1", 2), item("gamma", "1", 3)));

        List<ReciprocalRankFusion.FusedCandidate> fused = fusion.fuse(rankings, 10);

        double shared = 1.0 / 61.0 + 1.0 / 62.0;
        assertEquals(List.of("alpha", "beta", "gamma"), fused.stream()
                .map(ReciprocalRankFusion.FusedCandidate::schemaId).toList());
        assertEquals(shared, fused.get(0).rrfScore(), 1.0e-15);
        assertEquals(shared, fused.get(1).rrfScore(), 1.0e-15);
        assertEquals(1.0 / 63.0, fused.get(2).rrfScore(), 1.0e-15);
    }

    @Test
    void deterministicallyBreaksTiesBySchemaIdAndVersionRegardlessOfMapOrder() {
        ReciprocalRankFusion fusion = new ReciprocalRankFusion(60);
        Map<String, List<ReciprocalRankFusion.RankedItem>> firstOrder = new LinkedHashMap<>();
        firstOrder.put("z-vector", List.of(item("zeta", "1", 1), item("alpha", "2", 2)));
        firstOrder.put("a-lexical", List.of(item("alpha", "2", 1), item("zeta", "1", 2)));
        Map<String, List<ReciprocalRankFusion.RankedItem>> reverseOrder = new LinkedHashMap<>();
        reverseOrder.put("a-lexical", firstOrder.get("a-lexical"));
        reverseOrder.put("z-vector", firstOrder.get("z-vector"));

        List<ReciprocalRankFusion.FusedCandidate> first = fusion.fuse(firstOrder, 10);
        List<ReciprocalRankFusion.FusedCandidate> second = fusion.fuse(reverseOrder, 10);

        assertEquals(List.of("alpha@2", "zeta@1"), first.stream()
                .map(item -> item.schemaId() + "@" + item.schemaVersion()).toList());
        assertEquals(first, second);
    }

    @Test
    void validatesFusionConfigurationAndRankOrder() {
        assertThrows(IllegalArgumentException.class, () -> new ReciprocalRankFusion(0));
        assertThrows(IllegalArgumentException.class, () -> new ReciprocalRankFusion(60)
                .fuse(Map.of("lexical", List.of(item("alpha", "1", 2), item("beta", "1", 1))), 10));
    }

    @Test
    void deduplicatesRepeatedIdentityWithinOneRetrieverUsingBestRank() {
        ReciprocalRankFusion fusion = new ReciprocalRankFusion(60);
        List<ReciprocalRankFusion.FusedCandidate> fused = fusion.fuse(Map.of(
                "vector", List.of(item("alpha", "1", 1), item("alpha", "1", 2), item("beta", "1", 3))), 10);

        assertEquals(List.of("alpha", "beta"), fused.stream()
                .map(ReciprocalRankFusion.FusedCandidate::schemaId).toList());
        assertEquals(1.0 / 61.0, fused.getFirst().rrfScore(), 1.0e-15);
    }

    private static ReciprocalRankFusion.RankedItem item(String id, String version, int rank) {
        return new ReciprocalRankFusion.RankedItem(id, version, rank);
    }
}
