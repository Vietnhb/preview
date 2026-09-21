# Fresh database bootstrap

This procedure applies only to a verified brand-new, empty PostgreSQL database. The existing V1–V7 Flyway migrations assume Hibernate has created the application's base tables first. Do not use this sequence for an existing database, a partially initialized database, or a database containing production data.

## Legacy V6 repair

One historical partial-bootstrap state is repaired automatically: a database whose latest successful Flyway version is exactly `6` and which does not contain `schema_versions`. The Flyway `beforeMigrate__repair_v6_missing_schema_versions.sql` callback creates only that missing table before V7 runs. V7 remains unchanged and adds `definition_checksum`; V8-V14 then continue in order. V12 creates missing ambiguity/reviewer tables, V13 repeats that repair when V12 was already recorded, and V14 adds the remaining Hibernate-owned tables and indexes observed missing from Supabase. All repairs are additive and preserve existing rows.

The callback does nothing when `schema_versions` already exists or when the current Flyway version is anything other than `6`. It does not delete, rewrite, or backfill existing application rows. A database with a different missing base table or a malformed existing `schema_versions` table still fails closed and requires an audited repair specific to its actual schema.

Before restarting an affected deployment, take a database backup and verify the state with read-only queries:

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT to_regclass('schema_versions');
```

After startup, verify that versions 7 through 14 succeeded and that `schema_versions.definition_checksum` exists. PostgreSQL normally rolls back the failed V7 DDL transaction. If the history table contains an explicit failed V7 row, stop and repair Flyway history only after confirming that V7 made no partial schema change; the application does not silently rewrite Flyway history.

## Verify the database is empty

Before starting the backend, query the target database:

```sql
SELECT count(*)
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE';
```

The result must be `0`. If it is not zero, stop and use the existing environment's normal migration procedure with its database owner.

## Create the Hibernate-owned base schema

Start the backend once with these settings:

| Setting | Value |
|---|---|
| `FLYWAY_ENABLED` | `false` |
| `FLYWAY_BASELINE_ON_MIGRATE` | `false` |
| `JPA_DDL_AUTO` | `update` |
| `SQL_INIT_MODE` | `never` |
| `BOOTSTRAP_CATALOGS_ENABLED` | `false` |

Wait for Spring/JPA initialization to complete, then stop the backend gracefully. Do not direct application traffic to this database during bootstrap.

## Apply the full Flyway chain

Restart the backend with:

| Setting | Value |
|---|---|
| `FLYWAY_ENABLED` | `true` |
| `FLYWAY_BASELINE_ON_MIGRATE` | `true` |
| `JPA_DDL_AUTO` | `validate` |
| `SQL_INIT_MODE` | `never` |
| `BOOTSTRAP_CATALOGS_ENABLED` | `true` |

The configured baseline version is `0`. Flyway should record the baseline and apply V1–V13. Check that `flyway_schema_history` records successful versions `1` through `14`, the `vector` extension, `schema_search_embeddings.projection_version`, the `schema_search_embeddings`, `ambiguity_cases`, and `reviewer_decisions` tables, and the `simulation_runs` contract-identity columns/index exist, and the backend readiness health check is healthy.

After this first successful migration, set `FLYWAY_BASELINE_ON_MIGRATE=false` and keep `FLYWAY_ENABLED=true` and `JPA_DDL_AUTO=validate` for steady-state starts.

The application now fails startup when `FLYWAY_ENABLED=true` is combined with
Hibernate `update`, `create`, or `create-drop`. This guard prevents a normal
deployment from silently mutating a schema outside the migration history.
`docker-compose.yml` uses `JPA_DDL_AUTO=validate` for the same steady-state
rule. The Hibernate mutation modes remain available only while Flyway is
explicitly disabled for the verified empty-database bootstrap step.

The Testcontainers test `FreshSchemaBootstrapMigrationTest` exercises this sequence by creating the entity schema with the application's Hibernate naming strategy, baselining at `0`, applying every migration, validating the final database against the Hibernate entity model, and checking representative V1/V3 indexes and constraints plus V4–V13 objects. V11's simulation-run identity columns are nullable so rows created before that migration remain readable for historical replay; newly persisted runs populate them from the pinned compiled contract.
