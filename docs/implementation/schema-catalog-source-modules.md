# Physics schema catalog source modules

The checked-in source of physics schemas is split under
`backend/src/main/resources/schemas/source/<TOPIC>/`. Each JSON file contains one
complete schema version. Its numeric filename prefix preserves the catalog's
existing iteration order; the remaining path and the JSON identity must agree.
`schemas/catalog.json` remains the generated runtime artifact consumed by the
backend and the repository's scene and curriculum checks.

Run the generator after an approved source edit:

```powershell
node scripts/generate-schema-catalog.mjs --write
```

Check for drift without changing files:

```powershell
node scripts/generate-schema-catalog.mjs --check
```

CI runs the drift check. It rejects duplicate identities, missing order numbers,
invalid topic/module paths, path-to-entry identity mismatches, and generated
artifact differences. `--split` is only for an initial import of an existing
catalog and refuses to replace a populated source directory.

The active source modules currently hold 138 version rows for 74 latest schema
identities. Text corrections and solver-binding changes are published as newer
versions. An additional 28 older published rows remain byte-for-byte represented
in `backend/src/main/resources/schemas/history/published-versions.json`.
Bootstrap loads archived rows as retired before loading active source entries;
they stay out of the retrieval index while pinned historical runs can still
resolve their schema and solver binding. Existing database lifecycle values are
preserved.

At bootstrap, a row with a non-null checksum must match the source checksum as
before. A row with a null checksum is backfilled only if the stored JSON
definition and source definition are structurally identical after recursively
sorting object keys; array order, values, and JSON types still have to match. A
mismatch aborts bootstrap with the affected `schemaId@version` and an instruction
to compare the database row and publish a new version. This guard does not mutate
the stored definition.
