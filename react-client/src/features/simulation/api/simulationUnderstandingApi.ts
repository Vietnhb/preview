import axiosClient from "../../../shared/api/client";
import type { SolverTimeline, PixiVisualProgram } from "../model/svgScene";
import type { LibraryItem } from "../../../shared/types/physlive";

export type SimulationSourceMode = "TEXT" | "LATEX" | "IMAGE";
export type RecognitionResult = {
  sessionId: string; stage: "RECOGNITION" | "RECOGNITION_FAILED";
  recognizedText: string; displayText: string; sourceMode: SimulationSourceMode;
  confidence: number | null; message?: string;
};
export type SimulationParameter = {
  name: string; label?: string; value: number; unit?: string; min?: number; max?: number; step?: number;
};
/** Which value feeds each input of the law (built by the backend from the signed plan). */
export type SimulationFormulaBinding = {
  quantity: string; label: string; unit: string; source: "PARAMETER" | "FIXED" | "DEFAULT";
  parameter?: string; parameterLabel?: string; value?: number | null;
};
export type SimulationFormula = {
  modelId: string; label?: string; capabilityId: string; canonical: string[]; derived?: string[]; assumptions?: string[];
  bindings?: SimulationFormulaBinding[];
};
export type SimulationSpec = {
  durationSeconds: number; durationParameter?: string; parameters: SimulationParameter[];
  physicsCoverage: "COMPLETE" | "PARTIAL" | "NONE";
  physicsModels: Array<{ id: string; label?: string; capabilityId: string; inputs: Record<string, string | number> }>;
  requiredObjects?: Array<{ count?: number; label?: string; shape?: string }>;
  runtimeKind?: string; visualProgram?: PixiVisualProgram; solverTimeline?: SolverTimeline;
  [key: string]: unknown;
};
export type IntentResult = {
  sessionId: string; stage: "CLARIFY" | "EXPLAIN" | "UNSUPPORTED";
  description: string; schemaId: string; schemaVersion: string; planSignature?: string;
  question?: string; explanation?: string; defaults?: string[]; message?: string;
  simulationSpec?: SimulationSpec; formulas?: SimulationFormula[]; validation?: SimulationValidation;
};
export type SimulationValidation = {
  status: "PENDING" | "FLAGGED" | "PAUSED" | "UNVERIFIED" | "UNDERSTOOD"
    | "NEEDS_CLARIFICATION" | "VERIFIED_ANALYTICAL" | "VERIFIED_NUMERICAL"
    | "ASSUMPTION_REVIEW" | "VISUAL_ONLY_UNVERIFIED" | "UNSUPPORTED";
  flags: string[]; metrics?: Record<string, number>;
  executionMethod?: string; solverMethod?: string; verificationMethod?: string; verificationScope?: string;
  formulaSource?: string; capabilityId?: string; topicVersion?: string; solverVersion?: string;
  referenceSolverVersion?: string; absoluteError?: number | null; relativeError?: number | null;
  convergenceEvidence?: string[]; invariantResults?: Record<string, boolean>; assumptions?: string[];
  benchmarkSummary?: string | null;
};
export type GeneratedSimulationResult = {
  savedParameters?: Record<string, number>;
  formulas?: SimulationFormula[];
  explanation?: string;
  sessionId: string; stage: "SIMULATION"; code: string; schemaId: string; schemaVersion: string;
  description: string; planSignature: string;
  parameters: SimulationParameter[]; validation: SimulationValidation; simulationSpec: SimulationSpec;
};
export const saveGeneratedSimulation = (simulation: GeneratedSimulationResult, parameters: Record<string, number>,
  title: string, folderId: string, lessonId: string) =>
  axiosClient.post<LibraryItem>("/simulation/saved", { simulation, parameters, title, folderId, lessonId }).then(r => r.data);

export const openGeneratedSimulation = (id: string) =>
  axiosClient.get<GeneratedSimulationResult>(`/simulation/saved/${id}`).then(r => r.data);
/** A shared simulation as its author built it; rejects for older saves that have no stored scene. */
export const openSharedGeneratedSimulation = (id: string) =>
  axiosClient.get<GeneratedSimulationResult>(`/simulations/shared/${id}`, { params: { view: "generated" } }).then(r => r.data);
export const updateSavedSimulationVisual = (id: string, simulation: GeneratedSimulationResult) =>
  axiosClient.patch<void>(`/simulation/saved/${id}/visual`, simulation);
export const understandSimulationText = (description: string) =>
  axiosClient.post<IntentResult>("/simulation/understand", { description }).then(response => response.data);

export const recognizeSimulationImage = (file: File, text?: string) => {
  const body = new FormData(); body.append("file", file);
  if (text?.trim()) body.append("text", text.trim());
  return axiosClient.post<RecognitionResult>("/simulation/understand", body).then(response => response.data);
};
export const confirmSimulationInput = (sessionId: string, recognizedText: string, correctedText?: string) =>
  axiosClient.post<RecognitionResult | IntentResult>("/simulation/understand", {
    sessionId, recognizedText, ...(correctedText !== undefined ? { correctedText } : {}),
  }).then(response => response.data);

export const reviseSimulationIntent = (intent: IntentResult, text: string) =>
  understandSimulationText(intent.stage === "CLARIFY" && intent.question
    ? `${intent.description}\n\nCâu hỏi đã hỏi người dùng: ${intent.question}\nNgười dùng trả lời: ${text}`
    : `${intent.description}\n\nYêu cầu bổ sung/chỉnh sửa của người dùng: ${text}`);

export const confirmSimulationExplanation = (intent: IntentResult, renderDiagnostics?: { code: string; message: string }) =>
  axiosClient.post<GeneratedSimulationResult>("/simulation/generate", {
    ...intent, ...(renderDiagnostics ? { renderDiagnostics } : {}),
  }).then(response => response.data);

export const recomputeSimulation = (simulation: GeneratedSimulationResult, parameters: Record<string, number>, signal: AbortSignal) =>
  axiosClient.post<{ solverTimeline: SolverTimeline; validation: SimulationValidation }>("/simulation/compute", {
    schemaId: simulation.schemaId, schemaVersion: simulation.schemaVersion,
    description: simulation.description, planSignature: simulation.planSignature,
    simulationSpec: {
      durationSeconds: simulation.simulationSpec.durationSeconds,
      ...(simulation.simulationSpec.durationParameter ? { durationParameter: simulation.simulationSpec.durationParameter } : {}),
      parameters: simulation.simulationSpec.parameters,
      physicsModels: simulation.simulationSpec.physicsModels,
      physicsCoverage: simulation.simulationSpec.physicsCoverage,
    }, parameters,
  }, { signal }).then(response => response.data);