export type Version = {
  id: string;
  schemaId: string;
  version: string;
  lifecycleStatus: string;
  name?: string;
  topic?: string;
  definition?: unknown;
  definitionChecksum?: string;
  recordVersion?: number;
  createdAt: string;
};

export type ModuleRelease = {
  id: string;
  topic: string;
  moduleName: string;
  schemaId: string;
  schemaVersion: string;
  lifecycleStatus: "DRAFT" | "APPROVED" | "RETIRED";
};

export type Benchmark = {
  id: string;
  problemText: string;
  topic: string;
  gradeScope: string;
  sourceCategory: string;
  status: string;
  annotationCount: number;
  canAnnotate: boolean;
  canAdjudicate: boolean;
  annotations: { actor: string; specification: unknown }[];
  goldSpecification: unknown;
  version?: number;
  createdByReference?: string;
  createdAt?: string;
};

export type EvaluationRun = {
  id: string;
  evaluationType: string;
  status: string;
  benchmarkCount: number;
  metrics: Record<string, unknown>;
  report?: string;
  requestedByReference?: string;
  createdAt?: string;
  startedAt?: string;
  completedAt?: string;
  durationMs?: number;
  benchmarkSnapshotHash?: string;
  configuration?: Record<string, unknown>;
  failureCode?: string;
  failureMessage?: string;
};

export type EvaluationPage = {
  items: EvaluationRun[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};