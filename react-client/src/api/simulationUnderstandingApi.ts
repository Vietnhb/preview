import axiosClient from "./axios";

export type SimulationSourceMode = "TEXT" | "LATEX" | "IMAGE";

export type RecognitionResult = {
  sessionId: string;
  stage: "RECOGNITION" | "RECOGNITION_FAILED";
  recognizedText: string;
  displayText: string;
  sourceMode: SimulationSourceMode;
  confidence: number | null;
  message?: string;
};

export type SimulationParameter = {
  name: string;
  label?: string;
  value: number;
  unit?: string;
  min?: number;
  max?: number;
};

export type SimulationObjectRequirement = { sourceQuote?: string | null; label?: string | null; count?: number | null;
  shape?: string | null; role?: string | null; contextual?: boolean | null; visualDescription?: string | null;
  [key: string]: unknown };
export type SimulationConstraintRequirement = { sourceQuote?: string | null; label?: string | null; count?: number | null;
  kind?: string | null; details?: unknown; [key: string]: unknown };
export type SimulationFixedQuantity = { sourceQuote?: string | null; name?: string | null;
  valueSI?: number | unknown[] | Record<string, unknown> | string | null; unitSI?: string | null;
  provenance?: string | null; valueStructure?: string | null;
  sceneField?: string | null;
  bodyRequirementIndexes?: number[]; constraintRequirementIndexes?: number[];
  referenceObjectIndexes?: number[]; [key: string]: unknown };
export type SimulationSpatialRelation = { sourceQuote?: string | null; subjectObjectIndex?: number | null;
  referenceObjectIndex?: number | null; axis?: string | null; ordering?: string | null;
  relation?: string | null; subject?: string | null; reference?: string | null;
  [key: string]: unknown };

export type IntentResult = {
  sessionId: string;
  stage: "CLARIFY" | "EXPLAIN" | "UNSUPPORTED";
  question?: string;
  explanation?: string;
  parameters?: SimulationParameter[];
  defaults?: string[];
  schemaId?: string;
  message?: string;
  simulationSpec?: {
    physicalObjects?: unknown[];
    interactions?: unknown[];
    initialState?: string | unknown[] | Record<string, unknown> | null;
    requiredObjects?: SimulationObjectRequirement[];
    requiredConstraints?: SimulationConstraintRequirement[];
    fixedQuantities?: SimulationFixedQuantity[];
    qualitativeValues?: SimulationFixedQuantity[];
    inventoryWarnings?: string[];
    sceneWarnings?: string[];
    spatialRelations?: SimulationSpatialRelation[];
    runtimeKind?: "MATTER" | "VISUAL";
    visualIntent?: string | unknown[] | Record<string, unknown> | null;
    visualHints?: unknown[];
    visualEffects?: unknown[];
    displayPlan?: string | unknown[] | Record<string, unknown> | null;
    [key: string]: unknown;
  };
};

export type SimulationValidation = {
  status: "PENDING" | "OK" | "FLAGGED" | "PAUSED" | "UNVERIFIED" | "UNDERSTOOD"
    | "NEEDS_CLARIFICATION" | "VERIFIED_ANALYTICAL" | "VERIFIED_NUMERICAL"
    | "ASSUMPTION_REVIEW" | "VISUAL_ONLY_UNVERIFIED" | "UNSUPPORTED";
  flags: string[];
  metrics?: Record<string, number>;
  executionMethod?: string;
  solverMethod?: string;
  verificationMethod?: string;
  formulaSource?: string;
  capabilityId?: string;
  topicVersion?: string;
  solverVersion?: string;
  referenceSolverVersion?: string;
  absoluteError?: number | null;
  relativeError?: number | null;
  convergenceEvidence?: string[];
  invariantResults?: Record<string, boolean>;
  assumptions?: string[];
  benchmarkSummary?: string | null;
};

export type GeneratedSimulationResult = {
  sessionId: string;
  stage: "SIMULATION";
  code: string | null;
  parameters: SimulationParameter[];
  validation: SimulationValidation;
  simulationSpec?: { runtimeKind?: "MATTER" | "VISUAL";
    visualProgram?: { init: string; step: string; draw: string };
    externalForces?: boolean; friction?: boolean; conservativeInteractions?: boolean;
    durationSeconds?: number; expectedContacts?: Array<[string, string] | { bodyA: string; bodyB: string }>;
    physicalObjects?: unknown[]; interactions?: unknown[];
    initialState?: string | unknown[] | Record<string, unknown> | null;
    requiredObjects?: SimulationObjectRequirement[]; requiredConstraints?: SimulationConstraintRequirement[];
    fixedQuantities?: SimulationFixedQuantity[];
    qualitativeValues?: SimulationFixedQuantity[];
    inventoryWarnings?: string[];
    sceneWarnings?: string[];
    spatialRelations?: SimulationSpatialRelation[];
    visualIntent?: string | unknown[] | Record<string, unknown> | null;
    visualHints?: unknown[]; visualEffects?: unknown[];
    displayPlan?: string | unknown[] | Record<string, unknown> | null;
    [key: string]: unknown;
    plannedScene?: {
      durationSeconds: number;
      gravity: { x: number; y: number };
      bodies: Array<{ id: string; label: string; shape: "circle" | "rectangle";
        x: number; y: number; radius: number; width: number; height: number;
        mass: number; vx: number; vy: number; isStatic: boolean;
        restitution: number; friction: number; frictionAir: number }>;
      constraints: Array<{ bodyA: string | null; bodyB: string | null }>;
    } };
};

export const understandSimulationText = (description: string) =>
  axiosClient.post<IntentResult>("/simulation/understand", { description })
    .then((response) => response.data);

export const recognizeSimulationImage = (file: File, text?: string) => {
  const body = new FormData();
  body.append("file", file);
  if (text?.trim()) body.append("text", text.trim());
  return axiosClient.post<RecognitionResult>("/simulation/understand", body)
    .then((response) => response.data);
};

export const confirmSimulationInput = (sessionId: string, recognizedText: string, correctedText?: string) =>
  axiosClient.post<RecognitionResult | IntentResult>("/simulation/understand", {
    sessionId,
    recognizedText,
    ...(correctedText !== undefined ? { correctedText } : {}),
  }).then((response) => response.data);

export const reviseSimulationIntent = (sessionId: string, text: string) =>
  axiosClient.post<IntentResult>(`/simulation/${encodeURIComponent(sessionId)}/revise`, { text })
    .then((response) => response.data);

export const confirmSimulationExplanation = (sessionId: string) =>
  axiosClient.post<GeneratedSimulationResult | IntentResult>(`/simulation/${encodeURIComponent(sessionId)}/confirm-explanation`, {
    confirmed: true,
  }).then((response) => response.data);

export const getSimulationValidation = (sessionId: string) =>
  axiosClient.get<SimulationValidation>(`/simulation/${encodeURIComponent(sessionId)}/validation`)
    .then((response) => response.data);

export const reportSimulationValidation = (sessionId: string, validation: SimulationValidation,
  parameters: Record<string, number>) =>
  axiosClient.post<SimulationValidation>(`/simulation/${encodeURIComponent(sessionId)}/validation`,
    { ...validation, parameters })
    .then((response) => response.data);
