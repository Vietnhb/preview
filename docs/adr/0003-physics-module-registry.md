# Physics module and schema compilation boundary

Status: Accepted

Numerical and reference solver registries use immutable maps and fail on duplicate IDs. Approved schema definitions are validated and compiled into immutable `CompiledSchema` snapshots, cached by `schemaId@version`. Catalog bootstrap records a SHA-256 definition checksum and fails on drift of an existing published identity; a new version is required for a changed definition.

The current compatibility solver interface remains source-compatible while models migrate to typed quantity binders. New binders must accept `CanonicalQuantityBag`; parameter records remain data/invariant holders and formulas stay in Java.
