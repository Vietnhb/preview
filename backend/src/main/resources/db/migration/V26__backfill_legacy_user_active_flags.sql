-- Older user rows may predate the active column default. Treat them as active
-- unless they were explicitly suspended.
UPDATE users
SET active = TRUE
WHERE active IS NULL;
