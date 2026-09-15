import axiosClient from "./axios";
import type { Simulation } from "../types/physlive";

/**
 * API endpoints for physics simulation
 * Handles simulation execution, parameter adjustment, and dual validation
 */

type BackendSimulation = Omit<Simulation, "runId" | "valid" | "elapsedMilliseconds" | "parameters"> & {
  simulationRunId: string;
  validationPassed: boolean;
  computationTimeMs: number;
  adjustableParams?: Record<string, number>;
  parameters?: Record<string, number>;
};

const normalizeSimulation = (value: BackendSimulation): Simulation => ({
  ...value,
  runId: value.simulationRunId,
  valid: value.validationPassed,
  ready: value.validationPassed,
  parameters: value.adjustableParams ?? value.parameters ?? {},
  elapsedMilliseconds: value.computationTimeMs
});

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

export const simulationHistory = () => 
  axiosClient.get<BackendSimulation[]>("/simulations")
    .then(r => r.data.map(normalizeSimulation));

export const getSimulation = (simulationId: string) => 
  axiosClient.get<BackendSimulation>(`/simulations/${simulationId}`)
    .then(r => normalizeSimulation(r.data));
