import type { Simulation, VisualizationBinding, VisualizationNode } from "../types/physlive";
import { presentationFor } from "../components/simulation-canvas/model";

export type Binding = VisualizationBinding;

export type SceneNode = {
  id: string;
  type: string;
  layer: "static" | "trajectory" | "dynamic";
  transform: Record<string, Binding>;
  style: Record<string, string | number | boolean>;
  properties: Record<string, unknown>;
  children: SceneNode[];
};

export type SceneGraph = {
  id: string;
  nodes: SceneNode[];
  signature: string;
};

const STATIC_TYPES = new Set(["background", "grid", "environment", "ruler", "prop", "line", "rectangle", "circle"]);

function normalizeNode(node: VisualizationNode, parentId: string, index: number): SceneNode {
  const id = node.id || `${parentId}-${node.type}-${index}`;
  const children = (node.children ?? []).map((child, childIndex) => normalizeNode(child, id, childIndex));
  return {
    id,
    type: node.type,
    layer: node.layer ?? (STATIC_TYPES.has(node.type) ? "static" : "dynamic"),
    transform: node.transform ?? {},
    style: node.style ?? {},
    properties: node.properties ?? {},
    children,
  };
}

function binding(value: string | undefined, fallback = 0): Binding {
  return value ?? fallback;
}

function specEntityNode(entity: Record<string, unknown>, index: number): VisualizationNode | null {
  const id = typeof entity.id === "string" ? entity.id : `entity-${index}`;
  const transform = entity.transform;
  return {
    id,
    type: typeof entity.type === "string" ? entity.type : "body",
    layer: "dynamic",
    transform: transform && typeof transform === "object" ? transform as Record<string, Binding> : { x: 0, y: 0 },
    properties: {
      asset: typeof entity.asset === "string" ? entity.asset : "object.block.amber",
      label: typeof entity.label === "string" ? entity.label : undefined,
      ...(typeof entity.properties === "object" && entity.properties !== null ? entity.properties as Record<string, unknown> : {}),
    },
  };
}

/** Build a usable scene from schema presentation/series without inspecting a lesson or schema id. */
function schemaDrivenFallbackGraph(simulation: Simulation): SceneNode[] {
  const presentation = presentationFor(simulation);
  const environment = presentation?.environment ?? "track.engineering";
  const actors = presentation?.actors ?? [];
  const hasSecondDimension = actors.some(actor => Boolean(actor.y));
  const lanes = new Set(actors.map(actor => actor.lane ?? 0));
  // The canvas is always the default coordinate plane. A single scalar series
  // is still plotted on that plane; it must not switch renderer modes.
  const layout = hasSecondDimension || lanes.size <= 1 ? "dataPlane" : "lanes";
  const nodes: SceneNode[] = [
    normalizeNode({ id: "background", type: "background", layer: "static" }, "scene", 0),
    normalizeNode({ id: "grid", type: "grid", layer: "static" }, "scene", 1),
    normalizeNode({
      id: "environment",
      type: "environment",
      layer: "static",
      properties: { environment, layout },
    }, "scene", 2),
  ];

  for (let index = 0; index < actors.length; index++) {
    const actor = actors[index];
    const actorId = actor.id || `body-${index}`;
    nodes.push(normalizeNode({
      id: actorId,
      type: "body",
      layer: "dynamic",
      transform: { x: binding(actor.x), y: binding(actor.y) },
      properties: {
        asset: actor.asset,
        label: actor.label,
        lane: actor.lane ?? 0,
        vx: actor.vx,
        vy: actor.vy,
        ax: actor.ax,
        ay: actor.ay,
        layout,
      },
    }, "scene", 3 + index));

    if (presentation?.effects?.includes("motion.trail")) {
      nodes.push(normalizeNode({
        id: `${actorId}-trajectory`,
        type: "trajectory",
        layer: "trajectory",
        transform: { x: binding(actor.x), y: binding(actor.y) },
        properties: { actorId, lane: actor.lane ?? 0, layout, previewAll: hasSecondDimension },
      }, "scene", 100 + index));
    }
    if (actor.vx || actor.vy) {
      nodes.push(normalizeNode({
        id: `${actorId}-velocity`,
        type: "vector",
        layer: "dynamic",
        transform: { x: binding(actor.x), y: binding(actor.y) },
        style: { color: "green" },
        properties: { actorId, vectorX: actor.vx, vectorY: actor.vy, kind: "velocity", layout },
      }, "scene", 200 + index));
    }
    if (actor.ax || actor.ay) {
      nodes.push(normalizeNode({
        id: `${actorId}-acceleration`,
        type: "vector",
        layer: "dynamic",
        transform: { x: binding(actor.x), y: binding(actor.y) },
        style: { color: "red" },
        properties: { actorId, vectorX: actor.ax, vectorY: actor.ay, kind: "acceleration", layout },
      }, "scene", 300 + index));
    }
    for (const effect of presentation?.effects ?? []) {
      if (effect === "motion.trail") continue;
      nodes.push(normalizeNode({
        id: `${actorId}-${effect}`,
        type: "effect",
        layer: "dynamic",
        properties: { effect, actorId, layout },
      }, "scene", 400 + index));
    }
  }

  for (const prop of presentation?.props ?? []) {
    nodes.push(normalizeNode({
      id: prop,
      type: "prop",
      layer: "dynamic",
      properties: { asset: prop, anchorId: actors[0]?.id, layout },
    }, "scene", nodes.length));
  }
  // Schemas may provide a compact `series` declaration without a hand-authored
  // scene graph. Keep those production simulations visible in the canvas by
  // compiling each declared series into a lightweight graph node.
  const declaredSeries = simulation.visualization?.series ?? [];
  for (let seriesIndex = 0; seriesIndex < declaredSeries.length; seriesIndex++) {
    const series = declaredSeries[seriesIndex];
    const source = typeof series.source === "string" ? series.source : "";
    if (!source) continue;
    nodes.push(normalizeNode({
      id: `series-${series.key || nodes.length}`,
      type: "graph",
      layer: "dynamic",
      properties: { source, label: series.label, unit: series.unit, color: series.color, slot: seriesIndex, total: declaredSeries.length },
    }, "scene", nodes.length));
  }
  nodes.push(normalizeNode({ id: "ruler", type: "ruler", layer: "static", properties: { layout } }, "scene", nodes.length));
  return nodes;
}

/** Compile both the new AI scene graph and the current server presentation. */
export function compileSceneGraph(simulation: Simulation): SceneGraph {
  const visualization = simulation.visualization;
  const spec = simulation.spec ?? visualization.spec;
  const entityNodes = spec?.entities?.map(specEntityNode).filter((node): node is VisualizationNode => node !== null) ?? [];
  const visualNodes = spec?.visuals ?? [];
  const chartNodes = spec?.charts?.map((chart, index) => ({
    id: typeof chart.id === "string" ? chart.id : `chart-${index}`,
    type: "graph",
    layer: "static" as const,
    properties: chart,
  })) ?? [];
  const explicitNodes = visualization.presentation?.sceneGraph?.nodes ?? [...entityNodes, ...visualNodes, ...chartNodes];
  const nodes = explicitNodes?.length
    ? explicitNodes.map((node, index) => normalizeNode(node, "scene", index))
    : schemaDrivenFallbackGraph(simulation);
  const signature = JSON.stringify(nodes);
  return { id: simulation.simulationId, nodes, signature };
}

export function flattenSceneGraph(nodes: SceneNode[], output: SceneNode[] = []): SceneNode[] {
  for (const node of nodes) {
    output.push(node);
    if (node.children.length) flattenSceneGraph(node.children, output);
  }
  return output;
}

export function hasRenderableNodes(graph: SceneGraph): boolean {
  return graph.nodes.length > 0;
}
