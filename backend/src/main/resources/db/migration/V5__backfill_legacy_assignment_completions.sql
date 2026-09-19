-- Before the explicit completion step existed, creating a submission meant
-- the student had handed in the assignment. Preserve that meaning for rows
-- created before completed_at was introduced.
UPDATE assignment_submissions
SET completed_at = submitted_at
WHERE completed_at IS NULL;
