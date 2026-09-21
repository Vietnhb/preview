package com.example.backend.schema.routing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.config.properties.SchemaRoutingProperties;
import com.example.backend.schema.routing.index.IndexedSchemaCandidate;
import com.example.backend.schema.routing.index.SchemaSearchIndex;
import com.example.backend.schema.routing.lexical.Bm25SchemaRetriever;
import com.example.backend.schema.routing.model.SchemaSearchDocument;
import com.example.backend.schema.routing.model.SchemaIdentity;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.example.backend.schema.routing.vector.EmbeddingClient;
import com.example.backend.schema.routing.vector.EmbeddingResult;
import com.example.backend.schema.routing.vector.SchemaVectorRetriever;
import com.example.backend.schema.routing.verification.SchemaContractReranker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class SchemaRoutingServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void lowMarginReturnsPinnedAmbiguousCandidatesInsteadOfGuessing() throws Exception {
        IndexedSchemaCandidate first = candidate("mechanics_a", "2.1", "Mechanics A");
        IndexedSchemaCandidate second = candidate("mechanics_b", "1.4", "Mechanics B");
        List<SchemaSearchDocument> documents = List.of(first.document(), second.document());
        SchemaSearchIndex index = new SchemaSearchIndex();
        index.replace(List.of(first, second), new Bm25SchemaRetriever(1.2, 0.75).buildIndex(documents));

        EmbeddingClient embeddings = new EmbeddingClient() {
            @Override public EmbeddingResult embed(String text) { return new EmbeddingResult(List.of(0.25, 0.75)); }
            @Override public String providerId() { return "fake"; }
            @Override public String modelId() { return "fixture-v1"; }
            @Override public int dimension() { return 2; }
        };
        SchemaVectorRetriever vector = (query, provider, model, dimension, topK, eligibleSchemaVersions) -> {
            assertEquals(Set.of(new SchemaIdentity("mechanics_a", "2.1"),
                    new SchemaIdentity("mechanics_b", "1.4")), Set.copyOf(eligibleSchemaVersions));
            return List.of(
                    new SchemaVectorRetriever.RankedVector("mechanics_a", "2.1", "DYNAMICS", 0.9, 1),
                    new SchemaVectorRetriever.RankedVector("mechanics_b", "1.4", "DYNAMICS", 0.9, 2));
        };
        SchemaRoutingProperties properties = new SchemaRoutingProperties(true, 20, 20, 2, 60,
                0.25, 0.08, 20_000, 30_000, 64, 1.2, 0.75, 0.15,
                0.55, 0.25, 0.20, 0.75,
                new SchemaRoutingProperties.Embedding("fake", "fixture-v1", 2, Duration.ofSeconds(2)));
        SchemaContractReranker reranker = new SchemaContractReranker(new UnitNormalizer(mapper), properties);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();

        SchemaRoutingDecision decision = new SchemaRoutingService(index, embeddings, vector, reranker, properties, meters)
                .route("mass distance");

        assertEquals(SchemaRoutingDecision.Status.AMBIGUOUS, decision.status());
        assertEquals("INSUFFICIENT_CANDIDATE_MARGIN", decision.reasonCode());
        assertEquals(List.of("mechanics_a@2.1", "mechanics_b@1.4"), decision.candidates().stream()
                .map(item -> item.schemaId() + "@" + item.schemaVersion()).toList());
        assertEquals(1, meters.timer("physlive.schema.routing.lexical").count());
        assertEquals(2, meters.summary("physlive.schema.routing.candidate_count").totalAmount());
        assertEquals(1, meters.counter("physlive.schema.routing.ambiguous", "reason",
                "INSUFFICIENT_CANDIDATE_MARGIN").count());
    }

    @Test
    void aSingleWeakCandidateStillReturnsAmbiguity() throws Exception {
        IndexedSchemaCandidate only = candidate("mechanics_a", "2.1", "Mechanics A");
        SchemaSearchIndex index = new SchemaSearchIndex();
        index.replace(List.of(only), new Bm25SchemaRetriever(1.2, 0.75).buildIndex(List.of(only.document())));
        EmbeddingClient embeddings = new EmbeddingClient() {
            @Override public EmbeddingResult embed(String text) { return new EmbeddingResult(List.of(0.25, 0.75)); }
            @Override public String providerId() { return "fake"; }
            @Override public String modelId() { return "fixture-v1"; }
            @Override public int dimension() { return 2; }
        };
        SchemaVectorRetriever vector = (query, provider, model, dimension, topK, eligibleSchemaVersions) ->
                List.of(new SchemaVectorRetriever.RankedVector("mechanics_a", "2.1", "DYNAMICS", 0.9, 1));
        SchemaRoutingProperties properties = new SchemaRoutingProperties(true, 20, 20, 1, 60,
                0.25, 0.08, 20_000, 30_000, 64, 1.2, 0.75, 0.15,
                0.55, 0.25, 0.20, 0.75,
                new SchemaRoutingProperties.Embedding("fake", "fixture-v1", 2, Duration.ofSeconds(2)));
        SchemaContractReranker reranker = new SchemaContractReranker(new UnitNormalizer(mapper), properties);

        SchemaRoutingDecision decision = new SchemaRoutingService(index, embeddings, vector, reranker, properties)
                .route("unrelated words with no physics quantities");

        assertEquals(SchemaRoutingDecision.Status.AMBIGUOUS, decision.status());
        assertEquals("INSUFFICIENT_CONTRACT_EVIDENCE", decision.reasonCode());
        assertEquals(List.of("mechanics_a@2.1"), decision.candidates().stream()
                .map(item -> item.schemaId() + "@" + item.schemaVersion()).toList());
    }

    @Test
    void unitOnlyOverlapCannotProduceAConfidentSelection() throws Exception {
        IndexedSchemaCandidate source = candidate("mechanics_a", "2.1", "Mechanics A");
        IndexedSchemaCandidate only = new IndexedSchemaCandidate(new SchemaSearchDocument(
                "mechanics_a", "2.1", "DYNAMICS", "Mechanics A", "mechanics_family",
                "mechanics family", "checksum-mechanics_a"), source.contract());
        SchemaSearchIndex index = new SchemaSearchIndex();
        index.replace(List.of(only), new Bm25SchemaRetriever(1.2, 0.75).buildIndex(List.of(only.document())));
        EmbeddingClient embeddings = new EmbeddingClient() {
            @Override public EmbeddingResult embed(String text) { return new EmbeddingResult(List.of(0.25, 0.75)); }
            @Override public String providerId() { return "fake"; }
            @Override public String modelId() { return "fixture-v1"; }
            @Override public int dimension() { return 2; }
        };
        SchemaVectorRetriever vector = (query, provider, model, dimension, topK, eligibleSchemaVersions) ->
                List.of(new SchemaVectorRetriever.RankedVector("mechanics_a", "2.1", "DYNAMICS", 0.9, 1));
        SchemaRoutingProperties properties = new SchemaRoutingProperties(true, 20, 20, 1, 60,
                0.25, 0.08, 20_000, 30_000, 64, 1.2, 0.75, 0.15,
                0.55, 0.25, 0.20, 0.75,
                new SchemaRoutingProperties.Embedding("fake", "fixture-v1", 2, Duration.ofSeconds(2)));

        SchemaRoutingDecision decision = new SchemaRoutingService(index, embeddings, vector,
                new SchemaContractReranker(new UnitNormalizer(mapper), properties), properties)
                .route("12 kg");

        assertEquals(SchemaRoutingDecision.Status.AMBIGUOUS, decision.status());
        assertEquals("UNIT_ONLY_EVIDENCE", decision.reasonCode());
    }

    @Test
    void noRetrieverHitProducesBoundedExplicitAmbiguity() throws Exception {
        IndexedSchemaCandidate only = candidate("mechanics_a", "2.1", "Mechanics A");
        SchemaSearchIndex index = new SchemaSearchIndex();
        index.replace(List.of(only), new Bm25SchemaRetriever(1.2, 0.75).buildIndex(List.of(only.document())));
        EmbeddingClient embeddings = new EmbeddingClient() {
            @Override public EmbeddingResult embed(String text) { return new EmbeddingResult(List.of(0.25, 0.75)); }
            @Override public String providerId() { return "fake"; }
            @Override public String modelId() { return "fixture-v1"; }
            @Override public int dimension() { return 2; }
        };
        SchemaRoutingProperties properties = new SchemaRoutingProperties(true, 20, 20, 1, 60,
                0.25, 0.08, 20_000, 30_000, 64, 1.2, 0.75, 0.15,
                0.55, 0.25, 0.20, 0.75,
                new SchemaRoutingProperties.Embedding("fake", "fixture-v1", 2, Duration.ofSeconds(2)));

        SchemaRoutingDecision decision = new SchemaRoutingService(index, embeddings,
                (query, provider, model, dimension, topK, eligible) -> List.of(),
                new SchemaContractReranker(new UnitNormalizer(mapper), properties), properties)
                .route("unrelated words");

        assertEquals(SchemaRoutingDecision.Status.AMBIGUOUS, decision.status());
        assertEquals("NO_RETRIEVAL_EVIDENCE", decision.reasonCode());
        assertEquals(1, decision.candidates().size());
    }

    @Test
    void repeatedVectorIdentityIsDeduplicatedBeforeCandidateVerification() throws Exception {
        IndexedSchemaCandidate only = candidate("mechanics_a", "2.1", "Mechanics A");
        SchemaSearchIndex index = new SchemaSearchIndex();
        index.replace(List.of(only), new Bm25SchemaRetriever(1.2, 0.75).buildIndex(List.of(only.document())));
        EmbeddingClient embeddings = new EmbeddingClient() {
            @Override public EmbeddingResult embed(String text) { return new EmbeddingResult(List.of(0.25, 0.75)); }
            @Override public String providerId() { return "fake"; }
            @Override public String modelId() { return "fixture-v1"; }
            @Override public int dimension() { return 2; }
        };
        SchemaVectorRetriever vector = (query, provider, model, dimension, topK, eligibleSchemaVersions) ->
                List.of(
                        new SchemaVectorRetriever.RankedVector("mechanics_a", "2.1", "DYNAMICS", 0.9, 1),
                        new SchemaVectorRetriever.RankedVector("mechanics_a", "2.1", "DYNAMICS", 0.8, 2));
        SchemaRoutingProperties properties = new SchemaRoutingProperties(true, 20, 20, 1, 60,
                0.25, 0.08, 20_000, 30_000, 64, 1.2, 0.75, 0.15,
                0.55, 0.25, 0.20, 0.75,
                new SchemaRoutingProperties.Embedding("fake", "fixture-v1", 2, Duration.ofSeconds(2)));
        SchemaContractReranker reranker = new SchemaContractReranker(new UnitNormalizer(mapper), properties);

        SchemaRoutingDecision decision = new SchemaRoutingService(index, embeddings, vector, reranker, properties)
                .route("mass distance");

        assertEquals(SchemaRoutingDecision.Status.SELECTED, decision.status());
        assertEquals(List.of("mechanics_a@2.1"), decision.candidates().stream()
                .map(item -> item.schemaId() + "@" + item.schemaVersion()).toList());
    }

    private IndexedSchemaCandidate candidate(String id, String version, String name) throws Exception {
        JsonNode definition = mapper.readTree("""
                {"model":"mechanics_family","requiredQuantities":[
                 {"key":"mass","aliases":["m"],"allowedUnits":["kg"]},
                 {"key":"distance","aliases":["d"],"allowedUnits":["m"]}],"optionalQuantities":[]}
                """);
        CandidateContractProjection contract = CandidateContractProjection.from(id, version, "DYNAMICS", name, definition);
        SchemaSearchDocument document = new SchemaSearchDocument(id, version, "DYNAMICS", name,
                "mechanics_family", "distance mass kg mechanics", "checksum-" + id);
        return new IndexedSchemaCandidate(document, contract);
    }
}
