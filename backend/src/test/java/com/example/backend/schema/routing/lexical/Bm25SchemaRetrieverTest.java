package com.example.backend.schema.routing.lexical;

import com.example.backend.schema.routing.model.SchemaSearchDocument;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bm25SchemaRetrieverTest {
    @Test
    void scoresAHandComputedBm25Fixture() {
        Bm25SchemaRetriever retriever = new Bm25SchemaRetriever(1.2, 0.75);
        List<SchemaSearchDocument> corpus = List.of(
                document("mass-repeat", "1", "mass mass"),
                document("mass-once", "1", "mass speed"));

        Bm25SchemaRetriever.Index index = retriever.buildIndex(corpus);
        List<Bm25SchemaRetriever.RankedDocument> ranked = retriever.rank("mass", index, 10);

        double idf = Math.log(1.2);
        assertEquals(2, ranked.size());
        assertEquals("mass-repeat", ranked.get(0).document().schemaId());
        assertEquals(idf * 1.375, ranked.get(0).score(), 1.0e-12);
        assertEquals("mass-once", ranked.get(1).document().schemaId());
        assertEquals(idf, ranked.get(1).score(), 1.0e-12);
        assertEquals(1, ranked.get(0).rank());
        assertEquals(2, ranked.get(1).rank());
    }

    @Test
    void matchesThePromptHandCalculationWhenOnlyOneDocumentContainsTheQueryTerm() {
        Bm25SchemaRetriever retriever = new Bm25SchemaRetriever(1.2, 0.75);
        Bm25SchemaRetriever.Index index = retriever.buildIndex(List.of(
                document("alpha-document", "1", "alpha alpha"),
                document("beta-document", "1", "beta beta")));

        List<Bm25SchemaRetriever.RankedDocument> ranked = retriever.rank("alpha", index, 10);

        double expected = 1.375 * Math.log(2.0);
        assertEquals(1, ranked.size());
        assertEquals("alpha-document", ranked.getFirst().document().schemaId());
        assertEquals(expected, ranked.getFirst().score(), 1.0e-12);
        assertEquals(1, ranked.getFirst().rank());
    }

    @Test
    void breaksEqualScoresBySchemaIdThenVersion() {
        Bm25SchemaRetriever retriever = new Bm25SchemaRetriever(1.2, 0.75);
        List<SchemaSearchDocument> corpus = List.of(
                document("zeta", "1", "mass"),
                document("alpha", "2", "mass"),
                document("alpha", "1", "mass"));

        List<Bm25SchemaRetriever.RankedDocument> ranked = retriever.rank("mass", corpus, 10);

        assertEquals(List.of("alpha@1", "alpha@2", "zeta@1"), ranked.stream()
                .map(item -> item.document().schemaId() + "@" + item.document().schemaVersion())
                .toList());
    }

    @Test
    void validatesRankingConfigurationAndInput() {
        assertThrows(IllegalArgumentException.class, () -> new Bm25SchemaRetriever(0.0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new Bm25SchemaRetriever(1.2, 1.1));
        assertThrows(IllegalArgumentException.class,
                () -> new Bm25SchemaRetriever(1.2, 0.75).rank("mass", List.of(), 0));
    }

    @Test
    void doesNotReturnZeroScoreDocuments() {
        List<Bm25SchemaRetriever.RankedDocument> ranked = new Bm25SchemaRetriever(1.2, 0.75)
                .rank("mass", List.of(document("other", "1", "speed")), 5);

        assertTrue(ranked.isEmpty());
    }

    @Test
    void appliesGlobalStopWordsButKeepsPhysicsSymbolsAndUnitsSearchable() {
        Bm25SchemaRetriever retriever = new Bm25SchemaRetriever(1.2, 0.75);
        List<SchemaSearchDocument> corpus = List.of(
                document("physics", "1", "the voltage is 2 V"),
                document("other", "1", "the current is 2 A"));

        assertTrue(retriever.rank("the is", retriever.buildIndex(corpus), 10).isEmpty());
        assertEquals("physics", retriever.rank("V", retriever.buildIndex(corpus), 10).getFirst().document().schemaId());
    }

    @Test
    void replacementIndexDoesNotMutateThePreviousSnapshot() {
        Bm25SchemaRetriever retriever = new Bm25SchemaRetriever(1.2, 0.75);
        List<SchemaSearchDocument> source = new ArrayList<>(List.of(document("mass-v1", "1", "mass")));
        Bm25SchemaRetriever.Index original = retriever.buildIndex(source);

        source.clear();
        source.add(document("speed-v1", "1", "speed"));
        Bm25SchemaRetriever.Index replacement = retriever.buildIndex(source);

        assertEquals(1, original.documentCount());
        assertEquals(1, replacement.documentCount());
        assertEquals(List.of("mass-v1"), retriever.rank("mass", original, 10).stream()
                .map(item -> item.document().schemaId()).toList());
        assertTrue(retriever.rank("mass", replacement, 10).isEmpty());
    }

    private static SchemaSearchDocument document(String id, String version, String text) {
        return new SchemaSearchDocument(id, version, "mechanics", id, "model", text, "checksum-" + id + version);
    }
}
