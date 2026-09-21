export type User = { id: number; email: string; fullName: string; role: string; active?: boolean; institutionId?: string | null; schoolId?: string | null; schoolName?: string | null; dateOfBirth?: string | null; lastLogin?: string | null; avatarUrl?: string | null; billingRequired?: boolean };

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
  /** Frontend layout capability; absent means the renderer chooses dataPlane. */
  layout?: "dataPlane" | "horizontalTrack" | "projectileRange" | "collisionTrack" | "springBench" | "circuitBoard" | "world";
  actors?: VisualizationActor[];
  props?: string[];
  effects?: string[];
  /** Optional defaults for the versioned scalar-field wave view. */
  wave?: {
    fieldId?: string;
    probeX?: VisualizationBinding;
    displayExaggeration?: number;
  };
  /** Optional AI-generated scene graph. Legacy scene/actors remain supported. */
  sceneGraph?: {
    nodes: VisualizationNode[];
  };
};
export type VisualizationExpressionOperator = "add" | "subtract" | "multiply" | "divide" | "min" | "max" | "abs" | "negate" | "sin" | "cos" | "clamp";
export type VisualizationBinding = number | string | {
  source: "constant" | "series" | "entity" | "quantity";
  key?: string;
  entityId?: string;
  path?: string;
  value?: number;
} | {
  source: "expression";
  operator: VisualizationExpressionOperator;
  args: VisualizationBinding[];
};
/** vectorScene properties.vector accepts the VectorScene drawing contract. */
export type VisualizationNode = {
  id: string;
  type: string;
  layer?: "static" | "trajectory" | "dynamic";
  transform?: Record<string, VisualizationBinding>;
  style?: Record<string, string | number | boolean>;
  properties?: Record<string, unknown>;
  children?: VisualizationNode[];
};
/**
 * A sampled scalar field is versioned independently from legacy particle
 * timeseries. Samples are row-major: one spatial row for each field time.
 */
export type ScalarFieldAxis = {
  /** `key`/`coordinates` are the wire-format names; id/values remain aliases. */
  key?: string;
  id?: string;
  coordinates?: number[];
  values?: number[];
  unit: string;
  label?: string;
  symbol?: string;
};
export type ScalarFieldSamples = {
  encoding?: "time-major";
  values: number[] | number[][];
  /** Required for flattened values when its shape cannot be inferred. */
  shape?: number[];
};
export type ScalarFieldDefinition = {
  id: string;
  version: number | string;
  type: "scalarField";
  physicalDimension: 1 | 2;
  axes: ScalarFieldAxis[];
  shape: number[];
  time: number[];
  /** Row-major field samples: values[timeIndex][spaceIndex]. */
  values: number[] | number[][];
  valueUnit: string;
  timeUnit: string;
  sampling?: { spaceStep?: number; timeStep?: number };
  interpolation?: "linear";
  boundary?: string | { kind?: string; description?: string };
  /** Legacy transport aliases accepted by the frontend normalizer. */
  value?: { unit: string; label?: string; symbol?: string };
  samples?: ScalarFieldSamples;
};
export type ScalarFieldCollection = ScalarFieldDefinition[] | Record<string, ScalarFieldDefinition>;
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
export type Simulation = { simulationId: string; runId: string; specificationId: string; schemaId: string; valid: boolean; ready: boolean; time: number[]; positions: Record<string, number[]>; velocities: Record<string, number[]>; accelerations: Record<string, number[]>; values: Record<string, number[]>; /** Standalone scalar outputs stay separate from sampled `values` series. */ scalarOutputs?: Record<string, number>; /** Versioned spatial data; legacy clients may send either an array or an object-map. */ scalarFields?: ScalarFieldCollection; parameters: Record<string, number>; visualization: VisualizationDefinition; validation: Validation; endCondition?: EndCondition; resolvedEnd?: ResolvedEnd; spec?: SimulationSpec; result?: unknown; elapsedMilliseconds: number };
export type SimulationSummary = { simulationId: string; specificationId: string; schemaId: string; status: string; createdAt: string };
export type Curriculum = { topics: { id: string; name: string; slug: string; enabled: boolean; modules: { id: string; name: string; slug: string; levels: { id: string; name: string; lessons: { id: string; name: string; slug: string }[] }[] }[] }[] };
export type LibraryFolder = { id: string; name: string; itemCount: number; createdAt: string; updatedAt: string };
export type LibraryItem = { id: string; simulationId: string; folderId?: string; lessonId: string; specificationId: string; title: string; topic?: string; validationStatus: string; visibility: "PERSONAL" | "SHARED" | "PUBLIC"; createdAt: string; moderationStatus?: "PENDING" | "APPROVED" | "REJECTED" | "FEATURED"; moderationComment?: string | null; sharedById?: number | null; sharedByName?: string | null; schoolId?: string | null; schoolName?: string | null };
export type StudentOption = { id: number; fullName: string };
export type TeacherClassOption = { id: string; name: string; gradeLevel: number; schoolYear: string; subject?: string | null; students: StudentOption[] };
export type AssignmentActivityType = "PREDICT_OBSERVE_EXPLAIN" | "MEASUREMENT" | "PARAMETER_INVESTIGATION" | "FREE_EXPLORATION";
export type AssignmentQuestions = { prompt: string; activityType?: AssignmentActivityType; measurement?: { seriesSource: string; seriesLabel: string; unit: string; sampleTime: number; tolerance: number }; investigation?: { parameterKey: string; parameterLabel: string; outcomeSource?: string; outcomeLabel?: string } };
export type Assignment = { id: string; libraryItemId: string; libraryItemTitle?: string | null; classId?: string | null; className?: string | null; classGradeLevel?: number | null; specificationId: string; title: string; description?: string; questions: AssignmentQuestions | string; studentIds: number[]; status: string; assignedAt: string; dueAt?: string; predictionSubmitted?: boolean; submissionCompleted?: boolean; completedAt?: string | null; predictions?: { answerText?: string; reasoning?: string; estimatedValue?: number; conclusion?: string } | null; gradingCriteria?: unknown; maxScore?: number; autoGrade?: boolean; score?: number | null; feedback?: string | null; gradingStatus?: "PENDING" | "AI_GRADED" | "TEACHER_CONFIRMED" | "RETURNED" | null; retryAllowed?: boolean };
export type CreateAssignment = { libraryItemId: string; classId?: string; title: string; description?: string; questions: AssignmentQuestions; studentIds: number[]; dueAt?: string; gradingCriteria?: { expectedValue?: number; tolerance?: number; seriesSource?: string; sampleTime?: number; unit?: string }; maxScore?: number; autoGrade?: boolean };
export type AssignmentSubmission = { id: string; assignmentId: string; studentId: number; studentName: string; predictions: unknown; submittedAt: string; completedAt?: string | null; score?: number | null; maxScore?: number | null; feedback?: string | null; gradingStatus?: "PENDING" | "AI_GRADED" | "TEACHER_CONFIRMED" | "RETURNED"; gradedAt?: string | null; retryAllowed?: boolean };
export type ProblemSummary = { id: string; editableText?: string | null; previewText?: string | null; status: string; sourceMode: string; createdAt: string; updatedAt?: string; currentSpecificationId?: string | null; lessonId?: string | null };
export type EvaluationResult = { benchmarkCount: number; precision: number; recall: number; f1: number; kappa: number; incorrectRate: number };
