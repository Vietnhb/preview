-- Pin the model/catalog context and adjudication rationale used for review decisions.
ALTER TABLE IF EXISTS gold_annotations
    ADD COLUMN IF NOT EXISTS schema_catalog_checksum VARCHAR(120),
    ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(120),
    ADD COLUMN IF NOT EXISTS model_version VARCHAR(120);

ALTER TABLE IF EXISTS benchmark_adjudications
    ADD COLUMN IF NOT EXISTS rationale TEXT;
