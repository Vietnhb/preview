import type { VisualizationBinding } from "../types/physlive";
import { EMPTY_NUMERIC_SERIES, prepareSimulationData, seriesFor, type RuntimeData } from "../simulation-runtime/SimulationData";
import { interpolateAtTime } from "../simulation-runtime/interpolate";
import type { SceneNode } from "./SceneGraph";

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

  public resolve(binding: VisualizationBinding | undefined, time: number, index: number): number {
    if (typeof binding === "number") return Number.isFinite(binding) ? binding : 0;
    if (typeof binding === "string") return this.resolveSeries(binding, time, index);
    if (!binding) return 0;
    if (binding.source === "constant") return Number.isFinite(binding.value) ? binding.value ?? 0 : 0;
    if (binding.source === "quantity") return this.data.simulation.parameters?.[binding.key ?? ""] ?? 0;
    if (binding.source === "entity") {
      const entity = this.entities.find(item => item.id === binding.entityId);
      const entityBinding = entity?.transform[binding.path ?? "x"];
      if (entityBinding) return this.resolve(entityBinding, time, index);
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
