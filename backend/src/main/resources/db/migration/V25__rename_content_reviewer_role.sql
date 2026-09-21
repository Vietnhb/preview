-- Use the shorter REVIEWER role name while preserving existing accounts.
DO $$
DECLARE
    legacy_role_id INTEGER;
    reviewer_role_id INTEGER;
BEGIN
    SELECT id INTO legacy_role_id
    FROM roles
    WHERE name = 'CONTENT_REVIEWER';

    SELECT id INTO reviewer_role_id
    FROM roles
    WHERE name = 'REVIEWER';

    IF legacy_role_id IS NULL THEN
        RETURN;
    END IF;

    IF reviewer_role_id IS NULL THEN
        UPDATE roles
        SET name = 'REVIEWER'
        WHERE id = legacy_role_id;
    ELSE
        UPDATE users
        SET role_id = reviewer_role_id
        WHERE role_id = legacy_role_id;

        DELETE FROM roles
        WHERE id = legacy_role_id;
    END IF;
END $$;
