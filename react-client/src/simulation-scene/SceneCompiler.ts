import type { Simulation } from "../types/physlive";
import type { RuntimeData } from "../simulation-runtime/SimulationData";
import { compileSceneGraph, flattenSceneGraph, type SceneGraph } from "./SceneGraph";
import { validateVectorScene } from './VectorScene';
import { isSupportedPrimitive } from './PrimitiveCapabilities';

export type SceneValidationResult = { valid: boolean; errors: string[] };

function bindingError(binding: unknown, data: RuntimeData, graph: SceneGraph, depth = 0): string | undefined {
  if (depth > 32) return 'cyclic or excessively nested entity binding';
  if (typeof binding === "number") return Number.isFinite(binding) ? undefined : "must be finite";
  if (typeof binding === "string") return binding.trim() && data.series.has(binding) ? undefined : `series '${binding}' is unavailable`;
  if (!binding || typeof binding !== "object") return "is missing";
  const candidate = binding as Record<string, unknown>;
  const source = candidate.source;
  if (source === "expression") {
    const operator = candidate.operator;
    const args = candidate.args;
    const allowed = new Set(["add", "subtract", "multiply", "divide", "min", "max", "abs", "negate", "sin", "cos", "clamp"]);
    if (typeof operator !== "string" || !allowed.has(operator) || !Array.isArray(args)) return "has an invalid expression";
    const expected = operator === "clamp" ? 3 : ["abs", "negate", "sin", "cos"].includes(operator) ? 1 : undefined;
    if ((expected !== undefined && args.length !== expected) || (expected === undefined && args.length < 2)) return "has invalid expression arity";
    for (const arg of args) {
      const error = bindingError(arg, data, graph, depth + 1);
      if (error) return error;
    }
    return undefined;
  }
  if (source === "constant") return Number.isFinite(candidate.value) ? undefined : "constant value must be finite";
  if (source === "series") {
    const key = typeof candidate.key === "string" ? candidate.key : "";
    return key && data.series.has(key) ? undefined : `series '${key}' is unavailable`;
  }
  if (source === "quantity") {
    const key = typeof candidate.key === "string" ? candidate.key : "";
    return key && Number.isFinite(data.simulation.parameters?.[key]) ? undefined : `quantity '${key}' is unavailable`;
  }
  if (source === "entity") {
    const entityId = typeof candidate.entityId === "string" ? candidate.entityId : "";
    const path = typeof candidate.path === "string" ? candidate.path : "";
    const entity = flattenSceneGraph(graph.nodes).find(node => node.id === entityId);
    return entity && path && typeof entity.transform[path] !== "undefined"
      ? bindingError(entity.transform[path], data, graph, depth + 1)
      : `entity binding '${entityId}.${path}' is unavailable`;
  }
  return "has an unsupported binding source";
}

function validateWaveField(graph: SceneGraph, node: ReturnType<typeof flattenSceneGraph>[number], data: RuntimeData | undefined, errors: string[]) {
  const rawFieldId = node.properties.field;
  const fieldId = typeof rawFieldId === "string" ? rawFieldId.trim() : "";
  if (!fieldId) errors.push(`Wave field ${node.id} is missing required field.`);
  if (!Object.hasOwn(node.properties, "probeX")) errors.push(`Wave field ${node.id} is missing required probeX binding.`);
  if (!data) {
    errors.push(`Wave field ${node.id} cannot be validated without runtime field data.`);
    return;
  }
  if (fieldId && !data.scalarFields.has(fieldId)) {
    const fieldErrors = data.scalarFieldErrors.get(fieldId);
    errors.push(fieldErrors?.join(" ") || `Wave field ${node.id} references unavailable scalar field '${fieldId}'.`);
  }
  if (Object.hasOwn(node.properties, "probeX")) {
    const error = bindingError(node.properties.probeX, data, graph);
    if (error) errors.push(`Wave field ${node.id} probeX ${error}.`);
  }
}

export function validateSceneGraph(graph: SceneGraph, data?: RuntimeData): SceneValidationResult {
  const errors: string[] = [];
  for (const node of flattenSceneGraph(graph.nodes)) {
    if (!node.id) errors.push("Scene node is missing id");
    if (!isSupportedPrimitive(node.type)) errors.push(`Unsupported primitive: ${node.type}`);
    if (node.type === 'vectorScene') {
      errors.push(...validateVectorScene(node.properties.vector, value => {
        if (typeof value === 'number') return Number.isFinite(value) ? undefined : 'must be finite';
        return data ? bindingError(value, data, graph) : 'runtime data is required for bindings';
      }).map(error => `${node.id}: ${error}`));
    }
    if (node.type === "body" && typeof node.transform.x === "undefined") errors.push(`Body ${node.id} is missing transform.x`);
    if (node.type === "waveField") validateWaveField(graph, node, data, errors);
  }
  return { valid: errors.length === 0, errors };
}

/** Public compiler boundary used by CanvasPhysicsScene and future AI specs. */
export function compileSimulationScene(simulation: Simulation): SceneGraph {
  return compileSceneGraph(simulation);
}
