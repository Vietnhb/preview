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

export type MatterObjectRequirement = { sourceQuote?: string | null; label?: string | null; count?: number | null;
  shape?: string | null; role?: string | null; contextual?: boolean | null; visualDescription?: string | null;
  [key: string]: unknown };
export type MatterConstraintRequirement = { sourceQuote?: string | null; label?: string | null; count?: number | null;
  kind?: string | null; details?: unknown; [key: string]: unknown };
export type MatterFixedQuantity = { sourceQuote?: string | null; name?: string | null;
  valueSI?: number | unknown[] | Record<string, unknown> | string | null; unitSI?: string | null;
  provenance?: string | null; valueStructure?: string | null;
  sceneField?: string | null;
  bodyRequirementIndexes?: number[]; constraintRequirementIndexes?: number[];
  referenceObjectIndexes?: number[]; [key: string]: unknown };
export type MatterSpatialRelation = { sourceQuote?: string | null; subjectObjectIndex?: number | null;
  referenceObjectIndex?: number | null; axis?: string | null; ordering?: string | null;
  relation?: string | null; subject?: string | null; reference?: string | null;
  [key: string]: unknown };

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
    physicalObjects?: unknown[];
    interactions?: unknown[];
    initialState?: string | unknown[] | Record<string, unknown> | null;
    requiredObjects?: MatterObjectRequirement[];
    requiredConstraints?: MatterConstraintRequirement[];
    fixedQuantities?: MatterFixedQuantity[];
    qualitativeValues?: MatterFixedQuantity[];
    inventoryWarnings?: string[];
    sceneWarnings?: string[];
    spatialRelations?: MatterSpatialRelation[];
    runtimeKind?: "MATTER" | "VISUAL";
    visualIntent?: string | unknown[] | Record<string, unknown> | null;
    visualHints?: unknown[];
    visualEffects?: unknown[];
    displayPlan?: string | unknown[] | Record<string, unknown> | null;
    [key: string]: unknown;
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
    physicalObjects?: unknown[]; interactions?: unknown[];
    initialState?: string | unknown[] | Record<string, unknown> | null;
    requiredObjects?: MatterObjectRequirement[]; requiredConstraints?: MatterConstraintRequirement[];
    fixedQuantities?: MatterFixedQuantity[];
    qualitativeValues?: MatterFixedQuantity[];
    inventoryWarnings?: string[];
    sceneWarnings?: string[];
    spatialRelations?: MatterSpatialRelation[];
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
