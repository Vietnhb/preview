import type { VisualizationBinding } from "../types/physlive.ts";
import { EMPTY_NUMERIC_SERIES, prepareSimulationData, seriesFor, type RuntimeData } from "../simulation-runtime/SimulationData.ts";
import { interpolateAtTime } from "../simulation-runtime/interpolate.ts";
import type { SceneNode } from "./SceneGraph.ts";

export type ResolvedNode = {
  x: number;
  y: number;
  rotation: number;
  values: Record<string, number>;
};

/** Resolves spec bindings without knowing anything about a physics lesson. */
export class BindingResolver {
  private readonly data: RuntimeData;
  private readonly entities: SceneNode[];

  public constructor(data: RuntimeData, entities: SceneNode[] = []) {
    this.data = data;
    this.entities = entities;
  }

  public resolve(binding: VisualizationBinding | undefined, time: number, index: number, depth = 0): number {
    if (depth > 32) throw new Error('Cyclic or excessively nested entity binding');
    if (typeof binding === "number") return Number.isFinite(binding) ? binding : 0;
    if (typeof binding === "string") return this.resolveSeries(binding, time, index);
    if (!binding) return 0;
    if (binding.source === "expression") {
      const args = binding.args.map(value => this.resolve(value, time, index, depth + 1));
      switch (binding.operator) {
        case "add": return args.reduce((sum, value) => sum + value, 0);
        case "subtract": return (args[0] ?? 0) - (args[1] ?? 0);
        case "multiply": return args.reduce((product, value) => product * value, 1);
        case "divide": return Math.abs(args[1] ?? 0) <= Number.EPSILON ? 0 : (args[0] ?? 0) / (args[1] ?? 1);
        case "min": return args.length ? Math.min(...args) : 0;
        case "max": return args.length ? Math.max(...args) : 0;
        case "abs": return Math.abs(args[0] ?? 0);
        case "negate": return -(args[0] ?? 0);
        case "sin": return Math.sin(args[0] ?? 0);
        case "cos": return Math.cos(args[0] ?? 0);
        case "clamp": return Math.max(args[1] ?? Number.NEGATIVE_INFINITY, Math.min(args[2] ?? Number.POSITIVE_INFINITY, args[0] ?? 0));
      }
    }
    if (binding.source === "constant") return Number.isFinite(binding.value) ? binding.value ?? 0 : 0;
    if (binding.source === "quantity") return this.data.simulation.parameters?.[binding.key ?? ""] ?? 0;
    if (binding.source === "entity") {
      const entity = this.entities.find(item => item.id === binding.entityId);
      const entityBinding = entity?.transform[binding.path ?? "x"];
      if (entityBinding !== undefined) return this.resolve(entityBinding, time, index, depth + 1);
      const entityKey = binding.entityId && binding.path ? `${binding.entityId}.${binding.path}` : binding.key;
      return this.resolveSeries(entityKey, time, index);
    }
    return this.resolveSeries(binding.key, time, index);
  }

  public resolveNode(node: SceneNode, time: number, index: number): ResolvedNode {
    const values: Record<string, number> = {};
    for (const key of Object.keys(node.transform)) {
      values[key] = this.resolve(node.transform[key], time, index);
    }
    return {
      x: values.x ?? 0,
      y: values.y ?? 0,
      rotation: values.rotation ?? 0,
      values,
    };
  }

  public resolveProperty(node: SceneNode, key: string, time: number, index: number): number {
    const value = node.properties[key];
    if (typeof value === "number" || typeof value === "string" || (typeof value === "object" && value !== null)) {
      return this.resolve(value as VisualizationBinding, time, index);
    }
    return 0;
  }

  private resolveSeries(source: string | undefined, time: number, index: number): number {
    const values = source ? seriesFor(this.data, source) : EMPTY_NUMERIC_SERIES;
    return interpolateAtTime(this.data.time, values, time, index);
  }
}

export function resolverFor(simulation: RuntimeData["simulation"]): BindingResolver {
  return new BindingResolver(prepareSimulationData(simulation));
}
