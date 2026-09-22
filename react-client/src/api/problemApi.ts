import axiosClient from "./axios";
import type { Problem, ProblemSummary, Specification } from "../types/physlive";

/**
 * API endpoints for problem management
 * Handles problem creation, extraction, OCR, and ambiguity resolution
 */

export const createProblem = (text: string) => 
  axiosClient.post<Problem>("/problems", { text, sourceMode: "TEXT" }).then(r => r.data);

export const createProblemFromImage = (file: File, text?: string) => {
  const body = new FormData();
  body.append("file", file);
  if (text) body.append("text", text);
  return axiosClient.post<Problem>("/problems/image", body).then(r => r.data);
};

export const extractProblem = (id: string) => 
  axiosClient.post<Problem>(`/problems/${id}/extract`).then(r => r.data);

export const confirmProblem = (id: string, answers: Record<string, string>) => 
  axiosClient.post<Problem>(`/problems/${id}/confirm`, { answers }).then(r => r.data);

export const resolveAmbiguity = (
  specificationId: string, 
  ambiguityId: string, 
  answer: string, 
  comment?: string
) => 
  axiosClient.post<Specification>(
    `/specifications/${specificationId}/ambiguities/${ambiguityId}/confirm`, 
    { answer, comment }
  ).then(r => r.data);

export const updateProblemText = (id: string, text: string) => 
  axiosClient.put<Problem>(`/problems/${id}/text`, { text }).then(r => r.data);

export const updateSpecification = (
  problemId: string,
  specification: Pick<Specification, "objects" | "quantities" | "relations" | "endCondition">
) => axiosClient.put<Problem>(`/problems/${problemId}/specification`, specification).then(r => r.data);

export const problemHistory = (page = 0, size = 20) => 
  axiosClient.get<{ content: ProblemSummary[]; totalElements: number; totalPages: number }>(
    "/problems", 
    { params: { page, size } }
  ).then(r => r.data);

export const getSpecification = (specificationId: string) => 
  axiosClient.get<Specification>(`/specifications/${specificationId}`).then(r => r.data);

export const decideAssets = (specificationId: string, selectionId: string, accepted: boolean) =>
  axiosClient.post<Specification>(`/specifications/${specificationId}/assets/decision`, {
    selectionId,
    accepted,
  }).then(r => r.data);
