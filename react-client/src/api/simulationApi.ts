import axiosClient from "./axios";
import type { ResolvedEnd, Simulation } from "../types/physlive";

/**
 * API endpoints for physics simulation
 * Handles simulation execution, parameter adjustment, and dual validation
 */

export type BackendSimulation = Omit<Simulation, "runId" | "valid" | "elapsedMilliseconds" | "parameters"> & {
  simulationRunId: string;
  validationPassed: boolean;
  computationTimeMs: number;
  adjustableParams?: Record<string, number>;
  parameters?: Record<string, number>;
};

export const normalizeSimulation = (value: BackendSimulation): Simulation => {
  const resolvedEnd: ResolvedEnd = value.resolvedEnd ?? {
    time: value.time.at(-1) ?? 0,
    reason: "unknown",
    conditionReached: false,
  };
  return {
    ...value,
    runId: value.simulationRunId,
    valid: value.validationPassed,
    ready: value.validationPassed,
    parameters: value.adjustableParams ?? value.parameters ?? {},
    resolvedEnd,
    elapsedMilliseconds: value.computationTimeMs
  };
};

export const getSimulation = (simulationId: string) => 
  axiosClient.get<BackendSimulation>(`/simulations/${simulationId}`)
    .then(r => normalizeSimulation(r.data));

export const getSharedSimulation = (simulationId: string) =>
  axiosClient.get<BackendSimulation>(`/simulations/shared/${simulationId}`)
    .then(r => normalizeSimulation(r.data));
