-- Solver bindings belonged to the catalogue that preceded the schema-equation runtime: every
-- current topic pack (metaSchemaVersion 2.0) is computed from its own equations and never
-- looks a binding up. V46 already removed the bindings of the superseded packs.
DROP TABLE IF EXISTS solver_versions;
