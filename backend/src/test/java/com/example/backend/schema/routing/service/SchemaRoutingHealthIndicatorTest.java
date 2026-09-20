package com.example.backend.schema.routing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.config.properties.SchemaRoutingProperties;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.schema.routing.index.IndexedSchemaCandidate;
import com.example.backend.schema.routing.index.SchemaSearchIndex;
import com.example.backend.schema.routing.lexical.Bm25SchemaRetriever;
import com.example.backend.schema.routing.model.SchemaSearchDocument;
import com.example.backend.schema.routing.vector.EmbeddingClient;
import com.example.backend.schema.routing.vector.PgVectorSchemaEmbeddingStore;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.fasterxml.jackson.databind.ObjectMapper;

class SchemaRoutingHealthIndicatorTest {
    @Test
    void readinessIsDownWhenIndexIdentitiesDoNotMatchCurrentApprovedSchemas() throws Exception {
        SchemaRoutingProperties properties = new SchemaRoutingProperties(true, 5, 5, 3, 60,
                0.1, 0.02, 20_000, 30_000, 80, 1.2, 0.75,
                0.1, 0.4, 0.4, 0.2, 0.5,
                new SchemaRoutingProperties.Embedding("fake", "model-v1", 2, Duration.ofSeconds(1)));
        EmbeddingClient embeddings = mock(EmbeddingClient.class);
        when(embeddings.providerId()).thenReturn("fake");
        when(embeddings.modelId()).thenReturn("model-v1");
        when(embeddings.dimension()).thenReturn(2);
        PgVectorSchemaEmbeddingStore store = mock(PgVectorSchemaEmbeddingStore.class);
        when(store.vectorExtensionInstalled()).thenReturn(true);

        SchemaSearchDocument staleDocument = new SchemaSearchDocument("retired_schema", "1.0", "WAVES",
                "Retired schema", "model", "wave frequency", "checksum-a");
        var definition = new ObjectMapper().readTree("""
                {"model":"retired_schema","requiredQuantities":[{"key":"mass","aliases":[],"allowedUnits":["kg"]}],"optionalQuantities":[]}
                """);
        var contract = CandidateContractProjection.from("retired_schema", "1.0", "WAVES", "Retired schema", definition);
        SchemaSearchIndex index = new SchemaSearchIndex();
        index.replace(List.of(new IndexedSchemaCandidate(staleDocument, contract)),
                new Bm25SchemaRetriever(1.2, 0.75).buildIndex(List.of(staleDocument)));

        SchemaVersion current = new SchemaVersion();
        current.setSchemaId("current_schema");
        current.setVersion("2.0");
        SchemaDefinitionService schemas = mock(SchemaDefinitionService.class);
        when(schemas.approvedSchemas()).thenReturn(List.of(current));

        var health = new SchemaRoutingHealthIndicator(properties, embeddings, store, index, schemas).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("approved_schema_index_identity_mismatch", health.getDetails().get("reason"));
    }
}
