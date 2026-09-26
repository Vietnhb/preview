-- B2B roles. Preserve role IDs referenced by existing accounts.
-- Initial published prices from B2B_SYSTEM_DESIGN. Preserve later administrator edits.
CREATE TABLE IF NOT EXISTS license_plans (
    code VARCHAR(40) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    annual_price_vnd BIGINT NOT NULL CHECK (annual_price_vnd > 0),
    student_quota INTEGER NOT NULL CHECK (student_quota > 0),
    monthly_token_quota INTEGER CHECK (monthly_token_quota >= 0),
    active BOOLEAN NOT NULL DEFAULT true
);;
INSERT INTO license_plans (code, name, description, annual_price_vnd, student_quota, monthly_token_quota, active)
VALUES ('STARTER', 'Starter', 'Dành cho trường bắt đầu triển khai lớp học mô phỏng.', 30000000, 500, 100000, true),
       ('PROFESSIONAL', 'Professional', 'Dành cho trường triển khai trên nhiều khối lớp.', 50000000, 1000, 300000, true),
       ('ENTERPRISE', 'Enterprise', 'Dành cho trường quy mô lớn và nhu cầu AI cao.', 200000000, 5000, NULL, true)
ON CONFLICT (code) DO NOTHING;;

CREATE TABLE IF NOT EXISTS school_payments (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL REFERENCES schools(id),
    manager_id INTEGER NOT NULL REFERENCES users(id),
    plan_code VARCHAR(255) NOT NULL,
    amount_vnd BIGINT NOT NULL CHECK (amount_vnd > 0),
    monthly_token_quota INTEGER,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    provider_transaction_no VARCHAR(255)
);;

-- School class management. These tables are required before enabling JPA validate on Supabase.
CREATE TABLE IF NOT EXISTS school_classes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    grade_level INTEGER NOT NULL CHECK (grade_level BETWEEN 10 AND 12),
    school_year VARCHAR(20) NOT NULL,
    subject VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);;
CREATE UNIQUE INDEX IF NOT EXISTS idx_school_class_name_year
    ON school_classes (school_id, lower(name), school_year);;

CREATE TABLE IF NOT EXISTS class_teacher_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID NOT NULL REFERENCES school_classes(id) ON DELETE CASCADE,
    teacher_id INTEGER NOT NULL REFERENCES users(id),
    is_active BOOLEAN NOT NULL DEFAULT true,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT unique_teacher_class UNIQUE (class_id, teacher_id)
);;

CREATE TABLE IF NOT EXISTS class_enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID NOT NULL REFERENCES school_classes(id) ON DELETE CASCADE,
    student_id INTEGER NOT NULL REFERENCES users(id),
    school_year VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'TRANSFERRED', 'DROPPED', 'COMPLETED')),
    enrolled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);;
ALTER TABLE schools ADD COLUMN IF NOT EXISTS student_quota INTEGER CHECK (student_quota >= 0);;
ALTER TABLE school_payments ADD COLUMN IF NOT EXISTS student_quota INTEGER CHECK (student_quota >= 0);;
ALTER TABLE schools ADD COLUMN IF NOT EXISTS plan_code VARCHAR(255);;
ALTER TABLE schools ADD COLUMN IF NOT EXISTS annual_price_vnd BIGINT;;
ALTER TABLE schools ADD COLUMN IF NOT EXISTS next_plan_code VARCHAR(255);;
ALTER TABLE school_payments ADD COLUMN IF NOT EXISTS purpose VARCHAR(255) NOT NULL DEFAULT 'REGISTRATION';;
ALTER TABLE school_payments ADD COLUMN IF NOT EXISTS annual_price_vnd BIGINT;;
ALTER TABLE school_payments ADD COLUMN IF NOT EXISTS previous_plan_code VARCHAR(255);;
ALTER TABLE school_payments ADD COLUMN IF NOT EXISTS license_start DATE;;
ALTER TABLE school_payments ADD COLUMN IF NOT EXISTS license_end DATE;;
ALTER TABLE assignment_submissions ADD COLUMN IF NOT EXISTS score NUMERIC(8,3);;
ALTER TABLE assignment_submissions ADD COLUMN IF NOT EXISTS max_score NUMERIC(8,3);;
ALTER TABLE assignment_submissions ADD COLUMN IF NOT EXISTS feedback TEXT;;
ALTER TABLE assignment_submissions ADD COLUMN IF NOT EXISTS grading_status VARCHAR(24) NOT NULL DEFAULT 'PENDING';;
ALTER TABLE assignment_submissions ADD COLUMN IF NOT EXISTS graded_at TIMESTAMP WITH TIME ZONE;;
ALTER TABLE assignment_submissions ADD COLUMN IF NOT EXISTS graded_by INTEGER REFERENCES users(id);;
ALTER TABLE assignment_submissions ADD COLUMN IF NOT EXISTS retry_allowed BOOLEAN NOT NULL DEFAULT false;;
ALTER TABLE assignment_submissions ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;;
ALTER TABLE assignments ADD COLUMN IF NOT EXISTS grading_criteria JSONB;;
ALTER TABLE assignments ADD COLUMN IF NOT EXISTS max_score NUMERIC(8,3) NOT NULL DEFAULT 10;;
ALTER TABLE assignments ADD COLUMN IF NOT EXISTS auto_grade BOOLEAN NOT NULL DEFAULT false;;
ALTER TABLE library_items ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(16) NOT NULL DEFAULT 'APPROVED';;
ALTER TABLE library_items ADD COLUMN IF NOT EXISTS moderation_comment TEXT;;
ALTER TABLE library_items ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMP WITH TIME ZONE;;
ALTER TABLE library_items ADD COLUMN IF NOT EXISTS moderated_by INTEGER REFERENCES users(id);;
CREATE TABLE IF NOT EXISTS token_usage_audits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), school_id UUID NOT NULL REFERENCES schools(id), user_id INTEGER NOT NULL REFERENCES users(id),
    tokens BIGINT NOT NULL CHECK (tokens >= 0), operation VARCHAR(64) NOT NULL, usage_month DATE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);;
CREATE TABLE IF NOT EXISTS student_action_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), student_id INTEGER NOT NULL REFERENCES users(id), assignment_id UUID NOT NULL,
    action VARCHAR(64) NOT NULL, payload JSONB, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);;
CREATE TABLE IF NOT EXISTS support_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), sender_id INTEGER NOT NULL REFERENCES users(id),
    kind VARCHAR(16) NOT NULL, subject VARCHAR(180) NOT NULL, content TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN', admin_response TEXT, responded_by INTEGER REFERENCES users(id),
    responded_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);;
CREATE TABLE IF NOT EXISTS library_moderation_audits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), library_item_id UUID NOT NULL REFERENCES library_items(id), reviewer_id INTEGER NOT NULL REFERENCES users(id),
    from_status VARCHAR(16), to_status VARCHAR(16) NOT NULL, comment TEXT, decided_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);;

INSERT INTO roles (name)
VALUES ('ADMIN'), ('REVIEWER'), ('SCHOOL_MANAGER'), ('TEACHER'), ('STUDENT')
ON CONFLICT (name) DO NOTHING;;

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
    IF role_name IN ('ADMIN', 'REVIEWER') AND NEW.school_id IS NULL THEN
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
