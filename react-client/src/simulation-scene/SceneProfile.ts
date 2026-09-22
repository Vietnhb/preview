import type { Simulation, VisualizationPresentation } from "../types/physlive";

/**
 * Compatibility name for older callers. Scene capability selection is a
 * backend/JEV contract now; the client never classifies a scene by matching
 * words in a schema id, label, or OCR text.
 */
export type InferredScenePresentation = VisualizationPresentation;

/** Return only visual intent explicitly approved by the backend. */
export function declaredScenePresentation(simulation: Simulation): VisualizationPresentation {
  return simulation.visualization?.presentation ?? {};
}

/** @deprecated Use declaredScenePresentation. Kept for source compatibility. */
export function inferScenePresentation(simulation: Simulation): InferredScenePresentation {
  return declaredScenePresentation(simulation);
}
