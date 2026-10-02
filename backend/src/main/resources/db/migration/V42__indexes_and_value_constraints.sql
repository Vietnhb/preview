-- Production hardening that cannot lose data: indexes for foreign keys used by list and
-- report queries (PostgreSQL does not index foreign keys itself), a non-null users.active
-- flag and value constraints for the payment state columns.

-- An index is skipped with a warning when a column is missing, so a schema that drifted
-- during the Hibernate bootstrap does not block startup.
DO $$
DECLARE item record; missing integer;
BEGIN
    FOR item IN SELECT * FROM (VALUES
        ('idx_users_school',                         'users',                     'school_id'),
        ('idx_users_role',                           'users',                     'role_id'),
        ('idx_library_items_owner',                  'library_items',             'owner_id'),
        ('idx_library_items_simulation',             'library_items',             'simulation_id'),
        ('idx_library_items_folder',                 'library_items',             'folder_id'),
        ('idx_library_items_specification',          'library_items',             'specification_id'),
        ('idx_library_moderation_audits_item',       'library_moderation_audits', 'library_item_id'),
        ('idx_class_enrollments_class',              'class_enrollments',         'class_id'),
        ('idx_class_teacher_assignments_teacher',    'class_teacher_assignments', 'teacher_id'),
        ('idx_simulations_owner',                    'simulations',               'owner_id'),
        ('idx_simulations_specification',            'simulations',               'specification_id'),
        ('idx_problem_submissions_owner',            'problem_submissions',       'owner_id'),
        ('idx_extraction_runs_submission',           'extraction_runs',           'submission_id'),
        ('idx_source_assets_submission',             'source_assets',             'submission_id'),
        ('idx_school_payments_school',               'school_payments',           'school_id'),
        ('idx_assignment_submissions_student',       'assignment_submissions',    'student_id'),
        ('idx_student_action_logs_assignment',       'student_action_logs',       'assignment_id'),
        ('idx_support_items_sender',                 'support_items',             'sender_id'),
        ('idx_gold_annotations_benchmark',           'gold_annotations',          'benchmark_problem_id'),
        ('idx_benchmark_adjudications_benchmark',    'benchmark_adjudications',   'benchmark_problem_id'),
        ('idx_token_usage_audits_school_recorded',   'token_usage_audits',        'school_id,recorded_at')
    ) AS v(index_name, table_name, column_list) LOOP
        SELECT count(*) INTO missing
        FROM unnest(string_to_array(item.column_list, ',')) AS wanted(column_name)
        WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns c
                          WHERE c.table_schema = current_schema() AND c.table_name = item.table_name
                            AND c.column_name = wanted.column_name);
        IF missing = 0 THEN
            EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (%s)', item.index_name, item.table_name, item.column_list);
        ELSE
            RAISE WARNING 'Index % skipped: %.% not found', item.index_name, item.table_name, item.column_list;
        END IF;
    END LOOP;
END $$;

-- V27 already treats a null flag as locked; make the column say so.
UPDATE users SET active = FALSE WHERE active IS NULL;
ALTER TABLE users ALTER COLUMN active SET DEFAULT TRUE;
ALTER TABLE users ALTER COLUMN active SET NOT NULL;

-- Payment states written by SchoolPaymentService. Existing rows with any other value stop the migration.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_school_payments_status') THEN
        ALTER TABLE school_payments ADD CONSTRAINT ck_school_payments_status
            CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'EXPIRED', 'REQUIRES_REVIEW'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_school_payments_purpose') THEN
        ALTER TABLE school_payments ADD CONSTRAINT ck_school_payments_purpose
            CHECK (purpose IN ('REGISTRATION', 'RENEWAL', 'UPGRADE'));
    END IF;
END $$;
