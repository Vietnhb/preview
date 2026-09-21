-- V12 could be recorded on a partial legacy bootstrap before the Hibernate
-- base tables were present. Flyway never re-runs an applied migration, so
-- repair that exact state with a new additive migration.
DO $$
BEGIN
    -- Partial V6/V12 fixtures may still contain only registry tables. Leave
    -- those histories valid; Hibernate validation remains responsible for
    -- reporting the missing Hibernate-owned base schema.
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
