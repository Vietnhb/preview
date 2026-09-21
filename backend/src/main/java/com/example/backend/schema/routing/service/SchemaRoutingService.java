package com.example.backend.schema.routing.service;

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
import com.example.backend.schema.routing.lexical.UnicodePhysicsNormalizer;
import com.example.backend.schema.routing.model.RetrievalScore;
import com.example.backend.schema.routing.model.SchemaCandidate;
import com.example.backend.schema.routing.model.SchemaCandidate.VerificationEvidence;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.example.backend.schema.routing.model.SchemaIdentity;
import com.example.backend.schema.routing.vector.EmbeddingClient;
import com.example.backend.schema.routing.vector.EmbeddingResult;
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
        String query = UnicodePhysicsNormalizer.normalize(problemText);
        SchemaSearchIndex.Snapshot snapshot;
        try {
            snapshot = index.snapshot();
        } catch (RuntimeException failure) {
            throw new EmbeddingUnavailableException("The approved-schema search index is not ready; reindex schema embeddings.");
        }

        List<Bm25SchemaRetriever.RankedDocument> lexicalMatches = measure("physlive.schema.routing.lexical", () ->
                lexical.rank(query, snapshot.bm25Index(), properties.lexicalTopK()));
        var queryVector = embedQuery(query);
        List<SchemaVectorRetriever.RankedVector> vectorMatches = retrieveVectors(queryVector, snapshot);

        // Candidate verification and fusion are application logic. Keep their failures visible as
        // routing/contract failures instead of misreporting them as an embedding outage.
        SchemaRoutingDecision decision;
        try {
            decision = decide(query, snapshot, lexicalMatches, vectorMatches);
        } catch (EmbeddingUnavailableException | SchemaRoutingException failure) {
            meters.counter("physlive.schema.routing.failures").increment();
            throw failure;
        } catch (RuntimeException failure) {
            meters.counter("physlive.schema.routing.failures").increment();
            throw new SchemaRoutingException("Schema candidate verification failed for the approved index.", failure);
        }
        meters.summary("physlive.schema.routing.candidate_count").record(decision.candidates().size());
        decision.selectedCandidate().ifPresent(selected -> {
            meters.counter("physlive.schema.routing.selected").increment();
            int lexicalRank = selected.retrievalScore().lexicalRank();
            int vectorRank = selected.retrievalScore().vectorRank();
            int selectedRank = firstPresentRank(lexicalRank, vectorRank);
            meters.summary("physlive.schema.routing.selected_rank").record(selectedRank);
            meters.summary("physlive.schema.routing.selected_lexical_rank").record(
                    Math.max(1, selected.retrievalScore().lexicalRank()));
            meters.summary("physlive.schema.routing.selected_vector_rank").record(
                    Math.max(1, selected.retrievalScore().vectorRank()));
        });
        if (decision.status() == SchemaRoutingDecision.Status.AMBIGUOUS) {
            meters.counter("physlive.schema.routing.ambiguous", "reason", decision.reasonCode()).increment();
        }
        return decision;
    }

    private EmbeddingResult embedQuery(String query) {
        try {
            return measure("physlive.schema.routing.embedding", () -> embeddings.embed(query));
        } catch (EmbeddingUnavailableException failure) {
            meters.counter("physlive.schema.routing.embedding.failures").increment();
            meters.counter("physlive.schema.routing.failures").increment();
            throw failure;
        } catch (RuntimeException failure) {
            meters.counter("physlive.schema.routing.embedding.failures").increment();
            meters.counter("physlive.schema.routing.failures").increment();
            throw new EmbeddingUnavailableException(
                    "Check embedding provider/model configuration and credentials.");
        }
    }

    private List<SchemaVectorRetriever.RankedVector> retrieveVectors(
            EmbeddingResult queryVector,
            SchemaSearchIndex.Snapshot snapshot) {
        try {
            return measure("physlive.schema.routing.vector", () -> vector.rank(queryVector,
                    embeddings.providerId(), embeddings.modelId(), embeddings.dimension(),
                    properties.vectorTopK(), snapshot.byIdentity().keySet()));
        } catch (EmbeddingUnavailableException failure) {
            meters.counter("physlive.schema.routing.failures").increment();
            throw failure;
        } catch (RuntimeException failure) {
            meters.counter("physlive.schema.routing.failures").increment();
            throw new EmbeddingUnavailableException(
                    "Check PostgreSQL pgvector readiness and the current embedding index.");
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
        boolean noRetrievalEvidence = fused.isEmpty();
        if (noRetrievalEvidence) {
            fused = snapshot.candidates().stream().limit(properties.candidateTopK())
                    .map(item -> new ReciprocalRankFusion.FusedCandidate(
                            item.document().schemaId(), item.document().schemaVersion(), Double.MIN_VALUE))
                    .toList();
        }

        Map<SchemaIdentity, Bm25SchemaRetriever.RankedDocument> lexicalByIdentity = new HashMap<>();
        lexicalMatches.forEach(item -> lexicalByIdentity.putIfAbsent(
                identity(item.document().schemaId(), item.document().schemaVersion()), item));
        Map<SchemaIdentity, SchemaVectorRetriever.RankedVector> vectorByIdentity = new HashMap<>();
        vectorMatches.forEach(item -> vectorByIdentity.putIfAbsent(
                identity(item.schemaId(), item.schemaVersion()), item));

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
        if (candidates.isEmpty()) throw new SchemaRoutingException(
                "The approved schema index contains no contracts for bounded ambiguity handling.");

        SchemaCandidate first = candidates.getFirst();
        double secondScore = candidates.size() > 1 ? candidates.get(1).confidence() : 0;
        double margin = Math.max(0, first.confidence() - secondScore);
        double bestEvidence = Math.max(first.verificationEvidence().requiredQuantityCoverage(),
                Math.max(first.verificationEvidence().unitCompatibility(), first.verificationEvidence().metadataOverlap()));
        boolean independentContractEvidence = verifier.hasIndependentQueryEvidence(query)
                && (first.verificationEvidence().requiredQuantityCoverage() > 0
                || first.verificationEvidence().metadataOverlap() > 0);
        String reason;
        SchemaRoutingDecision.Status status;
        if (noRetrievalEvidence) {
            status = SchemaRoutingDecision.Status.AMBIGUOUS;
            reason = "NO_RETRIEVAL_EVIDENCE";
        } else if (!independentContractEvidence && first.verificationEvidence().unitCompatibility() > 0) {
            status = SchemaRoutingDecision.Status.AMBIGUOUS;
            reason = "UNIT_ONLY_EVIDENCE";
        } else if (bestEvidence < properties.minimumEvidenceScore()) {
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

    /** Keeps the rank metric meaningful when one retriever did not return the selected identity. */
    private static int firstPresentRank(int first, int second) {
        if (first > 0 && second > 0) return Math.min(first, second);
        if (first > 0) return first;
        if (second > 0) return second;
        return 0;
    }
}
