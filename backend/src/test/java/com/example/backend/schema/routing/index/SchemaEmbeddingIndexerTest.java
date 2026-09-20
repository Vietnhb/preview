package com.example.backend.schema.routing.index;

import com.example.backend.config.properties.SchemaRoutingProperties;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.schema.routing.lexical.Bm25SchemaRetriever;
import com.example.backend.schema.routing.index.IndexedSchemaCandidate;
import com.example.backend.schema.routing.index.SchemaEmbeddingIndexer.ReindexResult;
import com.example.backend.schema.routing.model.SchemaSearchDocument;
import com.example.backend.schema.routing.vector.EmbeddingClient;
import com.example.backend.schema.routing.vector.PgVectorSchemaEmbeddingStore;
import com.example.backend.service.problem.CompiledSchema;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchemaEmbeddingIndexerTest {
    @Test
    void failedLifecycleRefreshInvalidatesThePreviouslyReadySnapshot() throws Exception {
        SchemaDefinitionService schemas = mock(SchemaDefinitionService.class);
        SchemaSearchDocumentBuilder documents = mock(SchemaSearchDocumentBuilder.class);
        SchemaSearchIndex index = new SchemaSearchIndex();
        EmbeddingClient embeddings = mock(EmbeddingClient.class);
        PgVectorSchemaEmbeddingStore store = mock(PgVectorSchemaEmbeddingStore.class);
        SchemaRoutingProperties properties = new SchemaRoutingProperties(true, 5, 5, 3, 60,
                0.1, 0.02, 20_000, 30_000, 80, 1.2, 0.75,
                0.1, 0.4, 0.4, 0.2, 0.5,
                new SchemaRoutingProperties.Embedding("test-provider", "test-model", 3, Duration.ofSeconds(1)));
        SchemaSearchDocument previousDocument = document("old-schema");
        var previousContract = CandidateContractProjection.from("old-schema", "1.0", "WAVES", "Old schema",
                new ObjectMapper().readTree("""
                        {"model":"old-schema","requiredQuantities":[{"key":"mass","aliases":[],"allowedUnits":["kg"]}],"optionalQuantities":[]}
                        """));
        index.replace(List.of(new IndexedSchemaCandidate(previousDocument, previousContract)),
                new Bm25SchemaRetriever(1.2, 0.75).buildIndex(List.of(previousDocument)));

        SchemaVersion newlyApproved = schema("new-schema");
        when(schemas.approvedSchemas()).thenReturn(List.of(newlyApproved));
        when(schemas.compiled(newlyApproved)).thenThrow(new IllegalStateException("embedding unavailable"));
        when(embeddings.providerId()).thenReturn("test-provider");
        when(embeddings.modelId()).thenReturn("test-model");
        when(embeddings.dimension()).thenReturn(3);

        SchemaEmbeddingIndexer indexer = new SchemaEmbeddingIndexer(
                schemas, documents, index, embeddings, store, properties);

        assertThrows(IllegalStateException.class, indexer::rebuild);
        assertThrows(IllegalStateException.class, index::snapshot);
    }

    @Test
    void dryRunReportsMissingEmbeddingsWithoutGeneratingOrWritingVectors() {
        SchemaDefinitionService schemas = mock(SchemaDefinitionService.class);
        SchemaSearchDocumentBuilder documents = mock(SchemaSearchDocumentBuilder.class);
        SchemaSearchIndex index = mock(SchemaSearchIndex.class);
        EmbeddingClient embeddings = mock(EmbeddingClient.class);
        PgVectorSchemaEmbeddingStore store = mock(PgVectorSchemaEmbeddingStore.class);
        SchemaRoutingProperties properties = new SchemaRoutingProperties(true, 5, 5, 3, 60,
                0.1, 0.02, 20_000, 30_000, 80, 1.2, 0.75,
                0.1, 0.4, 0.4, 0.2, 0.5,
                new SchemaRoutingProperties.Embedding("test-provider", "test-model", 3, Duration.ofSeconds(1)));
        SchemaVersion first = schema("schema-a");
        SchemaVersion second = schema("schema-b");
        CompiledSchema compiled = mock(CompiledSchema.class);
        SchemaSearchDocument firstDocument = document("schema-a");
        SchemaSearchDocument secondDocument = document("schema-b");

        when(schemas.approvedSchemas()).thenReturn(List.of(first, second));
        when(schemas.compiled(any(SchemaVersion.class))).thenReturn(compiled);
        when(documents.build(first, compiled)).thenReturn(firstDocument);
        when(documents.build(second, compiled)).thenReturn(secondDocument);
        when(embeddings.providerId()).thenReturn("test-provider");
        when(embeddings.modelId()).thenReturn("test-model");
        when(embeddings.dimension()).thenReturn(3);
        when(store.hasCurrentEmbedding(firstDocument, "test-provider", "test-model", 3)).thenReturn(true);
        when(store.hasCurrentEmbedding(secondDocument, "test-provider", "test-model", 3)).thenReturn(false);

        SchemaEmbeddingIndexer indexer = new SchemaEmbeddingIndexer(
                schemas, documents, index, embeddings, store, properties);

        ReindexResult result = indexer.reindex(true);

        assertTrue(result.dryRun());
        assertEquals(2, result.approvedSchemas());
        assertEquals(1, result.currentEmbeddings());
        assertEquals(1, result.pendingEmbeddings());
        assertEquals("test-provider", result.embeddingProvider());
        assertEquals("test-model", result.embeddingModel());
        assertEquals(3, result.embeddingDimension());
        verify(embeddings, never()).embed(any(String.class));
        verify(store, never()).upsert(any(), any(), any(), anyInt(), any());
        verify(index, never()).replace(any(), any());
    }

    private static SchemaVersion schema(String id) {
        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId(id);
        schema.setVersion("1.0");
        schema.setTopic("WAVES");
        schema.setName(id);
        schema.setDefinitionChecksum("checksum-" + id);
        schema.setDefinition(new ObjectMapper().createObjectNode());
        return schema;
    }

    private static SchemaSearchDocument document(String id) {
        return new SchemaSearchDocument(id, "1.0", "WAVES", id, id,
                "search text for " + id, "checksum-" + id);
    }
}
