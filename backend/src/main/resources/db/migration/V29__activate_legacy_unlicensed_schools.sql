-- Some legacy registrations have no payment row at all, but were still
-- created with schools.active = false. They are unlicensed schools, not
-- administrator-disabled schools. Restore them when their manager is active
-- and has no suspension audit; access remains restricted by the license gate.

UPDATE schools s
SET active = TRUE
WHERE s.active = FALSE
  AND s.plan_code IS NULL
  AND s.license_start IS NULL
  AND s.license_end IS NULL
  AND EXISTS (
      SELECT 1
      FROM users manager
      JOIN roles role ON role.id = manager.role_id
      WHERE manager.school_id = s.id
        AND UPPER(role.name) = 'SCHOOL_MANAGER'
        AND manager.active = TRUE
        AND manager.deactivated_at IS NULL
        AND manager.deactivated_by IS NULL
  );
