# Fresh database bootstrap

This procedure applies only to a verified brand-new, empty PostgreSQL database. The existing V1–V7 Flyway migrations assume Hibernate has created the application's base tables first. Do not use this sequence for an existing database, a partially initialized database, or a database containing production data.

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

The configured baseline version is `0`. Flyway should record the baseline and apply V1–V9. Check that `flyway_schema_history` records successful versions `1` through `9`, the `vector` extension and `schema_search_embeddings` table exist, and the backend readiness health check is healthy.

After this first successful migration, set `FLYWAY_BASELINE_ON_MIGRATE=false` and keep `FLYWAY_ENABLED=true` and `JPA_DDL_AUTO=validate` for steady-state starts.

The Testcontainers test `FreshSchemaBootstrapMigrationTest` exercises this sequence by creating the entity schema with the application's Hibernate naming strategy, baselining at `0`, applying every migration, validating the final database against the Hibernate entity model, and checking representative V1/V3 indexes and constraints plus V4–V9 objects.
