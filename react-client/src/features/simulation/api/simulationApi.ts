import axiosClient from "../../../shared/api/client";
import type { Simulation } from "../../../shared/types/physlive";

/**
 * API endpoints for physics simulation
 * Handles simulation execution, parameter adjustment, and dual validation
 */

export type BackendSimulation = Omit<Simulation, "runId" | "valid" | "ready" | "elapsedMilliseconds" | "parameters"> & {
  simulationRunId: string;
  success: boolean;
  validationPassed: boolean;
  computationTimeMs: number;
  adjustableParams?: Record<string, number>;
  parameters?: Record<string, number>;
  rawResult?: unknown;
};

export const normalizeSimulation = (value: BackendSimulation): Simulation => {
  return {
    ...value,
    runId: value.simulationRunId,
    valid: value.validationPassed,
    ready: value.success,
    parameters: value.adjustableParams ?? value.parameters ?? {},
    result: value.rawResult ?? value.result,
    elapsedMilliseconds: value.computationTimeMs
  };
};

export const getSimulation = (simulationId: string) => 
  axiosClient.get<BackendSimulation>(`/simulations/${simulationId}`)
    .then(r => normalizeSimulation(r.data));

export const getSharedSimulation = (simulationId: string) =>
  axiosClient.get<BackendSimulation>(`/simulations/shared/${simulationId}`)
    .then(r => normalizeSimulation(r.data));
