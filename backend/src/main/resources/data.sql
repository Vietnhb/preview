-- B2B roles. Preserve role IDs referenced by existing accounts.
INSERT INTO roles (name)
VALUES ('ADMIN'), ('CONTENT_REVIEWER'), ('SCHOOL_MANAGER'), ('TEACHER'), ('STUDENT')
ON CONFLICT (name) DO NOTHING;;

UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'CONTENT_REVIEWER')
WHERE role_id = (SELECT id FROM roles WHERE name = 'REVIEWER');;

-- Migrate only explicit legacy school associations; never guess a user's school.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'users' AND column_name = 'institution_id') THEN
        EXECUTE 'UPDATE users u SET school_id = s.id FROM schools s
                 WHERE u.school_id IS NULL AND u.institution_id = s.id::text
                 AND u.role_id IN (SELECT id FROM roles WHERE name IN (''SCHOOL_MANAGER'', ''TEACHER'', ''STUDENT''))';
    END IF;
END $$;;

-- PostgreSQL does not allow a role lookup inside a CHECK or index predicate.
-- Check role consistency on writes, allowing administrators to repair legacy rows.
CREATE OR REPLACE FUNCTION validate_user_school() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE role_name text;
BEGIN
    SELECT name INTO role_name FROM roles WHERE id = NEW.role_id;
    IF role_name IN ('ADMIN', 'CONTENT_REVIEWER') AND NEW.school_id IS NULL THEN
        RETURN NEW;
    END IF;
    IF role_name IN ('SCHOOL_MANAGER', 'TEACHER', 'STUDENT') AND NEW.school_id IS NOT NULL THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'User role and school are inconsistent' USING ERRCODE = '23514';
END $$;;
DROP TRIGGER IF EXISTS check_role_school_consistency ON users;;
CREATE TRIGGER check_role_school_consistency BEFORE INSERT OR UPDATE OF role_id, school_id ON users
FOR EACH ROW EXECUTE FUNCTION validate_user_school();;

DO $$
DECLARE manager_role_id integer;
BEGIN
    SELECT id INTO manager_role_id FROM roles WHERE name = 'SCHOOL_MANAGER';
    EXECUTE format('CREATE UNIQUE INDEX IF NOT EXISTS idx_one_school_manager_per_school
                    ON users(school_id) WHERE role_id = %s AND active = true', manager_role_id);
END $$;;

-- Historical enrollments must not prevent subsequent transfers in the same year.
ALTER TABLE class_enrollments DROP CONSTRAINT IF EXISTS unique_active_enrollment_per_year;;
CREATE UNIQUE INDEX IF NOT EXISTS idx_active_enrollment_per_year
ON class_enrollments(student_id, school_year) WHERE status = 'ACTIVE';;

-- Existing simulation-count quotas cannot be treated as token allowances.
-- Initialize legacy rows once; preserve explicitly configured unlimited plans.
UPDATE schools SET monthly_token_quota = COALESCE(monthly_token_quota, 0),
                   used_tokens = 0, token_usage_month = date_trunc('month', CURRENT_DATE)::date
WHERE used_tokens IS NULL;;
