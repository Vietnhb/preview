# PhysLive documentation map

## Current architecture

- `implementation/simulation-creation-flow.md` describes routing, confirmation,
  generation, sandbox execution, and local controls.
- [Rebuild report](implementation/matter-rebuild-report.md) records the implemented
  flow, real provider/runtime evidence, deleted files, and observed limitations.
- `implementation/schema-catalog-source-modules.md` documents pack sources,
  catalog generation, and the legacy replay archive.

## Operations

- `implementation/fresh-database-bootstrap.md` applies only to a new, empty
  database. It is not a repair procedure for a database with existing data.

## Curriculum and physics evidence

- `curriculum/coverage.csv`, `registry-ids.json`, and `source-catalog.json` are
  the curriculum audit inputs.
- `curriculum/official-program-gap-audit.md` reports the current source and
  coverage gaps.
- `physics/formula-index.md`, `physics/models/`, and
  `physics/model-evidence-manifest.json` preserve model-level formula and test
  evidence. They include legacy specialized models and are not the active topic
  pack catalog; active packs are listed in
  `backend/src/main/resources/schemas/catalog.json`.

Superseded implementation reports are omitted from the working documentation;
Git history remains the archive for those snapshots.
