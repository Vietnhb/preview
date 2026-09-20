package com.example.backend.schema.routing.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import io.micrometer.core.instrument.MeterRegistry;

import com.example.backend.config.properties.SchemaRoutingProperties;
import com.example.backend.exception.EmbeddingUnavailableException;
import com.example.backend.exception.SchemaRoutingException;
import com.example.backend.schema.routing.fusion.ReciprocalRankFusion;
import com.example.backend.schema.routing.index.IndexedSchemaCandidate;
import com.example.backend.schema.routing.index.SchemaSearchIndex;
import com.example.backend.schema.routing.lexical.Bm25SchemaRetriever;
import com.example.backend.schema.routing.model.RetrievalScore;
import com.example.backend.schema.routing.model.SchemaCandidate;
import com.example.backend.schema.routing.model.SchemaCandidate.VerificationEvidence;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.example.backend.schema.routing.model.SchemaIdentity;
import com.example.backend.schema.routing.vector.EmbeddingClient;
import com.example.backend.schema.routing.vector.SchemaVectorRetriever;
import com.example.backend.schema.routing.verification.SchemaContractReranker;

@Service
public final class SchemaRoutingService {
    private final SchemaSearchIndex index;
    private final Bm25SchemaRetriever lexical;
    private final SchemaVectorRetriever vector;
    private final EmbeddingClient embeddings;
    private final SchemaContractReranker verifier;
    private final SchemaRoutingProperties properties;
    private final ReciprocalRankFusion fusion;
    private final MeterRegistry meters;

    public SchemaRoutingService(SchemaSearchIndex index, EmbeddingClient embeddings,
            SchemaVectorRetriever vector, SchemaContractReranker verifier,
            SchemaRoutingProperties properties) {
        this(index, embeddings, vector, verifier, properties, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    @Autowired
    public SchemaRoutingService(SchemaSearchIndex index, EmbeddingClient embeddings,
            SchemaVectorRetriever vector, SchemaContractReranker verifier,
            SchemaRoutingProperties properties, MeterRegistry meters) {
        this.index = index;
        this.embeddings = embeddings;
        this.vector = vector;
        this.verifier = verifier;
        this.properties = properties;
        this.meters = meters;
        this.lexical = new Bm25SchemaRetriever(properties.bm25K1(), properties.bm25B());
        this.fusion = new ReciprocalRankFusion(properties.rrfK());
    }

    public SchemaRoutingDecision route(String problemText) {
        if (!properties.enabled()) throw new EmbeddingUnavailableException("Enable physlive.schema-routing.enabled before accepting extraction requests.");
        if (!StringUtils.hasText(problemText)) throw new IllegalArgumentException("Problem text must not be blank");
        if (problemText.length() > properties.maximumQueryCharacters()) {
            throw new IllegalArgumentException("Problem text exceeds the configured schema-routing query limit");
        }
        String query = Normalizer.normalize(problemText.trim(), Normalizer.Form.NFKC);
        SchemaSearchIndex.Snapshot snapshot;
        try {
            snapshot = index.snapshot();
        } catch (RuntimeException failure) {
            throw new EmbeddingUnavailableException("The approved-schema search index is not ready; reindex schema embeddings.");
        }

        try {
            List<Bm25SchemaRetriever.RankedDocument> lexicalMatches = measure("physlive.schema.routing.lexical", () ->
                    lexical.rank(query, snapshot.bm25Index(), properties.lexicalTopK()));
            var queryVector = measure("physlive.schema.routing.embedding", () -> {
                try {
                    return embeddings.embed(query);
                } catch (RuntimeException failure) {
                    meters.counter("physlive.schema.routing.embedding.failures").increment();
                    throw failure;
                }
            });
            List<SchemaVectorRetriever.RankedVector> vectorMatches = measure("physlive.schema.routing.vector", () ->
                    vector.rank(queryVector, embeddings.providerId(), embeddings.modelId(), embeddings.dimension(),
                            properties.vectorTopK(), snapshot.byIdentity().keySet()));
            SchemaRoutingDecision decision = decide(query, snapshot, lexicalMatches, vectorMatches);
            meters.summary("physlive.schema.routing.candidate_count").record(decision.candidates().size());
            decision.selectedCandidate().ifPresent(selected -> {
                meters.counter("physlive.schema.routing.selected").increment();
                meters.summary("physlive.schema.routing.selected_rank").record(1);
                meters.summary("physlive.schema.routing.selected_lexical_rank").record(
                        Math.max(1, selected.retrievalScore().lexicalRank()));
                meters.summary("physlive.schema.routing.selected_vector_rank").record(
                        Math.max(1, selected.retrievalScore().vectorRank()));
            });
            if (decision.status() == SchemaRoutingDecision.Status.AMBIGUOUS) {
                meters.counter("physlive.schema.routing.ambiguous", "reason", decision.reasonCode()).increment();
            }
            return decision;
        } catch (EmbeddingUnavailableException failure) {
            meters.counter("physlive.schema.routing.failures").increment();
            throw failure;
        } catch (SchemaRoutingException failure) {
            meters.counter("physlive.schema.routing.failures").increment();
            throw failure;
        } catch (RuntimeException failure) {
            meters.counter("physlive.schema.routing.failures").increment();
            throw new EmbeddingUnavailableException("Check embedding provider/model configuration and PostgreSQL pgvector readiness.");
        }
    }

    private SchemaRoutingDecision decide(String query, SchemaSearchIndex.Snapshot snapshot,
            List<Bm25SchemaRetriever.RankedDocument> lexicalMatches,
            List<SchemaVectorRetriever.RankedVector> vectorMatches) {
        Map<String, List<ReciprocalRankFusion.RankedItem>> rankings = new LinkedHashMap<>();
        rankings.put("bm25", lexicalMatches.stream().map(item -> new ReciprocalRankFusion.RankedItem(
                item.document().schemaId(), item.document().schemaVersion(), item.rank())).toList());
        rankings.put("pgvector_cosine", vectorMatches.stream().map(item -> new ReciprocalRankFusion.RankedItem(
                item.schemaId(), item.schemaVersion(), item.rank())).toList());
        List<ReciprocalRankFusion.FusedCandidate> fused = measure("physlive.schema.routing.fusion",
                () -> fusion.fuse(rankings, properties.candidateTopK()));
        if (fused.isEmpty()) {
            throw new SchemaRoutingException("No approved schema candidates match this problem. Add relevant details or ask a teacher to choose a schema.");
        }

        Map<SchemaIdentity, Bm25SchemaRetriever.RankedDocument> lexicalByIdentity = new HashMap<>();
        lexicalMatches.forEach(item -> lexicalByIdentity.put(identity(item.document().schemaId(), item.document().schemaVersion()), item));
        Map<SchemaIdentity, SchemaVectorRetriever.RankedVector> vectorByIdentity = new HashMap<>();
        vectorMatches.forEach(item -> vectorByIdentity.put(identity(item.schemaId(), item.schemaVersion()), item));

        List<SchemaCandidate> candidates = new ArrayList<>();
        for (ReciprocalRankFusion.FusedCandidate item : fused) {
            var id = identity(item.schemaId(), item.schemaVersion());
            IndexedSchemaCandidate indexed = snapshot.byIdentity().get(id);
            if (indexed == null) continue;
            var lexicalEvidence = lexicalByIdentity.get(id);
            var vectorEvidence = vectorByIdentity.get(id);
            SchemaContractReranker.Result verified = verifier.verify(query, indexed);
            RetrievalScore retrieval = new RetrievalScore(
                    lexicalEvidence == null ? 0 : lexicalEvidence.rank(),
                    lexicalEvidence == null ? 0 : lexicalEvidence.score(),
                    vectorEvidence == null ? 0 : vectorEvidence.rank(),
                    vectorEvidence == null ? 0 : vectorEvidence.similarity(), item.rrfScore());
            candidates.add(new SchemaCandidate(indexed.contract(), retrieval, verified.evidence(), verified.confidence()));
        }
        candidates.sort(Comparator.comparingDouble(SchemaCandidate::confidence).reversed()
                .thenComparing(Comparator.comparingDouble((SchemaCandidate candidate) -> candidate.retrievalScore().rrfScore()).reversed())
                .thenComparing(SchemaCandidate::schemaId).thenComparing(SchemaCandidate::schemaVersion));
        if (candidates.isEmpty()) {
            throw new SchemaRoutingException("No current approved schema candidates match this problem. Add relevant details or ask a teacher to choose a schema.");
        }

        SchemaCandidate first = candidates.getFirst();
        double secondScore = candidates.size() > 1 ? candidates.get(1).confidence() : 0;
        double margin = Math.max(0, first.confidence() - secondScore);
        double bestEvidence = Math.max(first.verificationEvidence().requiredQuantityCoverage(),
                Math.max(first.verificationEvidence().unitCompatibility(), first.verificationEvidence().metadataOverlap()));
        String reason;
        SchemaRoutingDecision.Status status;
        if (bestEvidence < properties.minimumEvidenceScore()) {
            status = SchemaRoutingDecision.Status.AMBIGUOUS;
            reason = "INSUFFICIENT_CONTRACT_EVIDENCE";
        } else if (first.confidence() < properties.minimumScore()) {
            status = SchemaRoutingDecision.Status.AMBIGUOUS;
            reason = "BELOW_MINIMUM_CONFIDENCE";
        } else if (candidates.size() > 1 && margin < properties.minimumMargin()) {
            status = SchemaRoutingDecision.Status.AMBIGUOUS;
            reason = "INSUFFICIENT_CANDIDATE_MARGIN";
        } else {
            status = SchemaRoutingDecision.Status.SELECTED;
            reason = "CANDIDATE_CONTRACT_VERIFIED";
        }
        return new SchemaRoutingDecision(status, reason, candidates, first.confidence(), margin);
    }

    private SchemaIdentity identity(String schemaId, String version) {
        return new SchemaIdentity(schemaId, version);
    }

    private <T> T measure(String name, Supplier<T> operation) {
        long started = System.nanoTime();
        try {
            return operation.get();
        } finally {
            meters.timer(name).record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }
}
