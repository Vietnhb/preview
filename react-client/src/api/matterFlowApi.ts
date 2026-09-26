import axiosClient from "./axios";

export type MatterSourceMode = "TEXT" | "LATEX" | "IMAGE";

export type RecognitionResult = {
  sessionId: string;
  stage: "RECOGNITION" | "RECOGNITION_FAILED";
  recognizedText: string;
  displayText: string;
  sourceMode: MatterSourceMode;
  confidence: number | null;
  message?: string;
};

export type MatterParameter = {
  name: string;
  label?: string;
  value: number;
  unit?: string;
  min?: number;
  max?: number;
};

export type MatterObjectRequirement = { sourceQuote: string; label: string; count: number;
  shape?: "circle" | "rectangle" | "unspecified" };
export type MatterConstraintRequirement = { sourceQuote: string; label: string; count: number };
export type MatterFixedQuantity = { sourceQuote: string; name: string; valueSI: number; unitSI: string;
  provenance?: "EXPLICIT" | "ASSUMPTION"; valueStructure?: "SCALAR" | "VECTOR_NORM_2D" | "BOOLEAN";
  sceneField?: string;
  bodyRequirementIndexes?: number[]; constraintRequirementIndexes?: number[];
  referenceObjectIndexes?: number[] };
export type MatterSpatialRelation = { sourceQuote: string; subjectObjectIndex: number;
  referenceObjectIndex: number; axis: "x" | "y"; ordering: "LESS_THAN" | "GREATER_THAN" };

export type IntentResult = {
  sessionId: string;
  stage: "CLARIFY" | "EXPLAIN" | "UNSUPPORTED";
  question?: string;
  explanation?: string;
  parameters?: MatterParameter[];
  defaults?: string[];
  schemaId?: string;
  message?: string;
  simulationSpec?: {
    physicalObjects?: string[];
    interactions?: string[];
    initialState?: string;
    requiredObjects?: MatterObjectRequirement[];
    requiredConstraints?: MatterConstraintRequirement[];
    fixedQuantities?: MatterFixedQuantity[];
    qualitativeValues?: MatterFixedQuantity[];
    inventoryWarnings?: string[];
    sceneWarnings?: string[];
    spatialRelations?: MatterSpatialRelation[];
  };
};

export type MatterValidation = {
  status: "PENDING" | "OK" | "FLAGGED" | "PAUSED" | "UNVERIFIED";
  flags: string[];
  metrics?: Record<string, number>;
};

export type MatterSimulationResult = {
  sessionId: string;
  stage: "SIMULATION";
  code: string | null;
  parameters: MatterParameter[];
  validation: MatterValidation;
  simulationSpec?: { runtimeKind?: "MATTER" | "VISUAL";
    visualProgram?: { init: string; step: string; draw: string };
    externalForces?: boolean; friction?: boolean; conservativeInteractions?: boolean;
    durationSeconds?: number; expectedContacts?: Array<[string, string] | { bodyA: string; bodyB: string }>;
    physicalObjects?: string[]; interactions?: string[]; initialState?: string;
    requiredObjects?: MatterObjectRequirement[]; requiredConstraints?: MatterConstraintRequirement[];
    fixedQuantities?: MatterFixedQuantity[];
    qualitativeValues?: MatterFixedQuantity[];
    inventoryWarnings?: string[];
    sceneWarnings?: string[];
    spatialRelations?: MatterSpatialRelation[];
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

export const normalizeMatterText = (sourceMode: "TEXT" | "LATEX", text: string) =>
  axiosClient.post<RecognitionResult>("/matter-flow/normalize", { sourceMode, text })
    .then((response) => response.data);

export const normalizeMatterImage = (file: File, text?: string) => {
  const body = new FormData();
  body.append("file", file);
  if (text?.trim()) body.append("text", text.trim());
  return axiosClient.post<RecognitionResult>("/matter-flow/normalize-image", body)
    .then((response) => response.data);
};

export const confirmMatterInput = (sessionId: string, confirmed: boolean, correctedText?: string) =>
  axiosClient.post<RecognitionResult | IntentResult>(`/matter-flow/${encodeURIComponent(sessionId)}/confirm-input`, {
    confirmed,
    ...(correctedText !== undefined ? { correctedText } : {}),
  }).then((response) => response.data);

export const reviseMatterIntent = (sessionId: string, text: string) =>
  axiosClient.post<IntentResult>(`/matter-flow/${encodeURIComponent(sessionId)}/revise`, { text })
    .then((response) => response.data);

export const confirmMatterExplanation = (sessionId: string) =>
  axiosClient.post<MatterSimulationResult | IntentResult>(`/matter-flow/${encodeURIComponent(sessionId)}/confirm-explanation`, {
    confirmed: true,
  }).then((response) => response.data);

export const getMatterValidation = (sessionId: string) =>
  axiosClient.get<MatterValidation>(`/matter-flow/${encodeURIComponent(sessionId)}/validation`)
    .then((response) => response.data);

export const reportMatterValidation = (sessionId: string, validation: MatterValidation,
  parameters: Record<string, number>) =>
  axiosClient.post<void>(`/matter-flow/${encodeURIComponent(sessionId)}/validation`,
    { ...validation, parameters })
    .then((response) => response.data);
