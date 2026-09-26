UPDATE schema_versions
SET definition = jsonb_set(
        definition,
        '{visualization,presentation,actors}',
        COALESCE((
            SELECT jsonb_agg(actor - 'asset' - 'assetHint')
            FROM jsonb_array_elements(definition #> '{visualization,presentation,actors}') AS actor
        ), '[]'::jsonb),
        false)
WHERE jsonb_typeof(definition #> '{visualization,presentation,actors}') = 'array';

ALTER TABLE specifications DROP COLUMN IF EXISTS asset_selection;
