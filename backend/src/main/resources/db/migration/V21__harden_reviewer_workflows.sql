-- Production reviewer workflow hardening.
-- All changes are additive so existing benchmark, ambiguity and evaluation data remain intact.

ALTER TABLE IF EXISTS benchmark_problems
    ADD COLUMN IF NOT EXISTS status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_by_reference VARCHAR(80),
    ADD COLUMN IF NOT EXISTS archived_reason TEXT;

UPDATE benchmark_problems
SET status = CASE
    WHEN EXISTS (
        SELECT 1 FROM benchmark_adjudications a
        WHERE a.benchmark_problem_id = benchmark_problems.id
    ) THEN 'GOLD_READY'
    WHEN EXISTS (
        SELECT 1 FROM gold_annotations a
        WHERE a.benchmark_problem_id = benchmark_problems.id
    ) THEN 'ANNOTATING'
    WHEN active THEN 'DRAFT'
    ELSE 'ARCHIVED'
END
WHERE status IS NULL OR status = 'DRAFT';

ALTER TABLE IF EXISTS benchmark_problems
    DROP CONSTRAINT IF EXISTS ck_benchmark_problems_status;
ALTER TABLE IF EXISTS benchmark_problems
    ADD CONSTRAINT ck_benchmark_problems_status
    CHECK (status IN ('DRAFT', 'ANNOTATING', 'DISAGREEMENT', 'GOLD_READY', 'ARCHIVED'));

CREATE INDEX IF NOT EXISTS idx_benchmark_problems_status_topic_created
    ON benchmark_problems (status, topic, created_at DESC);

ALTER TABLE IF EXISTS ambiguity_cases
    ADD COLUMN IF NOT EXISTS claimed_by INTEGER,
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS claim_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF to_regclass('ambiguity_cases') IS NOT NULL
       AND to_regclass('users') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1 FROM pg_constraint WHERE conname = 'fk_ambiguity_cases_claimed_by'
       ) THEN
        ALTER TABLE ambiguity_cases
            ADD CONSTRAINT fk_ambiguity_cases_claimed_by
            FOREIGN KEY (claimed_by) REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ambiguity_cases_queue
    ON ambiguity_cases (status, claim_expires_at, created_at ASC);

ALTER TABLE IF EXISTS evaluation_runs
    ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN IF NOT EXISTS requested_by_reference VARCHAR(80),
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS duration_ms BIGINT,
    ADD COLUMN IF NOT EXISTS benchmark_snapshot_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS configuration JSONB,
    ADD COLUMN IF NOT EXISTS failure_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS failure_message TEXT,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE IF EXISTS evaluation_runs
    DROP CONSTRAINT IF EXISTS ck_evaluation_runs_status;
ALTER TABLE IF EXISTS evaluation_runs
    ADD CONSTRAINT ck_evaluation_runs_status
    CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'));

CREATE INDEX IF NOT EXISTS idx_evaluation_runs_status_created
    ON evaluation_runs (status, created_at DESC);
