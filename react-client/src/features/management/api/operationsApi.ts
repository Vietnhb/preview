import axiosClient from "../../../shared/api/client";

export const validationMetrics = () =>
  axiosClient.get<{
    total: number;
    failed: number;
    failureRate: number
  }>("/admin/metrics/validation").then(r => r.data);

export type ValidationRun = {
  id: string; submissionId: string; topic: string; schemaId: string; schemaVersion: string;
  solverVersion: string; passed: boolean; status: string; errorMessage?: string | null; createdAt: string;
};

export const validationRuns = () => axiosClient.get<ValidationRun[]>("/admin/validation-runs").then(r => r.data);