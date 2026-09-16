import type { Simulation } from "../types/physlive";

export type NumericSeries = Float32Array;
export type Timeline = Float64Array;
export const EMPTY_NUMERIC_SERIES = new Float32Array(0);

export type RuntimeData = {
  simulation: Simulation;
  time: Timeline;
  positions: Record<string, NumericSeries>;
  velocities: Record<string, NumericSeries>;
  accelerations: Record<string, NumericSeries>;
  values: Record<string, NumericSeries>;
  series: Map<string, NumericSeries>;
  ranges: Map<string, [number, number]>;
  maxAbs: Map<string, number>;
  length: number;
};

const dataCache = new WeakMap<Simulation, RuntimeData>();

function numericSeries(values: number[] | undefined): NumericSeries {
  if (values instanceof Float32Array) return values;
  return values?.length ? new Float32Array(values) : new Float32Array(0);
}

function normalizeGroup(group: Record<string, number[]> | undefined, series: Map<string, NumericSeries>, name: string) {
  const normalized: Record<string, NumericSeries> = {};
  if (!group) return normalized;
  for (const key of Object.keys(group)) {
    const values = numericSeries(group[key]);
    normalized[key] = values;
    series.set(`${name}.${key}`, values);
  }
  return normalized;
}

/** Convert server JSON arrays once, outside the animation hot path. */
export function prepareSimulationData(simulation: Simulation): RuntimeData {
  const cached = dataCache.get(simulation);
  if (cached) return cached;

  const series = new Map<string, NumericSeries>();
  const data: RuntimeData = {
    simulation,
    time: new Float64Array(simulation.time),
    positions: normalizeGroup(simulation.positions, series, "positions"),
    velocities: normalizeGroup(simulation.velocities, series, "velocities"),
    accelerations: normalizeGroup(simulation.accelerations, series, "accelerations"),
    values: normalizeGroup(simulation.values, series, "values"),
    series,
    ranges: new Map(),
    maxAbs: new Map(),
    length: simulation.time.length,
  };

  // A spec may refer to a named series without the storage group prefix. Add
  // aliases only when the name is unambiguous.
  for (const [path, values] of series) {
    const key = path.slice(path.indexOf(".") + 1);
    if (!series.has(key)) series.set(key, values);
  }

  for (const [path, values] of series) {
    let min = Number.POSITIVE_INFINITY;
    let max = Number.NEGATIVE_INFINITY;
    let maxAbs = 0;
    for (const value of values) {
      if (!Number.isFinite(value)) continue;
      min = Math.min(min, value);
      max = Math.max(max, value);
      maxAbs = Math.max(maxAbs, Math.abs(value));
    }
    if (Number.isFinite(min) && Number.isFinite(max)) data.ranges.set(path, [min, max]);
    data.maxAbs.set(path, maxAbs);
  }

  dataCache.set(simulation, data);
  return data;
}

export function seriesFor(data: RuntimeData, source: string | undefined): NumericSeries {
  return source ? data.series.get(source) ?? EMPTY_NUMERIC_SERIES : EMPTY_NUMERIC_SERIES;
}
