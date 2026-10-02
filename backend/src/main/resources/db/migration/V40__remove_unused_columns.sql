-- These obsolete fields have no application reader or writer.
-- Stop rather than discard data if another installation has populated the column.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM extraction_runs WHERE raw_response IS NOT NULL) THEN
        RAISE EXCEPTION 'raw_response contains data; archive it before applying V40';
    END IF;
    IF EXISTS (SELECT 1 FROM specifications WHERE clarification_conversation IS NOT NULL) THEN
        RAISE EXCEPTION 'clarification_conversation contains data; archive it before applying V40';
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE deactivation_reason IS NOT NULL) THEN
        RAISE EXCEPTION 'deactivation_reason contains data; archive it before applying V40';
    END IF;
END $$;

ALTER TABLE extraction_runs DROP COLUMN raw_response;
ALTER TABLE specifications DROP COLUMN clarification_conversation;
ALTER TABLE users DROP COLUMN deactivation_reason;
