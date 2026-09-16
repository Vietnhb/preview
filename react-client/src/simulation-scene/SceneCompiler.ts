import type { Simulation } from "../types/physlive";
import { compileSceneGraph, flattenSceneGraph, type SceneGraph } from "./SceneGraph";

export type SceneValidationResult = { valid: boolean; errors: string[] };

const KNOWN_PRIMITIVES = new Set([
  "background", "grid", "environment", "ruler", "body", "circle", "rectangle", "line", "arrow", "vector",
  "trajectory", "spring", "rope", "prop", "effect", "text", "graph", "chart", "circuitComponent",
]);

export function validateSceneGraph(graph: SceneGraph): SceneValidationResult {
  const errors: string[] = [];
  for (const node of flattenSceneGraph(graph.nodes)) {
    if (!node.id) errors.push("Scene node is missing id");
    if (!KNOWN_PRIMITIVES.has(node.type)) errors.push(`Unsupported primitive: ${node.type}`);
    if (node.type === "body" && typeof node.transform.x === "undefined") errors.push(`Body ${node.id} is missing transform.x`);
  }
  return { valid: errors.length === 0, errors };
}

/** Public compiler boundary used by CanvasPhysicsScene and future AI specs. */
export function compileSimulationScene(simulation: Simulation): SceneGraph {
  return compileSceneGraph(simulation);
}
