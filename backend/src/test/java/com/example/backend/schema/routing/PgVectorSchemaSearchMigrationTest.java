package com.example.backend.schema.routing;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.backend.schema.routing.model.SchemaSearchDocument;
import com.example.backend.schema.routing.model.SchemaIdentity;
import com.example.backend.schema.routing.vector.EmbeddingResult;
import com.example.backend.schema.routing.vector.PgVectorSchemaEmbeddingStore;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises V8 against a real pgvector server; disabled only when Docker is unavailable. */
@Testcontainers(disabledWithoutDocker = true)
class PgVectorSchemaSearchMigrationTest {
    private static final String IMAGE = "pgvector/pgvector:0.8.6-pg16-bookworm";
    private static final String PRIOR_BASELINE = "7";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("physlive_migration_test")
            .withUsername("physlive")
            .withPassword("physlive-test");

    @Test
    void v8MigratesAnEmptyDatabaseAndProvidesWorkingCosineVectorStorage() throws Exception {
        String database = createDatabaseName("empty");
        createDatabase(database);

        try (Connection connection = connect(database)) {
            assertEquals(0, countPublicTables(connection), "fixture database must start without application tables");
        }

        migrateFromVersionSevenBaseline(database);

        try (Connection connection = connect(database); Statement statement = connection.createStatement()) {
            assertEquals(1, count(connection, "SELECT count(*) FROM information_schema.columns " +
                    "WHERE table_schema = 'public' AND table_name = 'schema_search_embeddings' " +
                    "AND column_name = 'embedding' AND udt_name = 'vector'"));
            assertEquals(1, count(connection, "SELECT count(*) FROM pg_extension WHERE extname = 'vector'"));

            statement.executeUpdate("INSERT INTO schema_search_embeddings " +
                    "(schema_id, schema_version, topic, search_text, embedding, embedding_provider, " +
                    "embedding_model, embedding_dimension, source_checksum, created_at, updated_at) VALUES " +
                    "('schema_a', '1.0', 'kinematics', 'length time', '[1,0,0]'::vector, 'fake', 'test-model', " +
                    "3, 'checksum-a', now(), now()), " +
                    "('schema_b', '1.0', 'dynamics', 'force mass', '[0,1,0]'::vector, 'fake', 'test-model', " +
                    "3, 'checksum-b', now(), now()), " +
                    "('schema_a', '0.9', 'kinematics', 'old length time', '[1,0,0]'::vector, 'fake', 'test-model', " +
                    "3, 'checksum-old', now(), now())");

            assertEquals(0.0d, scalarDouble(connection,
                    "SELECT embedding <=> '[1,0,0]'::vector FROM schema_search_embeddings " +
                            "WHERE schema_id = 'schema_a'"), 0.000001);
            assertEquals(1.0d, scalarDouble(connection,
                    "SELECT embedding <=> '[1,0,0]'::vector FROM schema_search_embeddings " +
                            "WHERE schema_id = 'schema_b'"), 0.000001);

            var datasource = new DriverManagerDataSource(jdbcUrl(database), POSTGRES.getUsername(),
                    POSTGRES.getPassword());
            var store = new PgVectorSchemaEmbeddingStore(new JdbcTemplate(datasource));
            var currentProjection = new SchemaSearchDocument("schema_a", "1.0", "kinematics", "A", "model-a",
                    "length time", "checksum-a");
            assertTrue(store.hasCurrentEmbedding(currentProjection, "fake", "test-model", 3));
            var changedProjection = new SchemaSearchDocument("schema_a", "1.0", "kinematics", "A", "model-a",
                    "length displacement duration", "checksum-a");
            Assertions.assertFalse(store.hasCurrentEmbedding(changedProjection, "fake", "test-model", 3),
                    "changed search projection must trigger re-embedding despite unchanged schema checksum");

            statement.execute("CREATE TABLE schema_versions (schema_id varchar(80), version varchar(24), "
                    + "topic varchar(80), lifecycle_status varchar(16), definition_checksum varchar(64), "
                    + "PRIMARY KEY (schema_id, version))");
            statement.execute("CREATE TABLE topics (name varchar(80) PRIMARY KEY, enabled boolean NOT NULL)");
            statement.executeUpdate("INSERT INTO topics VALUES ('kinematics', true), ('dynamics', true)");
            statement.executeUpdate("INSERT INTO schema_versions VALUES "
                    + "('schema_a', '1.0', 'kinematics', 'APPROVED', 'checksum-a'), "
                    + "('schema_b', '1.0', 'dynamics', 'APPROVED', 'checksum-b'), "
                    + "('schema_a', '0.9', 'kinematics', 'APPROVED', 'checksum-old')");
            var nearest = store.nearest(new EmbeddingResult(java.util.List.of(1.0, 0.0, 0.0)),
                    "fake", "test-model", 3, 2, java.util.List.of(
                            new SchemaIdentity("schema_a", "1.0"), new SchemaIdentity("schema_b", "1.0")));
            assertEquals(2, nearest.size());
            assertEquals("schema_a", nearest.getFirst().schemaId());
            assertEquals("schema_b", nearest.get(1).schemaId());
            assertEquals(1.0d, nearest.getFirst().similarity(), 0.000001);
            assertEquals(0.0d, nearest.get(1).similarity(), 0.000001);

            SQLException wrongDimension = assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO schema_search_embeddings " +
                            "(schema_id, schema_version, topic, search_text, embedding, embedding_provider, " +
                            "embedding_model, embedding_dimension, source_checksum, created_at, updated_at) VALUES " +
                            "('schema_c', '1.0', 'waves', 'wave', '[1,0]'::vector, 'fake', 'test-model', " +
                            "3, 'checksum-c', now(), now())"));
            assertEquals("23514", wrongDimension.getSQLState());
            assertEquals(1, count(connection, "SELECT count(*) FROM flyway_schema_history " +
                    "WHERE version = '8' AND success"));
            assertEquals(1, count(connection, "SELECT count(*) FROM flyway_schema_history " +
                    "WHERE version = '9' AND success"));
        }
    }

    @Test
    void v8UpgradesARepresentedVersionSevenDatabaseWithoutChangingSchemaHistory() throws Exception {
        String database = createDatabaseName("prior");
        createDatabase(database);
        try (Connection connection = connect(database); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE schema_versions (schema_id varchar(80) NOT NULL, " +
                    "version varchar(24) NOT NULL, definition jsonb NOT NULL, " +
                    "PRIMARY KEY (schema_id, version))");
            statement.execute("CREATE TABLE solver_versions (schema_id varchar(80) NOT NULL)");
            statement.execute("INSERT INTO schema_versions (schema_id, version, definition) " +
                    "VALUES ('preserved_schema', '1.4', '{\"revision\":\"prior\"}'::jsonb)");
        }

        migrateFromVersionSevenBaseline(database);

        try (Connection connection = connect(database)) {
            assertEquals(1, count(connection, "SELECT count(*) FROM schema_versions " +
                    "WHERE schema_id = 'preserved_schema' AND version = '1.4' " +
                    "AND definition = '{\"revision\":\"prior\"}'::jsonb"));
            assertEquals(1, count(connection, "SELECT count(*) FROM flyway_schema_history " +
                    "WHERE version = '8' AND success"));
            assertEquals(1, count(connection, "SELECT count(*) FROM flyway_schema_history " +
                    "WHERE version = '9' AND success"));
            assertEquals(1, count(connection, "SELECT count(*) FROM information_schema.columns " +
                    "WHERE table_schema = 'public' AND table_name = 'solver_versions' " +
                    "AND column_name = 'binding_checksum'"));
            assertEquals(1, count(connection, "SELECT count(*) FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_name = 'schema_search_embeddings'"));
        }
    }

    private static void migrateFromVersionSevenBaseline(String database) {
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl(database), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas("public")
                .defaultSchema("public")
                .baselineVersion(MigrationVersion.fromVersion(PRIOR_BASELINE))
                .load();
        // The checked-in chain predates clean database bootstrap and starts at
        // V4 with Hibernate-owned tables. Baseline at V7 to exercise V8 and all
        // later additive migrations against an otherwise empty application schema.
        flyway.baseline();
        flyway.migrate();
    }

    private static void createDatabase(String database) throws SQLException {
        try (Connection connection = connect("postgres"); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE \"" + database + "\"");
        }
    }

    private static int countPublicTables(Connection connection) throws SQLException {
        return count(connection, "SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'");
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static double scalarDouble(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getDouble(1);
        }
    }

    private static Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(database), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String jdbcUrl(String database) {
        return POSTGRES.getJdbcUrl().replace("/physlive_migration_test", "/" + database);
    }

    private static String createDatabaseName(String kind) {
        return "physlive_" + kind + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
