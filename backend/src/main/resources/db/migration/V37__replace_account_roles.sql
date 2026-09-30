-- Convert existing accounts without changing user IDs or their school membership.
-- ADMIN in the old model becomes MANAGER. No account is promoted to the new ADMIN.
LOCK TABLE roles, users IN ACCESS EXCLUSIVE MODE;
DROP TRIGGER IF EXISTS check_role_school_consistency ON users;
DROP INDEX IF EXISTS idx_one_school_manager_per_school;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM roles WHERE upper(trim(name)) NOT IN
        ('ADMIN', 'MANAGER', 'REVIEWER', 'CONTENT_REVIEWER', 'SCHOOL_MANAGER', 'SCHOOL', 'TEACHER', 'STAFF', 'STUDENT'))
        OR EXISTS (SELECT 1 FROM users u LEFT JOIN roles r ON r.id = u.role_id WHERE r.id IS NULL) THEN
        RAISE EXCEPTION 'Unmapped or missing user role; resolve before migrating';
    END IF;
END $$;

CREATE TEMP TABLE role_account_mapping ON COMMIT DROP AS
SELECT u.id AS user_id, CASE upper(trim(r.name))
    WHEN 'ADMIN' THEN 2 WHEN 'MANAGER' THEN 2
    WHEN 'REVIEWER' THEN 3 WHEN 'CONTENT_REVIEWER' THEN 3
    WHEN 'SCHOOL_MANAGER' THEN 4 WHEN 'SCHOOL' THEN 4
    WHEN 'TEACHER' THEN 5 WHEN 'STAFF' THEN 5
    WHEN 'STUDENT' THEN 6 END AS new_role_id
FROM users u JOIN roles r ON r.id = u.role_id;

-- Park all foreign keys on a temporary role while the canonical IDs are rebuilt.
CREATE TEMP TABLE role_migration_placeholder ON COMMIT DROP AS
SELECT greatest(coalesce(max(id), 0), 6) + 1 AS id FROM roles;
INSERT INTO roles(id, name)
SELECT id, '__ROLE_MIGRATION__' FROM role_migration_placeholder;
UPDATE users SET role_id = (SELECT id FROM role_migration_placeholder);
DELETE FROM roles WHERE id <> (SELECT id FROM role_migration_placeholder);
INSERT INTO roles(id, name) VALUES
    (1, 'ADMIN'), (2, 'MANAGER'), (3, 'REVIEWER'),
    (4, 'SCHOOL'), (5, 'STAFF'), (6, 'STUDENT');
UPDATE users u SET role_id = m.new_role_id
FROM role_account_mapping m WHERE u.id = m.user_id;
DELETE FROM roles WHERE id = (SELECT id FROM role_migration_placeholder);

CREATE OR REPLACE FUNCTION validate_user_school() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE role_name text;
BEGIN
    SELECT name INTO role_name FROM roles WHERE id = NEW.role_id;
    IF role_name IN ('ADMIN', 'MANAGER', 'REVIEWER') AND NEW.school_id IS NULL THEN
        RETURN NEW;
    END IF;
    IF role_name IN ('SCHOOL', 'STAFF', 'STUDENT') AND NEW.school_id IS NOT NULL THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'User role and school are inconsistent' USING ERRCODE = '23514';
END $$;
CREATE TRIGGER check_role_school_consistency BEFORE INSERT OR UPDATE OF role_id, school_id ON users
FOR EACH ROW EXECUTE FUNCTION validate_user_school();
CREATE UNIQUE INDEX idx_one_school_manager_per_school
ON users(school_id) WHERE role_id = 4 AND active = true;

-- Check the retained school associations against the new role contract.
UPDATE users SET role_id = role_id;

ALTER TABLE roles ADD CONSTRAINT ck_roles_canonical_identity CHECK (
    (id = 1 AND name = 'ADMIN') OR (id = 2 AND name = 'MANAGER') OR
    (id = 3 AND name = 'REVIEWER') OR (id = 4 AND name = 'SCHOOL') OR
    (id = 5 AND name = 'STAFF') OR (id = 6 AND name = 'STUDENT')
);
DO $$
BEGIN
    EXECUTE format('ALTER SEQUENCE %s RESTART WITH 7', pg_get_serial_sequence('roles', 'id'));
END $$;
