DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        WHERE rel.relname = 'extraction_runs'
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) ILIKE '%extraction_path%'
    LOOP
        EXECUTE format('ALTER TABLE extraction_runs DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

UPDATE extraction_runs
SET extraction_path = 'AI_PROVIDER'
WHERE extraction_path = 'OPENROUTER';

ALTER TABLE extraction_runs
    ADD CONSTRAINT ck_extraction_runs_path
    CHECK (extraction_path IN ('AI_PROVIDER', 'RULE_BASED'));
