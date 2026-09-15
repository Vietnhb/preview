import axiosClient from "./axios";
import type { Assignment, AssignmentSubmission, CreateAssignment, StudentOption } from "../types/physlive";

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

export const assignmentSubmissions = (assignmentId: string) => 
  axiosClient.get<AssignmentSubmission[]>(`/assignments/${assignmentId}/submissions`).then(r => r.data);

export const submitAssignmentPrediction = (assignmentId: string, predictions: unknown) => 
  axiosClient.post<AssignmentSubmission>(
    `/assignments/${assignmentId}/predictions`, 
    { predictions }
  ).then(r => r.data);
