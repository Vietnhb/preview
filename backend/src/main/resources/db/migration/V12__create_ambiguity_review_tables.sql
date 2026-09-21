-- The ambiguity/reviewer entities were introduced after the original
-- Hibernate-owned bootstrap. Existing databases can therefore be at the
-- latest Flyway version while still missing these tables. Create only the
-- missing relations and preserve any rows that are already present.
DO $$
BEGIN
    -- V6 repair fixtures and some historical partial databases contain only
    -- registry tables. Leave those migrations successful; Hibernate validation
    -- will report the missing base schema instead of V12 manufacturing it.
    IF to_regclass('specifications') IS NOT NULL THEN
      CREATE TABLE IF NOT EXISTS ambiguity_cases (
        id UUID NOT NULL,
        created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
        updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
        specification_id UUID NOT NULL,
        code VARCHAR(80) NOT NULL,
        field_path VARCHAR(160) NOT NULL,
        question TEXT NOT NULL,
        options JSONB,
        status VARCHAR(16) NOT NULL,
        resolution TEXT,
        resolved_at TIMESTAMP(6) WITH TIME ZONE,
        CONSTRAINT pk_ambiguity_cases PRIMARY KEY (id),
        CONSTRAINT fk_ambiguity_cases_specification
            FOREIGN KEY (specification_id) REFERENCES specifications(id)
      );

      CREATE INDEX IF NOT EXISTS idx_ambiguity_cases_specification_status
          ON ambiguity_cases (specification_id, status);

      CREATE TABLE IF NOT EXISTS reviewer_decisions (
        id UUID NOT NULL,
        created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
        updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
        ambiguity_case_id UUID NOT NULL,
        actor_id INTEGER NOT NULL,
        actor_role VARCHAR(24) NOT NULL,
        decision_state VARCHAR(24) NOT NULL,
        answer TEXT NOT NULL,
        comment TEXT,
        decided_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
        CONSTRAINT pk_reviewer_decisions PRIMARY KEY (id),
        CONSTRAINT fk_reviewer_decisions_ambiguity_case
            FOREIGN KEY (ambiguity_case_id) REFERENCES ambiguity_cases(id)
      );

      CREATE INDEX IF NOT EXISTS idx_reviewer_decisions_ambiguity_case
          ON reviewer_decisions (ambiguity_case_id, decided_at DESC);
    END IF;
END $$;
