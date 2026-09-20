import type { ScalarFieldDefinition, Simulation } from "../types/physlive";

export type NumericSeries = Float32Array;
export type Timeline = Float64Array;
export const EMPTY_NUMERIC_SERIES = new Float32Array(0);

export type ScalarFieldData = {
  id: string;
  version: number | string;
  physicalDimension: 1 | 2;
  axisKey: string;
  x: Timeline;
  y?: Timeline;
  time: Timeline;
  /** Flattened, time-major samples: values[timeIndex * spaceLength + xIndex]. */
  values: NumericSeries;
  timeLength: number;
  spaceLength: number;
  yLength: number;
  valueUnit: string;
  timeUnit: string;
  valueLabel?: string;
  valueSymbol?: string;
  interpolation: "linear";
  boundary?: ScalarFieldDefinition["boundary"];
};

export type ScalarFieldNormalizationResult = {
  field?: ScalarFieldData;
  errors: string[];
};

export type ScalarFieldProbe = {
  fieldId: string;
  requestedX: number;
  requestedTime: number;
  requestedY?: number;
  x: number;
  y?: number;
  time: number;
  value: number;
  clamped: boolean;
};

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
  /** Versioned fields are separate from legacy point-particle timeseries. */
  scalarFields: Map<string, ScalarFieldData>;
  /** Retained so scene validation can explain malformed referenced fields. */
  scalarFieldErrors: Map<string, string[]>;
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

function isFiniteNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

function strictlyIncreasing(values: readonly number[]): boolean {
  return values.length > 0 && values.every((value, index) => isFiniteNumber(value) && (index === 0 || value > (values[index - 1] ?? value)));
}

function axisCoordinates(field: ScalarFieldDefinition): number[] | undefined {
  const axis = field.axes?.[0];
  return axis?.coordinates ?? axis?.values;
}

function axisCoordinatesAt(field: ScalarFieldDefinition, index: number): number[] | undefined {
  const axis = field.axes?.[index];
  return axis?.coordinates ?? axis?.values;
}

function fieldValues(field: ScalarFieldDefinition): number[] | number[][] | undefined {
  return field.values ?? field.samples?.values;
}

function fieldShape(field: ScalarFieldDefinition): number[] | undefined {
  return field.shape ?? field.samples?.shape;
}

function isValidVersion(version: unknown): version is number | string {
  return (typeof version === "number" && Number.isInteger(version) && version > 0)
    || (typeof version === "string" && version.trim().length > 0);
}

/**
 * Validate and normalize the public scalar-field JSON shape before it enters
 * the animation loop. This intentionally does not invent missing samples.
 */
export function normalizeScalarField(field: ScalarFieldDefinition): ScalarFieldNormalizationResult {
  const id = typeof field?.id === "string" ? field.id.trim() : "";
  const prefix = id ? `Scalar field '${id}'` : "Scalar field";
  const errors: string[] = [];
  if (!id) errors.push(`${prefix} is missing id.`);
  if (!isValidVersion(field?.version)) errors.push(`${prefix} is missing a valid version.`);
  if (field?.type !== "scalarField") errors.push(`${prefix} must declare type 'scalarField'.`);
  if (field?.physicalDimension !== 1 && field?.physicalDimension !== 2) errors.push(`${prefix} requires physicalDimension=1 or 2.`);

  const axis = field?.axes?.[0];
  const axisKey = axis?.key ?? axis?.id ?? "";
  const x = axisCoordinates(field) ?? [];
  if (axisKey !== "x") errors.push(`${prefix} requires an x axis.`);
  if (!strictlyIncreasing(x)) errors.push(`${prefix} x coordinates must be finite and strictly increasing.`);
  if (!axis?.unit?.trim()) errors.push(`${prefix} x axis is missing a unit.`);
  const yAxis = field?.physicalDimension === 2 ? field?.axes?.[1] : undefined;
  const y = field?.physicalDimension === 2 ? (axisCoordinatesAt(field, 1) ?? []) : [];
  if (field?.physicalDimension === 2) {
    const yKey = yAxis?.key ?? yAxis?.id ?? "";
    if (yKey !== "y") errors.push(`${prefix} requires a y axis for physicalDimension=2.`);
    if (!strictlyIncreasing(y)) errors.push(`${prefix} y coordinates must be finite and strictly increasing.`);
    if (!yAxis?.unit?.trim()) errors.push(`${prefix} y axis is missing a unit.`);
  }

  const time = field?.time ?? [];
  if (!strictlyIncreasing(time)) errors.push(`${prefix} time values must be finite and strictly increasing.`);
  if (!field?.timeUnit?.trim()) errors.push(`${prefix} is missing timeUnit.`);
  const valueUnit = field?.valueUnit ?? field?.value?.unit;
  if (!valueUnit?.trim()) errors.push(`${prefix} is missing valueUnit.`);

  const shape = fieldShape(field);
  const timeLength = time.length;
  const spaceLength = x.length;
  const yLength = field?.physicalDimension === 2 ? y.length : 1;
  const expectedShape = field?.physicalDimension === 2
    ? [timeLength, spaceLength, yLength]
    : [timeLength, spaceLength];
  if (!shape || shape.length !== expectedShape.length || shape.some((value, index) => !Number.isInteger(value) || value !== expectedShape[index])) {
    errors.push(`${prefix} shape must equal ${JSON.stringify(expectedShape)}.`);
  }

  const rawValues = fieldValues(field);
  const flattened: number[] = [];
  if (!rawValues || rawValues.length === 0) {
    errors.push(`${prefix} is missing samples.`);
  } else if (Array.isArray(rawValues[0])) {
    const rows = rawValues as number[][];
    const expectedRowLength = spaceLength * yLength;
    if (rows.length !== timeLength || rows.some(row => !Array.isArray(row) || row.length !== expectedRowLength)) {
      errors.push(`${prefix} nested samples must have ${expectedRowLength} values per time row.`);
    } else {
      for (const row of rows) flattened.push(...row);
    }
  } else {
    const values = rawValues as number[];
    if (values.length !== timeLength * spaceLength * yLength) {
      errors.push(`${prefix} flattened samples do not match shape.`);
    } else {
      flattened.push(...values);
    }
  }
  if (flattened.some(value => !isFiniteNumber(value))) errors.push(`${prefix} samples must be finite.`);
  if (field?.interpolation && field.interpolation !== "linear") errors.push(`${prefix} has unsupported interpolation '${field.interpolation}'.`);

  if (errors.length || !id || !isValidVersion(field?.version) || !axis || !valueUnit
      || (field.physicalDimension === 2 && !yAxis)) {
    return { errors };
  }
  return {
    field: {
      id,
      version: field.version,
      physicalDimension: field.physicalDimension,
      axisKey,
      x: new Float64Array(x),
      y: field.physicalDimension === 2 ? new Float64Array(y) : undefined,
      time: new Float64Array(time),
      values: new Float32Array(flattened),
      timeLength,
      spaceLength,
      yLength,
      valueUnit,
      timeUnit: field.timeUnit,
      valueLabel: field.value?.label,
      valueSymbol: field.value?.symbol,
      interpolation: "linear",
      boundary: field.boundary,
    },
    errors: [],
  };
}

function recordScalarFields(simulation: Simulation) {
  const scalarFields = new Map<string, ScalarFieldData>();
  const scalarFieldErrors = new Map<string, string[]>();
  const source = simulation.scalarFields ?? [];
  const entries = Array.isArray(source)
    ? source.entries()
    : Object.entries(source).map(([key, field]) => [key, field] as const);
  for (const [indexOrKey, field] of entries) {
    const fallbackKey = typeof indexOrKey === "number" ? `#${indexOrKey + 1}` : indexOrKey;
    const key = typeof field?.id === "string" && field.id.trim() ? field.id.trim() : fallbackKey;
    const result = normalizeScalarField(field);
    if (result.errors.length) {
      scalarFieldErrors.set(key, result.errors);
      continue;
    }
    const normalized = result.field;
    if (!normalized) continue;
    if (scalarFields.has(normalized.id)) {
      scalarFields.delete(normalized.id);
      scalarFieldErrors.set(normalized.id, [`Scalar field '${normalized.id}' is declared more than once.`]);
      continue;
    }
    scalarFields.set(normalized.id, normalized);
  }
  return { scalarFields, scalarFieldErrors };
}

/** Convert server JSON arrays once, outside the animation hot path. */
export function prepareSimulationData(simulation: Simulation): RuntimeData {
  const cached = dataCache.get(simulation);
  if (cached) return cached;

  const series = new Map<string, NumericSeries>();
  const fieldData = recordScalarFields(simulation);
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
    ...fieldData,
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

export function scalarFieldFor(data: RuntimeData, id: string | undefined): ScalarFieldData | undefined {
  return id?.trim() ? data.scalarFields.get(id.trim()) : undefined;
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.max(minimum, Math.min(maximum, value));
}

function lowerIndex(values: Timeline, value: number): number {
  const last = values.length - 1;
  if (last <= 0 || value <= (values[0] ?? value)) return 0;
  if (value >= (values[last] ?? value)) return Math.max(0, last - 1);
  let low = 0;
  let high = last;
  while (low + 1 < high) {
    const middle = Math.floor((low + high) / 2);
    if ((values[middle] ?? value) <= value) low = middle;
    else high = middle;
  }
  return low;
}

function interpolatePair(values: Timeline, value: number): { index: number; ratio: number } {
  const index = lowerIndex(values, value);
  const next = Math.min(values.length - 1, index + 1);
  const start = values[index] ?? value;
  const end = values[next] ?? start;
  const ratio = end - start <= Number.EPSILON ? 0 : (value - start) / (end - start);
  return { index, ratio: clamp(ratio, 0, 1) };
}

function sampleAt(field: ScalarFieldData, timeIndex: number, xIndex: number, yIndex = 0): number {
  return field.values[timeIndex * field.spaceLength * field.yLength + xIndex * field.yLength + yIndex] ?? Number.NaN;
}

/** Trilinear interpolation over time and one or two spatial axes. */
export function sampleScalarField(field: ScalarFieldData, x: number, time: number, requestedY?: number): number | undefined {
  if (!Number.isFinite(x) || !Number.isFinite(time) || field.x.length === 0 || field.time.length === 0) return undefined;
  const clampedX = clamp(x, field.x[0] ?? x, field.x.at(-1) ?? x);
  const clampedTime = clamp(time, field.time[0] ?? time, field.time.at(-1) ?? time);
  const spatial = interpolatePair(field.x, clampedX);
  const temporal = interpolatePair(field.time, clampedTime);
  const yCoordinates = field.y ?? new Float64Array([0]);
  const clampedY = field.physicalDimension === 2
    ? clamp(requestedY ?? ((yCoordinates[0] ?? 0) + (yCoordinates.at(-1) ?? 0)) / 2, yCoordinates[0] ?? 0, yCoordinates.at(-1) ?? 0)
    : 0;
  const transverse = field.physicalDimension === 2 ? interpolatePair(yCoordinates, clampedY) : { index: 0, ratio: 0 };
  const xNext = Math.min(field.spaceLength - 1, spatial.index + 1);
  const yNext = Math.min(field.yLength - 1, transverse.index + 1);
  const timeNext = Math.min(field.timeLength - 1, temporal.index + 1);
  const lowerLeft = sampleAt(field, temporal.index, spatial.index, transverse.index);
  const lowerRight = sampleAt(field, temporal.index, xNext, transverse.index);
  const lowerLeftY = sampleAt(field, temporal.index, spatial.index, yNext);
  const lowerRightY = sampleAt(field, temporal.index, xNext, yNext);
  const lowerX = lowerLeft + (lowerRight - lowerLeft) * spatial.ratio;
  const lowerY = lowerLeftY + (lowerRightY - lowerLeftY) * spatial.ratio;
  const lower = lowerX + (lowerY - lowerX) * transverse.ratio;
  const upperLeft = sampleAt(field, timeNext, spatial.index, transverse.index);
  const upperRight = sampleAt(field, timeNext, xNext, transverse.index);
  const upperLeftY = sampleAt(field, timeNext, spatial.index, yNext);
  const upperRightY = sampleAt(field, timeNext, xNext, yNext);
  const upperX = upperLeft + (upperRight - upperLeft) * spatial.ratio;
  const upperY = upperLeftY + (upperRightY - upperLeftY) * spatial.ratio;
  const upper = upperX + (upperY - upperX) * transverse.ratio;
  const value = lower + (upper - lower) * temporal.ratio;
  return Number.isFinite(value) ? value : undefined;
}

/** Resolve a probe in the field's physical domain, reporting any clamping. */
export function probeScalarField(field: ScalarFieldData, x: number, time: number, requestedY?: number): ScalarFieldProbe | undefined {
  if (!Number.isFinite(x) || !Number.isFinite(time) || field.x.length === 0 || field.time.length === 0) return undefined;
  const resolvedX = clamp(x, field.x[0] ?? x, field.x.at(-1) ?? x);
  const resolvedTime = clamp(time, field.time[0] ?? time, field.time.at(-1) ?? time);
  const yCoordinates = field.y ?? new Float64Array([0]);
  const requestedResolvedY = field.physicalDimension === 2
    ? (requestedY ?? ((yCoordinates[0] ?? 0) + (yCoordinates.at(-1) ?? 0)) / 2)
    : undefined;
  const resolvedY = field.physicalDimension === 2
    ? clamp(requestedResolvedY ?? 0, yCoordinates[0] ?? 0, yCoordinates.at(-1) ?? 0)
    : undefined;
  const value = sampleScalarField(field, resolvedX, resolvedTime, resolvedY);
  if (value === undefined) return undefined;
  const probe: ScalarFieldProbe = {
    fieldId: field.id,
    requestedX: x,
    requestedTime: time,
    x: resolvedX,
    time: resolvedTime,
    value,
    clamped: resolvedX !== x || resolvedTime !== time
      || (resolvedY !== undefined && requestedY !== undefined && resolvedY !== requestedY),
  };
  if (field.physicalDimension === 2) {
    probe.requestedY = requestedResolvedY;
    probe.y = resolvedY;
  }
  return probe;
}
