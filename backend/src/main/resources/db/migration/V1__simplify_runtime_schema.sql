-- Consolidate redundant runtime tables and columns. The guards also allow a fresh
-- development database to be created later by Hibernate during the transition.
DO $$
BEGIN
    IF to_regclass('simulation_runs') IS NOT NULL THEN
        ALTER TABLE simulation_runs
            ADD COLUMN IF NOT EXISTS validation_checkpoints JSONB NOT NULL DEFAULT '[]'::jsonb,
            ADD COLUMN IF NOT EXISTS validation_error TEXT;

        IF to_regclass('validation_runs') IS NOT NULL THEN
            WITH ranked_validation AS (
                SELECT id, simulation_id, passed, checkpoints, error_message,
                       row_number() OVER (PARTITION BY simulation_id ORDER BY created_at, id) AS sequence
                FROM validation_runs
            ), ranked_simulation AS (
                SELECT id, simulation_id,
                       row_number() OVER (PARTITION BY simulation_id ORDER BY created_at, id) AS sequence
                FROM simulation_runs
            )
            UPDATE simulation_runs target
               SET validation_passed = source.passed,
                   validation_checkpoints = source.checkpoints,
                   validation_error = source.error_message
              FROM ranked_simulation run
              JOIN ranked_validation source
                ON source.simulation_id = run.simulation_id
               AND source.sequence = run.sequence
             WHERE target.id = run.id;
        END IF;

        ALTER TABLE simulation_runs ALTER COLUMN validation_checkpoints DROP DEFAULT;
        CREATE INDEX IF NOT EXISTS idx_simulation_runs_simulation_created
            ON simulation_runs (simulation_id, created_at DESC);
    END IF;

    IF to_regclass('simulations') IS NOT NULL THEN
        ALTER TABLE simulations ADD COLUMN IF NOT EXISTS latest_run_id UUID;
        IF to_regclass('simulation_runs') IS NOT NULL THEN
            UPDATE simulations simulation
               SET latest_run_id = latest.id
              FROM (
                    SELECT DISTINCT ON (simulation_id) id, simulation_id
                    FROM simulation_runs
                    ORDER BY simulation_id, created_at DESC, id DESC
              ) latest
             WHERE simulation.id = latest.simulation_id
               AND simulation.latest_run_id IS NULL;
            ALTER TABLE simulations DROP CONSTRAINT IF EXISTS fk_simulations_latest_run;
            ALTER TABLE simulations ADD CONSTRAINT fk_simulations_latest_run
                FOREIGN KEY (latest_run_id) REFERENCES simulation_runs(id) ON DELETE SET NULL;
        END IF;
        ALTER TABLE simulations DROP COLUMN IF EXISTS latest_result;
    END IF;

    IF to_regclass('schema_versions') IS NOT NULL THEN
        ALTER TABLE schema_versions DROP COLUMN IF EXISTS enabled;
        CREATE UNIQUE INDEX IF NOT EXISTS uk_schema_versions_identity
            ON schema_versions (schema_id, version);
        CREATE INDEX IF NOT EXISTS idx_schema_versions_lifecycle
            ON schema_versions (lifecycle_status, topic, schema_id);
    END IF;

    IF to_regclass('solver_versions') IS NOT NULL THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_solver_versions_identity
            ON solver_versions (schema_id, version);
    END IF;

    DROP TABLE IF EXISTS parameter_snapshots;
    DROP TABLE IF EXISTS topic_module_releases;
    DROP TABLE IF EXISTS validation_runs;

    IF to_regclass('specifications') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_specifications_submission_created
            ON specifications (submission_id, created_at DESC);
    END IF;
    IF to_regclass('ambiguity_cases') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_ambiguity_cases_specification_status
            ON ambiguity_cases (specification_id, status);
    END IF;
    IF to_regclass('assignments') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_assignments_teacher_created
            ON assignments (teacher_id, created_at DESC);
        IF to_regclass('simulation_runs') IS NOT NULL THEN
            ALTER TABLE assignments DROP CONSTRAINT IF EXISTS fk_assignments_assigned_run;
            ALTER TABLE assignments ADD CONSTRAINT fk_assignments_assigned_run
                FOREIGN KEY (assigned_simulation_run_id) REFERENCES simulation_runs(id) ON DELETE SET NULL;
        END IF;
    END IF;
    IF to_regclass('assignment_students') IS NOT NULL THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_assignment_student
            ON assignment_students (assignment_id, student_id);
        CREATE INDEX IF NOT EXISTS idx_assignment_students_student
            ON assignment_students (student_id, assignment_id);
        ALTER TABLE assignment_students DROP CONSTRAINT IF EXISTS fk_assignment_students_student;
        ALTER TABLE assignment_students ADD CONSTRAINT fk_assignment_students_student
            FOREIGN KEY (student_id) REFERENCES users(id);
    END IF;
    IF to_regclass('assignment_submissions') IS NOT NULL THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_assignment_submission_student
            ON assignment_submissions (assignment_id, student_id);
        CREATE INDEX IF NOT EXISTS idx_assignment_submissions_assignment_submitted
            ON assignment_submissions (assignment_id, submitted_at DESC);
    END IF;
    IF to_regclass('student_action_logs') IS NOT NULL THEN
        ALTER TABLE student_action_logs DROP CONSTRAINT IF EXISTS fk_student_action_logs_student;
        ALTER TABLE student_action_logs ADD CONSTRAINT fk_student_action_logs_student
            FOREIGN KEY (student_id) REFERENCES users(id);
        ALTER TABLE student_action_logs DROP CONSTRAINT IF EXISTS fk_student_action_logs_assignment;
        ALTER TABLE student_action_logs ADD CONSTRAINT fk_student_action_logs_assignment
            FOREIGN KEY (assignment_id) REFERENCES assignments(id);
        CREATE INDEX IF NOT EXISTS idx_student_action_logs_student_occurred
            ON student_action_logs (student_id, occurred_at DESC);
    END IF;
    IF to_regclass('users') IS NOT NULL THEN
        ALTER TABLE users DROP CONSTRAINT IF EXISTS fk_users_deactivated_by;
        ALTER TABLE users ADD CONSTRAINT fk_users_deactivated_by
            FOREIGN KEY (deactivated_by) REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;
