import axiosClient from "./axios";
import type { Assignment, CreateAssignment, Curriculum, LibraryFolder, LibraryItem, Problem, Simulation, Specification, StudentOption, User } from "../types/physlive";

export const createProblem = (text: string) => axiosClient.post<Problem>("/problems", { text, sourceMode: "TEXT" }).then(r => r.data);
export const createProblemFromImage = (file: File, text?: string) => { const body = new FormData(); body.append("file", file); if (text) body.append("text", text); return axiosClient.post<Problem>("/problems/image", body).then(r => r.data); };
export const extractProblem = (id: string) => axiosClient.post<Problem>(`/problems/${id}/extract`).then(r => r.data);
export const confirmProblem = (id: string, answers: Record<string, string>) => axiosClient.post<Problem>(`/problems/${id}/confirm`, { answers }).then(r => r.data);
export const resolveAmbiguity = (specificationId: string, ambiguityId: string, answer: string, comment?: string) => axiosClient.post<Specification>(`/specifications/${specificationId}/ambiguities/${ambiguityId}/confirm`, { answer, comment }).then(r => r.data);
export const updateProblemText = (id: string, text: string) => axiosClient.put<Problem>(`/problems/${id}/text`, { text }).then(r => r.data);
type BackendSimulation = Omit<Simulation, "runId" | "valid" | "elapsedMilliseconds" | "parameters"> & {
  simulationRunId: string;
  validationPassed: boolean;
  computationTimeMs: number;
  adjustableParams?: Record<string, number>;
  parameters?: Record<string, number>;
};
const normalizeSimulation = (value: BackendSimulation): Simulation => ({
  ...value,
  runId: value.simulationRunId,
  valid: value.validationPassed,
  ready: value.validationPassed,
  parameters: value.adjustableParams ?? value.parameters ?? {},
  elapsedMilliseconds: value.computationTimeMs
});
export const runSimulation = (specificationId: string, schemaId: string, adjustableParams: Record<string, number>) => axiosClient.post<BackendSimulation>("/simulations", { specificationId, schemaId, adjustableParams }).then(r => normalizeSimulation(r.data));
export const adjustSimulation = (simulationId: string, adjustableParams: Record<string, number>) => axiosClient.post<BackendSimulation>("/simulations/adjust", { simulationId, adjustableParams }, { timeout: 20000 }).then(r => normalizeSimulation(r.data));
export const simulationHistory = () => axiosClient.get<BackendSimulation[]>("/simulations").then(r => r.data.map(normalizeSimulation));
export const getSimulation = (simulationId: string) => axiosClient.get<BackendSimulation>(`/simulations/${simulationId}`).then(r => normalizeSimulation(r.data));
export const curriculum = () => axiosClient.get<Curriculum>("/curriculum").then(r => r.data);
export const saveLibrary = (simulationId: string, folderId: string, lessonId: string, title: string, visibility: LibraryItem["visibility"] = "PERSONAL") => axiosClient.post<LibraryItem>("/library", { simulationId, folderId, lessonId, title, visibility }).then(r => r.data);
export const library = (topic?: string) => axiosClient.get<LibraryItem[]>("/library", { params: { topic } }).then(r => r.data);
export const personalLibrary = () => axiosClient.get<LibraryItem[]>("/library/mine").then(r => r.data);
export const libraryFolders = () => axiosClient.get<LibraryFolder[]>("/library/folders").then(r => r.data);
export const createLibraryFolder = (name: string) => axiosClient.post<LibraryFolder>("/library/folders", { name }).then(r => r.data);
export const renameLibraryFolder = (id: string, name: string) => axiosClient.patch<LibraryFolder>(`/library/folders/${id}`, { name }).then(r => r.data);
export const deleteLibraryFolder = (id: string) => axiosClient.delete(`/library/folders/${id}`);
export const studentOptions = () => axiosClient.get<StudentOption[]>("/user/students").then(r => r.data);
export const createAssignment = (request: CreateAssignment) => axiosClient.post<Assignment>("/assignments", request).then(r => r.data);
export const teacherAssignments = () => axiosClient.get<Assignment[]>("/assignments/mine/teacher").then(r => r.data);
export const studentAssignments = () => axiosClient.get<Assignment[]>("/assignments/mine/student").then(r => r.data);
export const adminUsers = () => axiosClient.get<User[]>("/admin/users").then(r => r.data);
export const validationMetrics = () => axiosClient.get<{ total: number; failed: number; failureRate: number }>("/admin/metrics/validation").then(r => r.data);
