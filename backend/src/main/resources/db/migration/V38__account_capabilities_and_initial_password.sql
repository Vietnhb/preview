ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS staff_type VARCHAR(32);
ALTER TABLE users ADD COLUMN IF NOT EXISTS reviewer_can_edit BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS reviewer_can_review BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users SET staff_type = 'TEACHER' WHERE staff_type IS NULL AND role_id = (SELECT id FROM roles WHERE name = 'STAFF');
UPDATE users SET reviewer_can_edit = TRUE, reviewer_can_review = TRUE WHERE role_id = (SELECT id FROM roles WHERE name = 'REVIEWER');

ALTER TABLE users ADD CONSTRAINT users_staff_type_valid CHECK (staff_type IS NULL OR staff_type IN ('TEACHER', 'DEPARTMENT_HEAD'));
ALTER TABLE users ADD COLUMN IF NOT EXISTS staff_alias VARCHAR(80)
    GENERATED ALWAYS AS (CASE staff_type WHEN 'DEPARTMENT_HEAD' THEN 'Trưởng bộ môn' WHEN 'TEACHER' THEN 'Giáo viên' ELSE NULL END) STORED;

COMMENT ON COLUMN users.staff_type IS 'STAFF: TEACHER (Giáo viên), DEPARTMENT_HEAD (Trưởng bộ môn)';
COMMENT ON COLUMN users.must_change_password IS 'Provisioned/reset passwords must be replaced before any other authenticated operation';
