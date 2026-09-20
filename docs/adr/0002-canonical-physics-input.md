# Canonical physics input contract

Status: Accepted

The schema boundary resolves aliases, normalizes units, materializes schema defaults, and rejects unknown or duplicate quantities. Runtime physics code consumes an immutable `CanonicalQuantityBag` keyed only by canonical schema keys. `LegacySpecificationAdapter` is the only compatibility path for persisted/root-field payloads and is not part of the new AI contract.

The strict provider validator runs before Jackson binding. Numeric strings, unknown fields, duplicate quantity names, duplicate ambiguity identity, non-finite values, and unsupported end-condition shapes are rejected early.
