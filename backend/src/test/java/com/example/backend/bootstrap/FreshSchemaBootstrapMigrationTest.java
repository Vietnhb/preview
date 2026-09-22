package com.example.backend.bootstrap;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the documented Hibernate-first bootstrap for a verified empty database. */
@Testcontainers(disabledWithoutDocker = true)
class FreshSchemaBootstrapMigrationTest {
    private static final String IMAGE = "pgvector/pgvector:0.8.6-pg16-bookworm";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("physlive_fresh_bootstrap_test")
            .withUsername("physlive")
            .withPassword("physlive-test");

    @Test
    void hibernateBaselineCanBeBaselinedAtZeroBeforeApplyingTheFullFlywayChain() throws Exception {
        try (Connection connection = connect()) {
            assertEquals(0, count(connection, "SELECT count(*) FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"),
                    "fixture database must start with an empty public schema");
        }

        DataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        createHibernateEntitySchema(dataSource);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertTrue(tableExists(jdbc, "assignment_submissions"));
        assertTrue(tableExists(jdbc, "assignments"));
        assertTrue(tableExists(jdbc, "school_classes"));
        assertTrue(tableExists(jdbc, "schema_versions"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_name = 'flyway_schema_history'", Integer.class));

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas("public")
                .defaultSchema("public")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .load()
                .migrate();

        validateHibernateEntitySchema(dataSource);

        assertEquals("0", jdbc.queryForObject("SELECT version FROM flyway_schema_history " +
                "WHERE type = 'BASELINE' AND success", String.class));
        assertEquals("30", jdbc.queryForObject("SELECT version FROM flyway_schema_history " +
                "WHERE success ORDER BY installed_rank DESC LIMIT 1", String.class));
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30"),
                jdbc.queryForList("SELECT version FROM flyway_schema_history " +
                "WHERE type = 'SQL' AND success ORDER BY installed_rank", String.class));

        // V1/V3 repair work must see Hibernate's tables and apply its named indexes and FKs.
        for (String index : List.of(
                "idx_simulation_runs_simulation_created",
                "idx_specifications_submission_created",
                "idx_ambiguity_cases_specification_status",
                "idx_assignments_teacher_created",
                "idx_assignment_students_student",
                "idx_assignment_submissions_assignment_submitted",
                "idx_student_action_logs_student_occurred")) {
            assertTrue(indexExists(jdbc, index), "missing index created by V1/V3: " + index);
        }
        assertConstraintExists(jdbc, "assignment_students", "fk_assignment_students_student");
        assertConstraintExists(jdbc, "student_action_logs", "fk_student_action_logs_student");
        assertConstraintExists(jdbc, "student_action_logs", "fk_student_action_logs_assignment");
        assertConstraintExists(jdbc, "users", "fk_users_deactivated_by");
        assertConstraintExists(jdbc, "simulations", "fk_simulations_latest_run");
        assertConstraintExists(jdbc, "assignments", "fk_assignments_assigned_run");

        // V4, V6, and V7 columns are part of the final entity schema; V5 is recorded as applied.
        assertColumnExists(jdbc, "assignment_submissions", "completed_at");
        assertColumnExists(jdbc, "assignments", "school_class_id");
        assertConstraintExists(jdbc, "assignments", "fk_assignments_school_class");
        assertColumnExists(jdbc, "schema_versions", "definition_checksum");
        assertColumnExists(jdbc, "simulation_runs", "schema_id");
        assertColumnExists(jdbc, "simulation_runs", "schema_version");
        assertColumnExists(jdbc, "simulation_runs", "binding_version");
        assertColumnExists(jdbc, "simulation_runs", "output_contract_checksum");
        assertTrue(indexExists(jdbc, "idx_simulation_runs_contract_identity"));
        assertTrue(tableExists(jdbc, "ambiguity_cases"));
        assertTrue(tableExists(jdbc, "reviewer_decisions"));
        assertTrue(indexExists(jdbc, "idx_reviewer_decisions_ambiguity_case"));
        assertTrue(migrationApplied(jdbc, "5"), "V5 backfill migration must run");
    }

    @Test
    void versionSixDatabaseMissingSchemaVersionsIsRepairedBeforeVersionSeven() throws Exception {
        String database = "physlive_v6_repair_" + UUID.randomUUID().toString().replace("-", "");
        createDatabase(database);
        String jdbcUrl = jdbcUrl(database);
        DataSource dataSource = new DriverManagerDataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
        createHibernateEntitySchema(dataSource);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(),
                POSTGRES.getPassword()); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE schema_versions");
            statement.execute("CREATE TABLE legacy_marker (id integer PRIMARY KEY, payload text NOT NULL)");
            statement.executeUpdate("INSERT INTO legacy_marker VALUES (1, 'preserve-me')");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas("public")
                .defaultSchema("public")
                .baselineVersion(MigrationVersion.fromVersion("6"))
                .load();
        flyway.baseline();
        flyway.migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl,
                POSTGRES.getUsername(), POSTGRES.getPassword()));
        assertEquals("preserve-me", jdbc.queryForObject(
                "SELECT payload FROM legacy_marker WHERE id = 1", String.class));
        assertColumnExists(jdbc, "schema_versions", "definition_checksum");
        assertTrue(indexExists(jdbc, "uk_schema_versions_identity"));
        assertTrue(indexExists(jdbc, "idx_schema_versions_lifecycle"));
        assertEquals("30", jdbc.queryForObject("SELECT version FROM flyway_schema_history " +
                "WHERE success ORDER BY installed_rank DESC LIMIT 1", String.class));
        assertEquals(List.of("7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30"),
                jdbc.queryForList("SELECT version FROM flyway_schema_history " +
                        "WHERE type = 'SQL' AND success ORDER BY installed_rank", String.class));
        assertTrue(tableExists(jdbc, "ambiguity_cases"));
        assertTrue(tableExists(jdbc, "reviewer_decisions"));
        validateHibernateEntitySchema(dataSource);
    }

    @Test
    void versionElevenDatabaseMissingAmbiguityTablesIsRepairedByVersionTwelve() throws Exception {
        String database = "physlive_v11_ambiguity_repair_" + UUID.randomUUID().toString().replace("-", "");
        createDatabase(database);
        String jdbcUrl = jdbcUrl(database);
        DataSource dataSource = new DriverManagerDataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
        createHibernateEntitySchema(dataSource);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(),
                POSTGRES.getPassword()); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE reviewer_decisions");
            statement.execute("DROP TABLE ambiguity_cases");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas("public")
                .defaultSchema("public")
                .baselineVersion(MigrationVersion.fromVersion("11"))
                .load();
        flyway.baseline();
        flyway.migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl,
                POSTGRES.getUsername(), POSTGRES.getPassword()));
        assertTrue(tableExists(jdbc, "ambiguity_cases"));
        assertTrue(tableExists(jdbc, "reviewer_decisions"));
        assertTrue(indexExists(jdbc, "idx_ambiguity_cases_specification_status"));
        assertTrue(indexExists(jdbc, "idx_reviewer_decisions_ambiguity_case"));
        assertEquals("30", jdbc.queryForObject("SELECT version FROM flyway_schema_history " +
                "WHERE success ORDER BY installed_rank DESC LIMIT 1", String.class));
        validateHibernateEntitySchema(dataSource);
    }

    @Test
    void versionTwelveDatabaseMissingAmbiguityTablesIsRepairedByVersionThirteen() throws Exception {
        String database = "physlive_v12_ambiguity_repair_" + UUID.randomUUID().toString().replace("-", "");
        createDatabase(database);
        String jdbcUrl = jdbcUrl(database);
        DataSource dataSource = new DriverManagerDataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
        createHibernateEntitySchema(dataSource);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(),
                POSTGRES.getPassword()); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE reviewer_decisions");
            statement.execute("DROP TABLE ambiguity_cases");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas("public")
                .defaultSchema("public")
                .baselineVersion(MigrationVersion.fromVersion("12"))
                .load();
        flyway.baseline();
        flyway.migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl,
                POSTGRES.getUsername(), POSTGRES.getPassword()));
        assertTrue(tableExists(jdbc, "ambiguity_cases"));
        assertTrue(tableExists(jdbc, "reviewer_decisions"));
        assertTrue(indexExists(jdbc, "idx_reviewer_decisions_ambiguity_case"));
        assertEquals("30", jdbc.queryForObject("SELECT version FROM flyway_schema_history " +
                "WHERE success ORDER BY installed_rank DESC LIMIT 1", String.class));
        validateHibernateEntitySchema(dataSource);
    }

    @Test
    void versionSixDatabaseWithSchemaVersionsPreservesPublishedRows() throws Exception {
        String database = "physlive_v6_existing_" + UUID.randomUUID().toString().replace("-", "");
        createDatabase(database);
        String jdbcUrl = jdbcUrl(database);
        UUID existingId = UUID.randomUUID();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(),
                POSTGRES.getPassword()); var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE schema_versions (
                        id uuid PRIMARY KEY,
                        created_at timestamp(6) with time zone NOT NULL,
                        updated_at timestamp(6) with time zone NOT NULL,
                        schema_id varchar(80) NOT NULL,
                        name varchar(120) NOT NULL,
                        topic varchar(80) NOT NULL,
                        version varchar(24) NOT NULL,
                        definition jsonb NOT NULL,
                        lifecycle_status varchar(16) NOT NULL
                    )
                    """);
            statement.execute("CREATE UNIQUE INDEX uk_schema_versions_identity " +
                    "ON schema_versions (schema_id, version)");
            statement.executeUpdate("INSERT INTO schema_versions " +
                    "(id, created_at, updated_at, schema_id, name, topic, version, definition, lifecycle_status) " +
                    "VALUES ('" + existingId + "', now(), now(), 'legacy_schema', 'Legacy schema', " +
                    "'KINEMATICS', '1.0', '{\"preserved\":true}'::jsonb, 'APPROVED')");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas("public")
                .defaultSchema("public")
                .baselineVersion(MigrationVersion.fromVersion("6"))
                .load();
        flyway.baseline();
        flyway.migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl,
                POSTGRES.getUsername(), POSTGRES.getPassword()));
        assertEquals(existingId, jdbc.queryForObject(
                "SELECT id FROM schema_versions WHERE schema_id = 'legacy_schema' AND version = '1.0'",
                UUID.class));
        assertEquals(true, jdbc.queryForObject(
                "SELECT definition ->> 'preserved' = 'true' FROM schema_versions WHERE id = ?",
                Boolean.class, existingId));
        assertColumnExists(jdbc, "schema_versions", "definition_checksum");
        assertNull(jdbc.queryForObject(
                "SELECT definition_checksum FROM schema_versions WHERE id = ?", String.class, existingId));
    }

    private static void createHibernateEntitySchema(DataSource dataSource) {
        initializeHibernate(dataSource, "create");
    }

    private static void validateHibernateEntitySchema(DataSource dataSource) {
        initializeHibernate(dataSource, "validate");
    }

    private static void initializeHibernate(DataSource dataSource, String ddlMode) {
        LocalContainerEntityManagerFactoryBean entityManagerFactoryBean =
                new LocalContainerEntityManagerFactoryBean();
        entityManagerFactoryBean.setDataSource(dataSource);
        entityManagerFactoryBean.setPackagesToScan("com.example.backend.entity");
        entityManagerFactoryBean.setPersistenceUnitName("fresh-schema-bootstrap-test");
        entityManagerFactoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        entityManagerFactoryBean.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", ddlMode,
                "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect",
                "hibernate.show_sql", "false",
                // Match Spring Boot's default JPA naming strategies used by the application.
                "hibernate.implicit_naming_strategy",
                "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy",
                "hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        try {
            entityManagerFactoryBean.afterPropertiesSet();
            assertNotNull(entityManagerFactoryBean.getObject(), "Hibernate must initialize the entity model");
        } finally {
            entityManagerFactoryBean.destroy();
        }
    }

    private static boolean tableExists(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_name = ?", Integer.class, table) == 1;
    }

    private static boolean indexExists(JdbcTemplate jdbc, String index) {
        return jdbc.queryForObject("SELECT count(*) FROM pg_indexes " +
                "WHERE schemaname = 'public' AND indexname = ?", Integer.class, index) == 1;
    }

    private static void assertConstraintExists(JdbcTemplate jdbc, String table, String constraint) {
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pg_constraint c " +
                "JOIN pg_class t ON t.oid = c.conrelid " +
                "JOIN pg_namespace n ON n.oid = t.relnamespace " +
                "WHERE n.nspname = 'public' AND t.relname = ? AND c.conname = ?",
                Integer.class, table, constraint), "missing constraint " + constraint + " on " + table);
    }

    private static void assertColumnExists(JdbcTemplate jdbc, String table, String column) {
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                Integer.class, table, column), "missing column " + table + "." + column);
    }

    private static boolean migrationApplied(JdbcTemplate jdbc, String version) {
        return jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history " +
                "WHERE version = ? AND success", Integer.class, version) == 1;
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void createDatabase(String database) throws SQLException {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE \"" + database + "\"");
        }
    }

    private static String jdbcUrl(String database) {
        return POSTGRES.getJdbcUrl().replace("/physlive_fresh_bootstrap_test", "/" + database);
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
