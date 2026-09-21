-- A null active flag is not an authenticated state. Lock any rows that still
-- carry the legacy null value; runtime authentication applies the same rule.
UPDATE users
SET active = FALSE
WHERE active IS NULL;
