package com.example.backend.schema.routing.service;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.backend.config.properties.SchemaRoutingProperties;
import com.example.backend.schema.routing.index.SchemaSearchIndex;
import com.example.backend.schema.routing.model.SchemaIdentity;
import com.example.backend.schema.routing.vector.EmbeddingClient;
import com.example.backend.schema.routing.vector.PgVectorSchemaEmbeddingStore;
import com.example.backend.service.problem.SchemaDefinitionService;

/** Reports whether the configured hybrid route can serve extraction requests. */
@Component("schemaRouting")
public final class SchemaRoutingHealthIndicator implements HealthIndicator {
    private final SchemaRoutingProperties properties;
    private final EmbeddingClient embeddings;
    private final PgVectorSchemaEmbeddingStore store;
    private final SchemaSearchIndex index;
    private final SchemaDefinitionService schemas;

    public SchemaRoutingHealthIndicator(SchemaRoutingProperties properties, EmbeddingClient embeddings,
            PgVectorSchemaEmbeddingStore store, SchemaSearchIndex index, SchemaDefinitionService schemas) {
        this.properties = properties;
        this.embeddings = embeddings;
        this.store = store;
        this.index = index;
        this.schemas = schemas;
    }

    @Override
    public Health health() {
        if (!properties.enabled()) return Health.outOfService().withDetail("routing", "disabled").build();
        var identity = properties.embedding();
        if (!identity.provider().equals(embeddings.providerId()) || !identity.model().equals(embeddings.modelId())
                || identity.dimension() != embeddings.dimension()) {
            return Health.down().withDetail("reason", "embedding_identity_mismatch").build();
        }
        try {
            if (!store.vectorExtensionInstalled()) {
                return Health.down().withDetail("action", "Apply Flyway migrations with pgvector support").build();
            }
            SchemaSearchIndex.Snapshot snapshot = index.snapshot();
            Set<SchemaIdentity> approved = schemas.approvedSchemas().stream()
                    .map(schema -> new SchemaIdentity(schema.getSchemaId(), schema.getVersion()))
                    .collect(Collectors.toUnmodifiableSet());
            if (!snapshot.byIdentity().keySet().equals(approved)) {
                return Health.down()
                        .withDetail("reason", "approved_schema_index_identity_mismatch")
                        .withDetail("approvedSchemaVersions", approved.size())
                        .withDetail("indexedSchemaVersions", snapshot.candidates().size())
                        .withDetail("action", "Refresh the approved-schema search index before accepting extraction requests")
                        .build();
            }
            return Health.up()
                    .withDetail("provider", identity.provider())
                    .withDetail("model", identity.model())
                    .withDetail("dimension", identity.dimension())
                    .withDetail("indexedSchemaVersions", snapshot.candidates().size())
                    .build();
        } catch (RuntimeException failure) {
            return Health.down().withDetail("action", "Verify pgvector migration, embedding credentials, and schema index readiness")
                    .build();
        }
    }
}
