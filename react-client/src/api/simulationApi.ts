import axiosClient from "./axios";
import type { ResolvedEnd, Simulation, SimulationSummary } from "../types/physlive";

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
    reason: "time_limit",
    conditionReached: value.time.length > 0,
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

export const runSimulation = (
  specificationId: string, 
  schemaId: string, 
  adjustableParams: Record<string, number>
) => 
  axiosClient.post<BackendSimulation>("/simulations", { 
    specificationId, 
    schemaId, 
    adjustableParams 
  }).then(r => normalizeSimulation(r.data));

export const adjustSimulation = (simulationId: string, adjustableParams: Record<string, number>) => 
  axiosClient.post<BackendSimulation>(
    "/simulations/adjust", 
    { simulationId, adjustableParams }, 
    { timeout: 20000 }
  ).then(r => normalizeSimulation(r.data));

export const recentSimulationHistory = () =>
  axiosClient.get<SimulationSummary[]>("/simulations/recent")
    .then(r => r.data);

export const getSimulation = (simulationId: string) => 
  axiosClient.get<BackendSimulation>(`/simulations/${simulationId}`)
    .then(r => normalizeSimulation(r.data));

export const getSharedSimulation = (simulationId: string) =>
  axiosClient.get<BackendSimulation>(`/simulations/shared/${simulationId}`)
    .then(r => normalizeSimulation(r.data));
