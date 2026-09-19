import axiosClient from "./axios";
import type { Assignment, AssignmentSubmission, CreateAssignment, StudentOption } from "../types/physlive";
import { normalizeSimulation, type BackendSimulation } from "./simulationApi";

/**
 * API endpoints for assignment management
 * Handles assignment creation, submission, and student management
 */

export const studentOptions = () => 
  axiosClient.get<StudentOption[]>("/user/students").then(r => r.data);

export const createAssignment = (request: CreateAssignment) => 
  axiosClient.post<Assignment>("/assignments", request).then(r => r.data);

export const teacherAssignments = () => 
  axiosClient.get<Assignment[]>("/assignments/mine/teacher").then(r => r.data);

export const studentAssignments = () => 
  axiosClient.get<Assignment[]>("/assignments/mine/student").then(r => r.data);

export const assignedSimulation = (assignmentId: string) =>
  axiosClient.get<BackendSimulation>(`/assignments/${assignmentId}/simulation`).then(r => normalizeSimulation(r.data));

export const adjustAssignedSimulation = (
  assignmentId: string,
  simulationId: string,
  adjustableParams: Record<string, number>
) => axiosClient.post<BackendSimulation>(
  `/assignments/${assignmentId}/simulation/adjust`,
  { simulationId, adjustableParams },
  { timeout: 20000 }
).then(r => normalizeSimulation(r.data));

export const assignmentSubmissions = (assignmentId: string) => 
  axiosClient.get<AssignmentSubmission[]>(`/assignments/${assignmentId}/submissions`).then(r => r.data);
export type AssignmentReport = { assigned: number; submitted: number; pending: number; graded: number; confirmed: number; retryAllowed: number; averageScore?: number | null; maxScore: number };
export const assignmentReport = (assignmentId: string) => axiosClient.get<AssignmentReport>(`/assignments/${assignmentId}/report`).then(r => r.data);

export const gradeAssignmentSubmission = (assignmentId: string, submissionId: string, score: number, maxScore: number, feedback: string, confirm = true) =>
  axiosClient.put<AssignmentSubmission>(`/assignments/${assignmentId}/submissions/${submissionId}/grade`, { score, maxScore, feedback, confirm }).then(r => r.data);

export const reopenAssignmentSubmission = (assignmentId: string, submissionId: string) =>
  axiosClient.post<AssignmentSubmission>(`/assignments/${assignmentId}/submissions/${submissionId}/reopen`).then(r => r.data);

export const submitAssignmentPrediction = (assignmentId: string, predictions: unknown) => 
  axiosClient.post<AssignmentSubmission>(
    `/assignments/${assignmentId}/predictions`, 
    { predictions }
  ).then(r => r.data);

export const logStudentAction = (assignmentId: string, action: string, payload?: unknown) =>
  axiosClient.post("/student/action-logs", { assignmentId, action, payload }).then(r => r.data);
