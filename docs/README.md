# PhysLive documentation map

This directory keeps only documents that are needed to complete and operate the
production backend work. Historical checkpoint reports are intentionally not
kept here; Git history is the archive for superseded documentation.

## Production status

- `implementation/backend-schema-routing-production-report.md` is the single
  implementation status report and evidence ledger for the production prompt.
- `curriculum/official-program-gap-audit.md` is the single human-readable
  curriculum coverage report. It distinguishes internal catalog coverage from
  coverage of the official Vietnamese high-school physics program.

## Operational runbooks

- `implementation/fresh-database-bootstrap.md` documents the verified empty
  database bootstrap sequence and its limitation.
- `implementation/schema-catalog-source-modules.md` documents catalog source
  generation, drift checks, and historical-version handling.

## Architecture and academic evidence

- `adr/` contains accepted architecture decisions. ADRs are retained even when
  implementation evolves because they explain the contract and migration
  choices.
- `physics/formula-index.md` is the cross-model formula and scope index.
- `physics/models/` contains model-specific assumptions and reference evidence.

## Machine-readable curriculum evidence

- `curriculum/coverage.csv` is the coverage gate input.
- `curriculum/registry-ids.json` and `curriculum/source-catalog.json` are inputs
  used by the curriculum scripts.

Do not add another checkpoint report for the same production effort. Update the
single production report or curriculum audit above, or add an ADR/runbook when
the document has a distinct long-lived purpose.
