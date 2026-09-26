# Fresh database bootstrap

This procedure applies only to a verified brand-new, empty PostgreSQL database. The existing V1–V7 Flyway migrations assume Hibernate has created the application's base tables first. Do not use this sequence for an existing database, a partially initialized database, or a database containing production data.

## Legacy partial-bootstrap repair

There is no automatic callback for partially initialized databases. Before restarting an affected deployment, take a database backup and verify the state with read-only queries:

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT to_regclass('schema_versions');
```

If `schema_versions` is missing or Flyway history contains a failed migration, stop and perform an audited, environment-specific repair before retrying. The application does not rewrite Flyway history or infer missing tables.

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
| `BOOTSTRAP_CATALOGS_ENABLED` | `false` |

Wait for Spring/JPA initialization to complete, then stop the backend gracefully. Do not direct application traffic to this database during bootstrap.

## Apply the full Flyway chain

Restart the backend with:

| Setting | Value |
|---|---|
| `FLYWAY_ENABLED` | `true` |
| `FLYWAY_BASELINE_ON_MIGRATE` | `true` |
| `JPA_DDL_AUTO` | `validate` |
| `BOOTSTRAP_CATALOGS_ENABLED` | `true` |

The configured baseline version is `0`. Flyway should record the baseline and apply every migration through the current version `36`. Check that `flyway_schema_history` records every migration present in `backend/src/main/resources/db/migration/` as successful, the `ambiguity_cases` and `reviewer_decisions` tables exist, and the `simulation_runs` contract-identity columns/index exist. Migrations V33–V36 add clarification persistence, retire superseded schema catalog entries, remove the obsolete non-curriculum optics route, and remove persisted simulation asset selection while preserving pinned history. Migration V20 removes historical schema-search embedding tables; they must not be required by the JEV runtime. The backend readiness health check must be healthy.

After this first successful migration, set `FLYWAY_BASELINE_ON_MIGRATE=false` and keep `FLYWAY_ENABLED=true` and `JPA_DDL_AUTO=validate` for steady-state starts.

The application now fails startup when `FLYWAY_ENABLED=true` is combined with
Hibernate `update`, `create`, or `create-drop`. This guard prevents a normal
deployment from silently mutating a schema outside the migration history.
`docker-compose.yml` uses `JPA_DDL_AUTO=validate` for the same steady-state
rule. The Hibernate mutation modes remain available only while Flyway is
explicitly disabled for the verified empty-database bootstrap step.

V11's simulation-run identity columns are nullable so rows created before that migration remain readable for historical replay; newly persisted runs populate them from the pinned compiled contract. This runbook does not make an existing or partially initialized database safe to reset or repair; inspect its actual Flyway history and data before any migration action.
