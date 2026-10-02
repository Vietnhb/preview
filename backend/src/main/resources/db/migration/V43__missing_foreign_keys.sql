-- Reference columns that were plain values become foreign keys.
-- The migration stops, naming the column and the number of rows, when existing data points
-- at a row that does not exist. Find them with:
--   SELECT * FROM <table> t WHERE t.<column> IS NOT NULL
--     AND NOT EXISTS (SELECT 1 FROM <target> x WHERE x.<target column> = t.<column>);
-- then correct or clear those values and start the application again.
DO $$
DECLARE item record; orphans bigint;
BEGIN
    FOR item IN SELECT * FROM (VALUES
        ('fk_student_action_logs_assignment', 'student_action_logs', 'assignment_id',      'assignments',   'id',   'CASCADE'),
        ('fk_assignment_students_student',    'assignment_students', 'student_id',         'users',         'id',   'CASCADE'),
        ('fk_users_deactivated_by',           'users',               'deactivated_by',     'users',         'id',   'SET NULL'),
        ('fk_reviewer_decisions_actor',       'reviewer_decisions',  'actor_id',           'users',         'id',   'NO ACTION'),
        ('fk_schools_plan',                   'schools',             'plan_code',          'license_plans', 'code', 'NO ACTION'),
        ('fk_schools_next_plan',              'schools',             'next_plan_code',     'license_plans', 'code', 'NO ACTION'),
        ('fk_school_payments_plan',           'school_payments',     'plan_code',          'license_plans', 'code', 'NO ACTION'),
        ('fk_school_payments_previous_plan',  'school_payments',     'previous_plan_code', 'license_plans', 'code', 'NO ACTION')
    ) AS v(constraint_name, table_name, column_name, target_table, target_column, on_delete) LOOP
        CONTINUE WHEN EXISTS (SELECT 1 FROM pg_constraint WHERE conname = item.constraint_name);
        EXECUTE format('SELECT count(*) FROM %I t WHERE t.%I IS NOT NULL AND NOT EXISTS (SELECT 1 FROM %I x WHERE x.%I = t.%I)',
                       item.table_name, item.column_name, item.target_table, item.target_column, item.column_name) INTO orphans;
        IF orphans > 0 THEN
            RAISE EXCEPTION '%.% has % row(s) that reference a missing %.%; correct them before applying V43',
                item.table_name, item.column_name, orphans, item.target_table, item.target_column;
        END IF;
        EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES %I (%I) ON DELETE %s',
                       item.table_name, item.constraint_name, item.column_name, item.target_table, item.target_column, item.on_delete);
    END LOOP;
END $$;
