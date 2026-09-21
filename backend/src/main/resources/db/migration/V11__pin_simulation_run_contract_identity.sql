-- Pin the execution contract on each simulation run. Existing rows may not
-- have enough trustworthy metadata to backfill every binding field, so the
-- new columns remain nullable for historical compatibility. New application
-- writes populate all fields from the pinned compiled schema and binding.
DO $$
BEGIN
    IF to_regclass('simulation_runs') IS NOT NULL THEN
        ALTER TABLE simulation_runs
            ADD COLUMN IF NOT EXISTS schema_id VARCHAR(80),
            ADD COLUMN IF NOT EXISTS schema_version VARCHAR(24),
            ADD COLUMN IF NOT EXISTS binding_version VARCHAR(24),
            ADD COLUMN IF NOT EXISTS numerical_solver_id VARCHAR(120),
            ADD COLUMN IF NOT EXISTS reference_solver_id VARCHAR(120),
            ADD COLUMN IF NOT EXISTS output_contract_version VARCHAR(40),
            ADD COLUMN IF NOT EXISTS output_contract_checksum VARCHAR(64);

        CREATE INDEX IF NOT EXISTS idx_simulation_runs_contract_identity
            ON simulation_runs (schema_id, schema_version, binding_version);
    END IF;
END $$;
