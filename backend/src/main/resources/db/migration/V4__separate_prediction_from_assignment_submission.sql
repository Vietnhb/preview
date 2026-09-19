ALTER TABLE assignment_submissions
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_assignment_submissions_assignment_completed
    ON assignment_submissions (assignment_id, completed_at DESC);
