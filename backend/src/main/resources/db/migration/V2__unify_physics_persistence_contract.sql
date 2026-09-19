-- Historical migration marker.
--
-- Version 2 was already applied to the shared database before migrations were
-- committed to this repository. Keep this version reserved so every new schema
-- change receives a strictly newer version. The current contract is enforced by
-- V3, which is idempotent for databases created before or after this marker.
SELECT 1;
