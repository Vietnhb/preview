export type User = { id: number; email: string; fullName: string; role: string; active?: boolean; institutionId?: string | null; schoolId?: string | null; dateOfBirth?: string | null; lastLogin?: string | null; avatarUrl?: string | null };

export type Quantity = {
  name: string; symbol?: string; value: number; originalValue?: number; originalUnit?: string;
  normalizedValue: number; normalizedUnit: string; confidence: number; sourceText?: string;
};

export type EventCondition =
  | { type: "contact"; entities: string[]; quantity?: string; operator?: ">=" | "<=" | ">" | "<" | "=="; value?: number }
  | { type: "collision"; entities: string[]; firstQuantity?: string; secondQuantity?: string };

export type EndCondition =
  | { type: "time_limit"; duration: number }
  | { type: "threshold"; quantity: string; operator: ">=" | "<=" | ">" | "<" | "=="; value: number; maxTime?: number }
  | { type: "event"; event: EventCondition; maxTime?: number }
  | { type: "cycle_count"; quantity: string; count: number; maxTime?: number }
  | { type: "manual"; maxTime?: number };

export type ResolvedEnd = {
  time: number;
  reason: "time_limit" | "threshold" | "event" | "cycle_count" | "manual" | "max_time" | "unknown";
  conditionReached: boolean;
};

export type Ambiguity = { id?: string; code: string; fieldPath?: string; field?: string; question: string; options?: string[]; status?: string; resolution?: string; resolvedAt?: string };
export type ConversationMessage = { id: string; role: "user" | "assistant"; text: string };
export type VisualizationControl = { key: string; label: string; symbol: string; unit: string; min: number; max: number; step: number };
export type VisualizationSeries = { key: string; source: string; label: string; symbol: string; unit: string; color: string };
export type VisualizationActor = {
  id: string;
  asset: string;
  x: string;
  y?: string;
  vx?: string;
  vy?: string;
  ax?: string;
  ay?: string;
  label?: string;
  lane?: number;
};
export type VisualizationPresentation = {
  theme?: string;
  environment?: string;
  actors?: VisualizationActor[];
  props?: string[];
  effects?: string[];
  /** Optional AI-generated scene graph. Legacy scene/actors remain supported. */
  sceneGraph?: {
    nodes: VisualizationNode[];
  };
};
export type VisualizationBinding = number | string | {
  source: "constant" | "series" | "entity" | "quantity";
  key?: string;
  entityId?: string;
  path?: string;
  value?: number;
};
export type VisualizationNode = {
  id: string;
  type: string;
  layer?: "static" | "trajectory" | "dynamic";
  transform?: Record<string, VisualizationBinding>;
  style?: Record<string, string | number | boolean>;
  properties?: Record<string, unknown>;
  children?: VisualizationNode[];
};
export type EntitySpec = Record<string, unknown>;
export type QuantitySpec = Record<string, unknown>;
export type BindingSpec = VisualizationBinding;
export type VisualSpec = VisualizationNode;
export type ControlSpec = VisualizationControl;
export type ChartSpec = Record<string, unknown>;
export type SimulationSpec = {
  /** Legacy field; new specs should use endCondition. */
  duration?: number;
  endCondition?: EndCondition;
  entities: EntitySpec[];
  quantities: QuantitySpec[];
  bindings: BindingSpec[];
  visuals: VisualSpec[];
  controls?: ControlSpec[];
  charts?: ChartSpec[];
};
export type VisualizationDefinition = {
  scene: string;
  controls: VisualizationControl[];
  series: VisualizationSeries[];
  presentation?: VisualizationPresentation;
  spec?: SimulationSpec;
};
export type Specification = {
  id?: string; schemaVersion?: string; schemaId?: string; topic?: string; confidence: number;
  objects: unknown[]; quantities: Quantity[]; relations: unknown[]; endCondition?: EndCondition | null; ambiguity?: unknown;
  ambiguityCases?: Ambiguity[]; ambiguities?: Ambiguity[]; confirmationState: string; validationStatus?: string; validationResult?: unknown;
};
export type Problem = { id: string; editableText?: string; originalText?: string; sourceMode: string; status: string; currentSpecification?: Specification; sourceAssets?: { id: string; originalFilename: string }[] };
export type Validation = { passed: boolean; tolerance: number; checkpoints: { time: number; maxRelativeError: number; passed: boolean }[] };
export type Simulation = { simulationId: string; runId: string; specificationId: string; schemaId: string; valid: boolean; ready: boolean; time: number[]; positions: Record<string, number[]>; velocities: Record<string, number[]>; accelerations: Record<string, number[]>; values: Record<string, number[]>; parameters: Record<string, number>; visualization: VisualizationDefinition; validation: Validation; endCondition?: EndCondition; resolvedEnd?: ResolvedEnd; spec?: SimulationSpec; result?: unknown; elapsedMilliseconds: number };
export type SimulationSummary = { simulationId: string; specificationId: string; schemaId: string; status: string; createdAt: string };
export type Curriculum = { topics: { id: string; name: string; slug: string; enabled: boolean; modules: { id: string; name: string; slug: string; levels: { id: string; name: string; lessons: { id: string; name: string; slug: string }[] }[] }[] }[] };
export type LibraryFolder = { id: string; name: string; itemCount: number; createdAt: string; updatedAt: string };
export type LibraryItem = { id: string; simulationId: string; folderId?: string; lessonId: string; specificationId: string; title: string; topic?: string; validationStatus: string; visibility: "PERSONAL" | "SHARED"; createdAt: string };
export type StudentOption = { id: number; fullName: string };
export type Assignment = { id: string; libraryItemId: string; specificationId: string; title: string; description?: string; questions: unknown; studentIds: number[]; status: string; assignedAt: string; dueAt?: string; predictionSubmitted?: boolean; predictions?: { answerText?: string; reasoning?: string; estimatedValue?: number } | null; gradingCriteria?: unknown; maxScore?: number; autoGrade?: boolean; score?: number | null; feedback?: string | null; gradingStatus?: "PENDING" | "AI_GRADED" | "TEACHER_CONFIRMED" | "RETURNED" | null; retryAllowed?: boolean };
export type CreateAssignment = { libraryItemId: string; title: string; description?: string; questions: { prompt: string }; studentIds: number[]; dueAt?: string; gradingCriteria?: { expectedValue?: number; tolerance?: number }; maxScore?: number; autoGrade?: boolean };
export type AssignmentSubmission = { id: string; assignmentId: string; studentId: number; studentName: string; predictions: unknown; submittedAt: string; score?: number | null; maxScore?: number | null; feedback?: string | null; gradingStatus?: "PENDING" | "AI_GRADED" | "TEACHER_CONFIRMED" | "RETURNED"; gradedAt?: string | null; retryAllowed?: boolean };
export type ProblemSummary = { id: string; editableText?: string | null; previewText?: string | null; status: string; sourceMode: string; createdAt: string; updatedAt?: string; currentSpecificationId?: string | null; lessonId?: string | null };
export type EvaluationResult = { benchmarkCount: number; precision: number; recall: number; f1: number; kappa: number; incorrectRate: number };
