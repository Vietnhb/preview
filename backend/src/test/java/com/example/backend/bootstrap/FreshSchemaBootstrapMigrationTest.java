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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        assertEquals("9", jdbc.queryForObject("SELECT version FROM flyway_schema_history " +
                "WHERE success ORDER BY installed_rank DESC LIMIT 1", String.class));
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"),
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
        assertTrue(migrationApplied(jdbc, "5"), "V5 backfill migration must run");
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

    private static int count(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
