ALTER TABLE assignments
    ADD COLUMN IF NOT EXISTS school_class_id UUID;

ALTER TABLE assignments
    ADD CONSTRAINT fk_assignments_school_class
    FOREIGN KEY (school_class_id) REFERENCES school_classes(id);

CREATE INDEX IF NOT EXISTS idx_assignments_school_class_id
    ON assignments(school_class_id);
