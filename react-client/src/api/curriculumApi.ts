import axiosClient from "./axios";
import type { Curriculum } from "../types/physlive";

/**
 * API endpoints for curriculum management
 * Handles curriculum structure and educational content organization
 */

export const curriculum = () => 
  axiosClient.get<Curriculum>("/curriculum").then(r => r.data);

export const simulationCurriculum = (schemaId: string, schemaVersion: string, signal: AbortSignal) =>
  axiosClient.get<Curriculum>("/curriculum", { params: { schemaId, schemaVersion }, signal }).then(r => r.data);
