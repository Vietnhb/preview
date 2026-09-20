package com.example.backend.schema.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.backend.config.properties.SchemaRoutingProperties;
import com.example.backend.entity.enums.LifecycleStatus;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.schema.routing.index.SchemaEmbeddingIndexer;
import com.example.backend.schema.routing.index.SchemaSearchDocumentBuilder;
import com.example.backend.schema.routing.index.SchemaSearchIndex;
import com.example.backend.schema.routing.model.SchemaIdentity;
import com.example.backend.schema.routing.vector.EmbeddingClient;
import com.example.backend.schema.routing.vector.EmbeddingResult;
import com.example.backend.schema.routing.vector.PgVectorSchemaEmbeddingStore;
import com.example.backend.schema.routing.service.SchemaRoutingHealthIndicator;
import com.example.backend.service.problem.CompiledSchema;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Lifecycle/index integration against PostgreSQL and the pgvector query used in production. */
@Testcontainers(disabledWithoutDocker = true)
class PgVectorSchemaLifecycleIndexIntegrationTest {
    private static final String IMAGE = "pgvector/pgvector:0.8.6-pg16-bookworm";
    private static final String BASELINE_VERSION = "7";
    private static final String PROVIDER = "lifecycle-test";
    private static final String MODEL = "fixture-v1";
    private static final int DIMENSION = 3;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("physlive_lifecycle_test")
            .withUsername("physlive")
            .withPassword("physlive-test");

    @Test
    void approvedIdentityRefreshExcludesRetiredAndDisabledSchemasAndFailsReadinessOnRebuildFailure()
            throws Exception {
        String database = createDatabaseName();
        createDatabase(database);
        migrateAdditiveSearchSchema(database);

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl(database),
                POSTGRES.getUsername(), POSTGRES.getPassword()));
        createRegistryTables(jdbc);
        insertTopic(jdbc, "KINEMATICS", true);
        insertTopic(jdbc, "OPTICS", true);
        insertTopic(jdbc, "DYNAMICS", false);
        insertSchema(jdbc, "motion", "1.0", "KINEMATICS", "APPROVED", "motion-v1", "Motion v1", 1);
        insertSchema(jdbc, "motion", "2.0", "KINEMATICS", "DRAFT", "motion-v2", "Motion v2", 2);
        insertSchema(jdbc, "retiring", "1.0", "KINEMATICS", "APPROVED", "retiring-v1", "Retiring", 1);
        insertSchema(jdbc, "disabled_topic", "1.0", "DYNAMICS", "APPROVED", "disabled-v1", "Disabled topic", 1);
        insertSchema(jdbc, "stable", "1.0", "OPTICS", "APPROVED", "stable-v1", "Stable", 1);

        PgVectorSchemaEmbeddingStore store = new PgVectorSchemaEmbeddingStore(jdbc);
        for (SchemaIdentity identity : allInitialIdentities()) {
            insertVector(jdbc, identity);
        }

        SchemaDefinitionService schemas = mock(SchemaDefinitionService.class);
        when(schemas.approvedSchemas()).thenAnswer(ignored -> approvedEnabledSchemas(jdbc));
        when(schemas.compiled(any(SchemaVersion.class))).thenAnswer(invocation ->
                compileForIndex(invocation.getArgument(0)));

        AtomicBoolean failRebuild = new AtomicBoolean(false);
        EmbeddingClient embeddings = new EmbeddingClient() {
            @Override
            public EmbeddingResult embed(String text) {
                if (failRebuild.get() && text.contains("rebuild_failure_trigger")) {
                    throw new IllegalStateException("deterministic embedding failure for lifecycle test");
                }
                return new EmbeddingResult(List.of(1.0, 0.0, 0.0));
            }

            @Override public String providerId() { return PROVIDER; }
            @Override public String modelId() { return MODEL; }
            @Override public int dimension() { return DIMENSION; }
        };
        SchemaSearchIndex index = new SchemaSearchIndex();
        SchemaRoutingProperties properties = routingProperties();
        SchemaEmbeddingIndexer indexer = new SchemaEmbeddingIndexer(schemas,
                new SchemaSearchDocumentBuilder(), index, embeddings, store, properties);

        indexer.rebuild();
        assertEquals(Set.of(new SchemaIdentity("motion", "1.0"), new SchemaIdentity("retiring", "1.0"),
                new SchemaIdentity("stable", "1.0")), index.snapshot().byIdentity().keySet());
        SchemaRoutingHealthIndicator health = new SchemaRoutingHealthIndicator(properties, embeddings, store, index,
                schemas);
        assertEquals(Status.UP, health.health().getStatus());

        jdbc.update("UPDATE schema_versions SET lifecycle_status = 'RETIRED' WHERE schema_id = 'motion' AND version = '1.0'");
        jdbc.update("UPDATE schema_versions SET lifecycle_status = 'APPROVED' WHERE schema_id = 'motion' AND version = '2.0'");
        jdbc.update("UPDATE schema_versions SET lifecycle_status = 'RETIRED' WHERE schema_id = 'retiring'");

        indexer.rebuild();
        Set<SchemaIdentity> refreshed = index.snapshot().byIdentity().keySet();
        assertEquals(Set.of(new SchemaIdentity("motion", "2.0"), new SchemaIdentity("stable", "1.0")), refreshed);
        assertFalse(refreshed.contains(new SchemaIdentity("motion", "1.0")));
        assertFalse(refreshed.contains(new SchemaIdentity("retiring", "1.0")));
        assertFalse(refreshed.contains(new SchemaIdentity("disabled_topic", "1.0")));
        assertEquals(Status.UP, health.health().getStatus());

        List<PgVectorSchemaEmbeddingStore.VectorMatch> nearest = store.nearest(
                new EmbeddingResult(List.of(1.0, 0.0, 0.0)), PROVIDER, MODEL, DIMENSION, 10,
                allInitialIdentities());
        Set<SchemaIdentity> returned = nearest.stream()
                .map(match -> new SchemaIdentity(match.schemaId(), match.schemaVersion()))
                .collect(Collectors.toUnmodifiableSet());
        assertEquals(refreshed, returned,
                "the live cosine query must exclude retired, draft, and disabled-topic embeddings");

        insertSchema(jdbc, "failure_schema", "1.0", "KINEMATICS", "APPROVED", "failure-v1",
                "rebuild_failure_trigger", 1);
        failRebuild.set(true);
        assertThrows(IllegalStateException.class, indexer::rebuild);
        assertThrows(IllegalStateException.class, index::snapshot,
                "a failed lifecycle rebuild must discard the previously ready index");
        assertEquals(Status.DOWN, health.health().getStatus(),
                "readiness must fail closed instead of serving a stale approved-schema index");
    }

    private static Set<SchemaIdentity> allInitialIdentities() {
        return Set.of(new SchemaIdentity("motion", "1.0"), new SchemaIdentity("motion", "2.0"),
                new SchemaIdentity("retiring", "1.0"), new SchemaIdentity("disabled_topic", "1.0"),
                new SchemaIdentity("stable", "1.0"));
    }

    private static List<SchemaVersion> approvedEnabledSchemas(JdbcTemplate jdbc) {
        return jdbc.query("""
                SELECT schema_id, version, topic, lifecycle_status, definition_checksum, name, definition::text
                  FROM (
                    SELECT s.*, row_number() OVER (PARTITION BY lower(s.schema_id)
                        ORDER BY s.created_at DESC, s.version DESC) AS identity_rank
                      FROM schema_versions s
                      JOIN topics t ON lower(t.name) = lower(s.topic) AND t.enabled = TRUE
                     WHERE s.lifecycle_status = 'APPROVED'
                  ) active
                 WHERE identity_rank = 1
                 ORDER BY schema_id
                """, (rs, row) -> {
            SchemaVersion schema = new SchemaVersion();
            schema.setSchemaId(rs.getString("schema_id"));
            schema.setVersion(rs.getString("version"));
            schema.setTopic(rs.getString("topic"));
            schema.setLifecycleStatus(LifecycleStatus.valueOf(rs.getString("lifecycle_status")));
            schema.setDefinitionChecksum(rs.getString("definition_checksum"));
            schema.setName(rs.getString("name"));
            try {
                schema.setDefinition(MAPPER.readTree(rs.getString("definition")));
            } catch (Exception invalidFixtureJson) {
                throw new IllegalStateException("Lifecycle fixture contains invalid schema JSON", invalidFixtureJson);
            }
            return schema;
        });
    }

    private static CompiledSchema compileForIndex(SchemaVersion schema) {
        var quantity = new CompiledSchema.QuantityDefinition("mass", Set.of("m"), Set.of("kg"), Set.of("m"),
                false, false, false, null, null, null, "kg");
        String model = schema.getDefinition().path("model").asText();
        return new CompiledSchema(schema.getSchemaId(), schema.getVersion(), schema.getTopic(), model,
                java.util.Map.of("mass", quantity), java.util.Map.of("m", "mass"), java.util.Map.of(), Set.of(),
                java.util.Map.of(), java.util.Map.of(), new CompiledSchema.ExecutionDefinition(10, 1),
                new CompiledSchema.ValidationDefinition(0.01, List.of(1.0)), schema.getDefinitionChecksum());
    }

    private static SchemaRoutingProperties routingProperties() {
        return new SchemaRoutingProperties(true, 10, 10, 5, 60, 0.1, 0.02,
                20_000, 30_000, 80, 1.2, 0.75,
                0.1, 0.4, 0.4, 0.2, 0.5,
                new SchemaRoutingProperties.Embedding(PROVIDER, MODEL, DIMENSION, Duration.ofSeconds(1)));
    }

    private static void createRegistryTables(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE TABLE schema_versions (
                    schema_id varchar(80) NOT NULL,
                    version varchar(24) NOT NULL,
                    topic varchar(80) NOT NULL,
                    lifecycle_status varchar(16) NOT NULL,
                    definition_checksum varchar(64) NOT NULL,
                    name varchar(120) NOT NULL,
                    definition jsonb NOT NULL,
                    created_at timestamptz NOT NULL,
                    PRIMARY KEY (schema_id, version)
                )
                """);
        jdbc.execute("CREATE TABLE topics (name varchar(80) PRIMARY KEY, enabled boolean NOT NULL)");
    }

    private static void insertTopic(JdbcTemplate jdbc, String name, boolean enabled) {
        jdbc.update("INSERT INTO topics (name, enabled) VALUES (?, ?)", name, enabled);
    }

    private static void insertSchema(JdbcTemplate jdbc, String schemaId, String version, String topic,
            String status, String checksum, String name, int ageDays) throws Exception {
        JsonNode definition = MAPPER.readTree("""
                {"model":"%s_%s","description":"%s","requiredQuantities":[
                  {"key":"mass","aliases":["m"],"allowedUnits":["kg"],"symbol":"m"}],
                 "optionalQuantities":[]}
                """.formatted(schemaId, version.replace('.', '_'), name));
        jdbc.update("""
                INSERT INTO schema_versions
                    (schema_id, version, topic, lifecycle_status, definition_checksum, name, definition, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CURRENT_TIMESTAMP - (? * INTERVAL '1 day'))
                """, schemaId, version, topic, status, checksum, name, definition.toString(), ageDays);
    }

    private static void insertVector(JdbcTemplate jdbc, SchemaIdentity identity) {
        jdbc.update("""
                INSERT INTO schema_search_embeddings
                    (schema_id, schema_version, topic, search_text, embedding, embedding_provider,
                     embedding_model, embedding_dimension, source_checksum, created_at, updated_at)
                SELECT ?, ?, s.topic, ?, CAST('[1,0,0]' AS vector), ?, ?, ?, s.definition_checksum,
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                  FROM schema_versions s WHERE s.schema_id = ? AND s.version = ?
                """, identity.schemaId(), identity.schemaVersion(), identity.schemaId() + " " + identity.schemaVersion(),
                PROVIDER, MODEL, DIMENSION, identity.schemaId(), identity.schemaVersion());
    }

    private static void migrateAdditiveSearchSchema(String database) {
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl(database), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas("public")
                .defaultSchema("public")
                .baselineVersion(MigrationVersion.fromVersion(BASELINE_VERSION))
                .load();
        flyway.baseline();
        flyway.migrate();
    }

    private static void createDatabase(String database) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl("postgres"), POSTGRES.getUsername(),
                POSTGRES.getPassword()); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE \"" + database + "\"");
        }
    }

    private static String jdbcUrl(String database) {
        return POSTGRES.getJdbcUrl().replace("/physlive_lifecycle_test", "/" + database);
    }

    private static String createDatabaseName() {
        return "physlive_lifecycle_" + UUID.randomUUID().toString().replace("-", "");
    }
}
