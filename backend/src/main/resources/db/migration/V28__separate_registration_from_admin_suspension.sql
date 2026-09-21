-- Registration/payment state must not reuse administrator suspension flags.
-- Restore legacy managers/schools created inactive solely because their first
-- registration payment had not completed yet. Explicitly suspended users keep
-- their inactive state because suspension audit fields are populated.

UPDATE schools s
SET active = TRUE
WHERE s.active = FALSE
  AND s.plan_code IS NULL
  AND EXISTS (
      SELECT 1
      FROM school_payments p
      JOIN users manager ON manager.id = p.manager_id
      WHERE p.school_id = s.id
        AND p.purpose = 'REGISTRATION'
        AND p.status IN ('PENDING', 'FAILED', 'REQUIRES_REVIEW')
        AND manager.deactivated_at IS NULL
        AND manager.deactivated_by IS NULL
  );

UPDATE users manager
SET active = TRUE
WHERE manager.active = FALSE
  AND manager.deactivated_at IS NULL
  AND manager.deactivated_by IS NULL
  AND EXISTS (
      SELECT 1
      FROM school_payments p
      WHERE p.manager_id = manager.id
        AND p.purpose = 'REGISTRATION'
        AND p.status IN ('PENDING', 'FAILED', 'REQUIRES_REVIEW')
  );
