import type { Assignment, AssignmentSubmission, StudentOption } from "../../../../types/physlive";

export type AssignmentRecord = {
  assignment: Assignment;
  submissions: AssignmentSubmission[];
};

export type SubmissionFilter = "all" | "submitted" | "pending";

export type SubmissionTableProps = {
  record: AssignmentRecord;
  students: Map<number, StudentOption>;
  filter: SubmissionFilter;
  query: string;
  onGrade: (submissionId: string, score: number, feedback: string) => Promise<void>;
  onReopen: (submissionId: string) => Promise<void>;
};
