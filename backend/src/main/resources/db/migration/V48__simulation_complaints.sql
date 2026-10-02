-- Teachers can dispute a simulation; the complaint is a support item of kind COMPLAINT that reviewers answer.
DO $$
DECLARE constraint_name text;
BEGIN
    -- The kind check was created inline, so its name is whatever the database generated.
    FOR constraint_name IN
        SELECT con.conname FROM pg_constraint con
        WHERE con.conrelid = 'support_items'::regclass AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) ILIKE '%kind%'
    LOOP
        EXECUTE format('ALTER TABLE support_items DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

ALTER TABLE support_items
    ADD CONSTRAINT support_items_kind_check CHECK (kind IN ('FEEDBACK', 'MESSAGE', 'COMPLAINT'));

ALTER TABLE support_items ADD COLUMN IF NOT EXISTS simulation_id UUID;

CREATE INDEX IF NOT EXISTS idx_support_items_kind_created ON support_items (kind, created_at DESC);
