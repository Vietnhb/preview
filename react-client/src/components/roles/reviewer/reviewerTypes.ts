export type ReviewItem = {
  id: string;
  specificationId: string;
  question: string;
  fieldPath: string;
  code: string;
  options: string[];
  problemText: string;
  topic: string;
  quantities: unknown;
  relations: unknown;
};

export type Version = {
  id: string;
  schemaId: string;
  version: string;
  lifecycleStatus: string;
  name?: string;
  topic?: string;
  definition?: unknown;
  solverId?: string;
  outputDefinition?: { referenceSolverId?: string };
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
};

export type Evaluation = {
  benchmarkCount: number;
  precision: number;
  recall: number;
  f1: number;
  kappa: number;
  incorrectRate: number;
};
