-- users N-N permissions. The role stays the account type (users.role_id); what an individual
-- STAFF or REVIEWER account may do is granted through the user_permissions junction:
--   STAFF    : TEACH and/or DEPARTMENT_HEAD_PHYSICS, granted by the SCHOOL account of the same school
--   REVIEWER : CONTENT_EDIT and/or CONTENT_REVIEW, granted by a MANAGER
-- Replaces users.staff_type, users.staff_alias, users.reviewer_can_edit and users.reviewer_can_review.

CREATE TABLE IF NOT EXISTS permissions (
    id SERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    role_id INTEGER NOT NULL REFERENCES roles(id),
    label VARCHAR(120) NOT NULL
);

INSERT INTO permissions (code, role_id, label)
SELECT v.code, r.id, v.label
FROM (VALUES (1, 'TEACH', 'STAFF', 'Giáo viên'),
             (2, 'DEPARTMENT_HEAD_PHYSICS', 'STAFF', 'Tổ trưởng bộ môn Vật Lý'),
             (3, 'CONTENT_EDIT', 'REVIEWER', 'Biên soạn'),
             (4, 'CONTENT_REVIEW', 'REVIEWER', 'Kiểm duyệt')) AS v(position, code, role_name, label)
JOIN roles r ON r.name = v.role_name
ORDER BY v.position
ON CONFLICT (code) DO UPDATE SET role_id = EXCLUDED.role_id, label = EXCLUDED.label;

CREATE TABLE IF NOT EXISTS user_permissions (
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission_id INTEGER NOT NULL REFERENCES permissions(id),
    granted_by INTEGER,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, permission_id)
);

-- A table created by Hibernate during the empty-database bootstrap has no junction key or grantor reference yet.
DO $$
DECLARE existing_key text; key_columns text;
BEGIN
    SELECT c.conname, (SELECT string_agg(a.attname, ',' ORDER BY a.attname) FROM pg_attribute a
                       WHERE a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey))
    INTO existing_key, key_columns
    FROM pg_constraint c WHERE c.conrelid = 'user_permissions'::regclass AND c.contype = 'p';
    IF existing_key IS NOT NULL AND key_columns <> 'permission_id,user_id' THEN
        EXECUTE format('ALTER TABLE user_permissions DROP CONSTRAINT %I', existing_key);
        existing_key := NULL;
    END IF;
    IF existing_key IS NULL THEN
        ALTER TABLE user_permissions ADD PRIMARY KEY (user_id, permission_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'user_permissions'::regclass
                   AND conname = 'fk_user_permissions_granted_by') THEN
        ALTER TABLE user_permissions ADD CONSTRAINT fk_user_permissions_granted_by
            FOREIGN KEY (granted_by) REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_user_permissions_permission ON user_permissions(permission_id);

-- Carry the existing account data over. granted_by stays NULL: the original grantor was never recorded.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()
               AND table_name = 'users' AND column_name = 'staff_type') THEN
        -- Every existing STAFF account teaches; a department head kept the teacher workspace as well.
        EXECUTE 'INSERT INTO user_permissions (user_id, permission_id)
                 SELECT u.id, p.id FROM users u JOIN roles r ON r.id = u.role_id AND r.name = ''STAFF''
                 JOIN permissions p ON p.code = ''TEACH''
                 ON CONFLICT DO NOTHING';
        EXECUTE 'INSERT INTO user_permissions (user_id, permission_id)
                 SELECT u.id, p.id FROM users u JOIN roles r ON r.id = u.role_id AND r.name = ''STAFF''
                 JOIN permissions p ON p.code = ''DEPARTMENT_HEAD_PHYSICS''
                 WHERE u.staff_type = ''DEPARTMENT_HEAD''
                 ON CONFLICT DO NOTHING';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()
               AND table_name = 'users' AND column_name = 'reviewer_can_edit') THEN
        EXECUTE 'INSERT INTO user_permissions (user_id, permission_id)
                 SELECT u.id, p.id FROM users u JOIN roles r ON r.id = u.role_id AND r.name = ''REVIEWER''
                 JOIN permissions p ON p.code = ''CONTENT_EDIT''
                 WHERE u.reviewer_can_edit
                 ON CONFLICT DO NOTHING';
        EXECUTE 'INSERT INTO user_permissions (user_id, permission_id)
                 SELECT u.id, p.id FROM users u JOIN roles r ON r.id = u.role_id AND r.name = ''REVIEWER''
                 JOIN permissions p ON p.code = ''CONTENT_REVIEW''
                 WHERE u.reviewer_can_review
                 ON CONFLICT DO NOTHING';
    END IF;
    -- Stop rather than guess the permissions of an account the old columns left without any.
    IF EXISTS (SELECT 1 FROM users u JOIN roles r ON r.id = u.role_id
               WHERE r.name IN ('STAFF', 'REVIEWER')
                 AND NOT EXISTS (SELECT 1 FROM user_permissions up WHERE up.user_id = u.id)) THEN
        RAISE EXCEPTION 'A STAFF or REVIEWER account has no permission to migrate; grant one before applying V41';
    END IF;
END $$;

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_staff_type_valid;
ALTER TABLE users DROP COLUMN IF EXISTS staff_alias;
ALTER TABLE users DROP COLUMN IF EXISTS staff_type;
ALTER TABLE users DROP COLUMN IF EXISTS reviewer_can_edit;
ALTER TABLE users DROP COLUMN IF EXISTS reviewer_can_review;

-- A permission can only be granted to an account of the role it belongs to.
CREATE OR REPLACE FUNCTION validate_user_permission() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users u JOIN permissions p ON p.role_id = u.role_id
                   WHERE u.id = NEW.user_id AND p.id = NEW.permission_id) THEN
        RAISE EXCEPTION 'Permission does not belong to the role of this user' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS check_user_permission_role ON user_permissions;
CREATE TRIGGER check_user_permission_role BEFORE INSERT OR UPDATE ON user_permissions
FOR EACH ROW EXECUTE FUNCTION validate_user_permission();

-- Checked at commit so a role change and its permission changes can be written in any order:
-- no permission of a former role may remain, and STAFF and REVIEWER accounts hold at least one permission.
CREATE OR REPLACE FUNCTION validate_user_permission_set() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE target integer; role_name text;
BEGIN
    IF TG_TABLE_NAME = 'users' THEN target := NEW.id; ELSE target := OLD.user_id; END IF;
    SELECT r.name INTO role_name FROM users u JOIN roles r ON r.id = u.role_id WHERE u.id = target;
    IF role_name IS NULL THEN RETURN NULL; END IF;
    IF EXISTS (SELECT 1 FROM user_permissions up JOIN permissions p ON p.id = up.permission_id
               JOIN users u ON u.id = up.user_id WHERE up.user_id = target AND p.role_id <> u.role_id) THEN
        RAISE EXCEPTION 'User % holds a permission of another role', target USING ERRCODE = '23514';
    END IF;
    IF role_name IN ('STAFF', 'REVIEWER')
       AND NOT EXISTS (SELECT 1 FROM user_permissions up WHERE up.user_id = target) THEN
        RAISE EXCEPTION '% account % needs at least one permission', role_name, target USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END $$;
DROP TRIGGER IF EXISTS check_user_permission_set_on_users ON users;
CREATE CONSTRAINT TRIGGER check_user_permission_set_on_users AFTER INSERT OR UPDATE OF role_id ON users
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_user_permission_set();
DROP TRIGGER IF EXISTS check_user_permission_set_on_grants ON user_permissions;
CREATE CONSTRAINT TRIGGER check_user_permission_set_on_grants AFTER DELETE ON user_permissions
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_user_permission_set();

COMMENT ON TABLE permissions IS 'Grantable permissions; each belongs to one role (STAFF: TEACH, DEPARTMENT_HEAD_PHYSICS; REVIEWER: CONTENT_EDIT, CONTENT_REVIEW)';
COMMENT ON TABLE user_permissions IS 'users N-N permissions. granted_by is the SCHOOL (for STAFF) or MANAGER (for REVIEWER) account that granted it';
