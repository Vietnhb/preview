import axiosClient from "./axios";
import type { Curriculum } from "../types/physlive";

/**
 * API endpoints for curriculum management
 * Handles curriculum structure and educational content organization
 */

export const curriculum = () => 
  axiosClient.get<Curriculum>("/curriculum").then(r => r.data);
