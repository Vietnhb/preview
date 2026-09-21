package com.example.backend.schema.routing.index;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.config.properties.SchemaRoutingProperties;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.exception.EmbeddingUnavailableException;
import com.example.backend.schema.routing.vector.EmbeddingClient;
import com.example.backend.schema.routing.vector.EmbeddingResult;
import com.example.backend.schema.routing.vector.PgVectorSchemaEmbeddingStore;
import com.example.backend.schema.routing.lexical.Bm25SchemaRetriever;
import com.example.backend.service.problem.SchemaDefinitionService;

/** Idempotently indexes only missing or stale approved versions before requests are accepted. */
@Service
public class SchemaEmbeddingIndexer {
    public record ReindexResult(boolean dryRun, int approvedSchemas, int currentEmbeddings,
                                int pendingEmbeddings, String embeddingProvider,
                                String embeddingModel, int embeddingDimension) { }

    private final SchemaDefinitionService schemaDefinitions;
    private final SchemaSearchDocumentBuilder documents;
    private final SchemaSearchIndex index;
    private final EmbeddingClient embeddings;
    private final PgVectorSchemaEmbeddingStore store;
    private final SchemaRoutingProperties properties;
    private final Bm25SchemaRetriever lexical;
    @Value("${physlive.schema-routing.legacy-index-startup:false}")
    private boolean legacyIndexStartup;

    public SchemaEmbeddingIndexer(SchemaDefinitionService schemaDefinitions, SchemaSearchDocumentBuilder documents,
            SchemaSearchIndex index, EmbeddingClient embeddings, PgVectorSchemaEmbeddingStore store,
            SchemaRoutingProperties properties) {
        this.schemaDefinitions = schemaDefinitions;
        this.documents = documents;
        this.index = index;
        this.embeddings = embeddings;
        this.store = store;
        this.properties = properties;
        this.lexical = new Bm25SchemaRetriever(properties.bm25K1(), properties.bm25B());
        requireConfiguredIdentity();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void indexAtStartup() {
        if (legacyIndexStartup && properties.enabled()) rebuild();
    }

    /** Safe to call from an administrative reindex operation; upserts are idempotent. */
    public synchronized void rebuild() {
        requireEnabled();
        index.invalidate();
        try {
            List<IndexedSchemaCandidate> approved = schemaDefinitions.approvedSchemas().stream()
                    .map(this::indexOne)
                    .toList();
            String semanticGeneration = null;
            if (approved.stream().anyMatch(item -> !item.document().semanticViews().isEmpty())) {
                semanticGeneration = store.startGeneration(SchemaRetrievalMetadataCatalog.PROJECTION_VERSION,
                        embeddings.providerId(), embeddings.modelId(), embeddings.dimension());
                if (semanticGeneration == null || semanticGeneration.isBlank()) {
                    throw new IllegalStateException("Semantic retrieval generation could not be created");
                }
                indexSemanticViews(approved, semanticGeneration);
                store.activateGeneration(semanticGeneration, SchemaRetrievalMetadataCatalog.PROJECTION_VERSION,
                        embeddings.providerId(), embeddings.modelId(), embeddings.dimension());
            }
            index.replace(approved, lexical.buildIndex(approved.stream().map(IndexedSchemaCandidate::document).toList()));
        } catch (RuntimeException failure) {
            index.invalidate();
            throw failure;
        }
    }

    /** Reindexes idempotently, or reports the work without embedding or writing when dryRun is true. */
    public synchronized ReindexResult reindex(boolean dryRun) {
        requireEnabled();
        if (!dryRun) rebuild();
        return inspectCurrentEmbeddings(dryRun);
    }

    public void refreshAfterCatalogChange() {
        if (!legacyIndexStartup || !properties.enabled()) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            rebuild();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rebuild();
            }
        });
    }

    private IndexedSchemaCandidate indexOne(SchemaVersion schema) {
        var document = verifiedDocument(schema);
        if (!store.hasCurrentEmbedding(document, embeddings.providerId(), embeddings.modelId(), embeddings.dimension())) {
            EmbeddingResult result;
            try {
                result = embeddings.embed(document.searchText());
            } catch (EmbeddingUnavailableException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw new EmbeddingUnavailableException(
                        "Check embedding provider/model configuration and credentials before reindexing the schema search index.");
            }
            if (result.values().size() != embeddings.dimension()) {
                throw new IllegalStateException("Embedding client returned an unexpected dimension for schema search index");
            }
            store.upsert(document, embeddings.providerId(), embeddings.modelId(), embeddings.dimension(), result);
        }
        var contract = CandidateContractProjection.from(schema.getSchemaId(), schema.getVersion(), schema.getTopic(),
                schema.getName(), schema.getDefinition());
        return new IndexedSchemaCandidate(document, contract);
    }

    private void indexSemanticViews(List<IndexedSchemaCandidate> candidates, String generationId) {
        for (IndexedSchemaCandidate candidate : candidates) {
            for (var view : candidate.document().semanticViews()) {
                EmbeddingResult result;
                try {
                    result = embeddings.embed(view.text());
                } catch (EmbeddingUnavailableException failure) {
                    throw failure;
                } catch (RuntimeException failure) {
                    throw new EmbeddingUnavailableException(
                            "Check embedding provider/model configuration and credentials before semantic reindexing.");
                }
                store.upsertSemanticView(generationId, candidate.document(), view,
                        embeddings.providerId(), embeddings.modelId(), embeddings.dimension(), result);
            }
        }
    }

    private ReindexResult inspectCurrentEmbeddings(boolean dryRun) {
        int current = 0;
        List<SchemaVersion> approved = schemaDefinitions.approvedSchemas();
        for (SchemaVersion schema : approved) {
            var document = verifiedDocument(schema);
            if (store.hasCurrentEmbedding(document, embeddings.providerId(), embeddings.modelId(), embeddings.dimension())) {
                current++;
            }
        }
        return new ReindexResult(dryRun, approved.size(), current, approved.size() - current,
                embeddings.providerId(), embeddings.modelId(), embeddings.dimension());
    }

    private com.example.backend.schema.routing.model.SchemaSearchDocument verifiedDocument(SchemaVersion schema) {
        if (schema.getDefinitionChecksum() == null || schema.getDefinitionChecksum().isBlank()) {
            throw new IllegalStateException("Schema routing cannot index " + schema.getSchemaId() + "@"
                    + schema.getVersion() + " until its stored definition checksum has been verified and backfilled");
        }
        var compiled = schemaDefinitions.compiled(schema);
        return documents.build(schema, compiled);
    }

    private void requireConfiguredIdentity() {
        var configured = properties.embedding();
        if (!embeddings.providerId().equals(configured.provider())
                || !embeddings.modelId().equals(configured.model())
                || embeddings.dimension() != configured.dimension()) {
            throw new IllegalStateException("Embedding client identity does not match physlive.schema-routing.embedding configuration");
        }
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new EmbeddingUnavailableException(
                    "Enable physlive.schema-routing.enabled before rebuilding the schema search index.");
        }
    }
}
