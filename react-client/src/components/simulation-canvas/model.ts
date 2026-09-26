import type { Simulation, VisualizationActor, VisualizationPresentation } from "../../types/physlive";

export type OverlayState = {
  grid: boolean;
  trajectory: boolean;
  velocity: boolean;
  acceleration: boolean;
};

export type Point = { x: number; y: number };

export type CanvasPalette = {
  backgroundTop: string;
  backgroundBottom: string;
  panel: string;
  grid: string;
  gridStrong: string;
  text: string;
  muted: string;
  blue: string;
  cyan: string;
  green: string;
  red: string;
  amber: string;
};

export type SceneFrame = {
  ctx: CanvasRenderingContext2D;
  width: number;
  height: number;
  simulation: Simulation;
  index: number;
  time?: number;
  overlays: OverlayState;
  presentation: VisualizationPresentation;
  palette: CanvasPalette;
  cache?: SceneCache;
};

export type TrajectoryCache = {
  path: Path2D;
  builtIndex: number;
};

export type SceneCache = {
  background: { key: string; canvas: HTMLCanvasElement } | null;
  grids: Map<string, HTMLCanvasElement>;
  environments: Map<string, HTMLCanvasElement>;
  rulers: Map<string, HTMLCanvasElement>;
  ranges: Map<string, [number, number]>;
  trajectories: Map<string, TrajectoryCache>;
};

export type ActorFrame = {
  config: VisualizationActor;
  position: Point;
  velocity: Point;
  acceleration: Point;
  distance: number;
  scale?: number;
  rotation?: number;
  label?: string;
};

export function presentationFor(simulation: Simulation): VisualizationPresentation {
  return simulation.visualization?.presentation ?? {};
}

export function paletteFor(_theme: string | undefined, darkMode: boolean): CanvasPalette {
  if (!darkMode) {
    return {
      backgroundTop: "#f8fbff", backgroundBottom: "#eaf0f7", panel: "#ffffff",
      grid: "rgba(148,163,184,.20)", gridStrong: "rgba(100,116,139,.32)",
      text: "#172033", muted: "#64748b", blue: "#2563eb", cyan: "#0891b2",
      green: "#059669", red: "#dc2626", amber: "#d97706",
    };
  }
  return {
    backgroundTop: "#111d33", backgroundBottom: "#050811", panel: "#0f172a",
    grid: "rgba(71,85,105,.23)", gridStrong: "rgba(100,116,139,.34)",
    text: "#f8fafc", muted: "#94a3b8", blue: "#3b82f6", cyan: "#38bdf8",
    green: "#10b981", red: "#f43f5e", amber: "#fbbf24",
  };
}

export function sample(values: number[] | undefined, times: number[], time: number | undefined, index: number): number {
  if (!values?.length) return 0;
  if (time === undefined || times.length < 2 || time <= times[0]) return values[index] ?? values[0] ?? 0;
  const last = times.length - 1;
  if (time >= times[last]) return values[last] ?? 0;
  let cursor = Math.max(0, Math.min(last - 1, index));
  while (cursor > 0 && times[cursor] > time) cursor--;
  while (cursor < last - 1 && times[cursor + 1] <= time) cursor++;
  const t0 = times[cursor];
  const t1 = times[cursor + 1];
  const ratio = t1 - t0 <= Number.EPSILON ? 0 : (time - t0) / (t1 - t0);
  const start = values[cursor] ?? 0;
  return start + ((values[cursor + 1] ?? start) - start) * ratio;
}

const seriesCache = new WeakMap<Simulation, Map<string, number[]>>();

export function readSeries(simulation: Simulation, source: string | undefined): number[] {
  if (!source) return [];
  const cached = seriesCache.get(simulation)?.get(source);
  if (cached) return cached;
  const [group, key] = source.split(".");
  const records: Record<string, Record<string, number[]>> = {
    positions: simulation.positions,
    velocities: simulation.velocities,
    accelerations: simulation.accelerations,
    values: simulation.values,
  };
  const record = records[group];
  const values = record?.[key] ?? [];
  let simulationCache = seriesCache.get(simulation);
  if (!simulationCache) {
    simulationCache = new Map();
    seriesCache.set(simulation, simulationCache);
  }
  simulationCache.set(source, values);
  return values;
}

export function actorState(frame: SceneFrame, config: VisualizationActor): Omit<ActorFrame, "position"> & { world: Point } {
  const { simulation, time, index } = frame;
  const xValues = readSeries(simulation, config.x);
  const yValues = readSeries(simulation, config.y);
  return {
    config,
    world: {
      x: sample(xValues, simulation.time, time, index),
      y: sample(yValues, simulation.time, time, index),
    },
    velocity: {
      x: sample(readSeries(simulation, config.vx), simulation.time, time, index),
      y: sample(readSeries(simulation, config.vy), simulation.time, time, index),
    },
    acceleration: {
      x: sample(readSeries(simulation, config.ax), simulation.time, time, index),
      y: sample(readSeries(simulation, config.ay), simulation.time, time, index),
    },
    distance: xValues.length ? Math.abs(sample(xValues, simulation.time, time, index) - (xValues[0] ?? 0)) : 0,
    label: config.label,
  };
}

export function extent(values: number[], fallback: [number, number] = [-1, 1]): [number, number] {
  let min = Number.POSITIVE_INFINITY;
  let max = Number.NEGATIVE_INFINITY;
  for (const value of values) {
    if (!Number.isFinite(value)) continue;
    min = Math.min(min, value);
    max = Math.max(max, value);
  }
  if (!Number.isFinite(min) || !Number.isFinite(max)) return fallback;
  if (Math.abs(max - min) < Number.EPSILON) return [min - 1, max + 1];
  const padding = (max - min) * 0.12;
  return [min - padding, max + padding];
}

export function mapRange(value: number, min: number, max: number, start: number, end: number): number {
  return start + ((value - min) / Math.max(max - min, Number.EPSILON)) * (end - start);
}

export function hasEffect(frame: SceneFrame, effect: string): boolean {
  return frame.presentation.effects?.includes(effect) ?? false;
}
