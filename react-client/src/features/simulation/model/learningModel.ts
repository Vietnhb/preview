import type { Simulation } from "../../../shared/types/physlive";

export type LearningSeries = { key: string; label: string; symbol: string; unit: string; color: string; data: number[] };
export type LearningControl = { key: string; label: string; symbol: string; unit: string; min: number; max: number; step: number };

export function hasValidControlBounds(control: LearningControl): boolean {
  return Number.isFinite(control.min) && Number.isFinite(control.max) && control.min <= control.max;
}

export function isWithinControlBounds(control: LearningControl, value: number): boolean {
  return hasValidControlBounds(control) && Number.isFinite(value) && value >= control.min && value <= control.max;
}

export function clampControlValue(control: LearningControl, value: number): number {
  if (!hasValidControlBounds(control)) return 0;
  return Math.max(control.min, Math.min(control.max, Number.isFinite(value) ? value : control.min));
}

/** The value a control starts from: the parameter the stored run was computed with. */
export function controlValue(control: LearningControl, simulation: Simulation): number {
  const value = simulation.parameters?.[control.key];
  return Number.isFinite(value) ? value : Number.NaN;
}

export function learningSeries(simulation: Simulation): LearningSeries[] {
  const read = (path: string): number[] => {
    const [group, key] = path.split(".");
    const source = (simulation as unknown as Record<string, Record<string, number[]>>)[group];
    return source?.[key] ?? [];
  };
  const candidates: LearningSeries[] = (simulation.visualization?.series ?? []).map(item => ({ ...item, data: read(item.source) }));
  return candidates.filter(s => s.data.length === simulation.time.length && s.data.length > 0 && s.data.every(Number.isFinite));
}

export function indexAtTime(times: number[], time: number): number {
  let low = 0, high = times.length - 1;
  while (low < high) {
    const middle = Math.ceil((low + high) / 2);
    if (times[middle] <= time) low = middle; else high = middle - 1;
  }
  return low;
}

export function interpolateAtTime(times: number[], values: number[], time: number): number {
  if (values.length === 0) return 0;
  if (times.length < 2 || time <= times[0]) return values[0] ?? 0;
  const lastIndex = times.length - 1;
  if (time >= times[lastIndex]) return values[lastIndex] ?? 0;

  let low = 0, high = lastIndex;
  while (low <= high) {
    const mid = Math.floor((low + high) / 2);
    if (times[mid] <= time) {
      if (mid === lastIndex || times[mid + 1] > time) {
        low = mid;
        break;
      }
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }

  const i = Math.min(Math.max(0, low), lastIndex - 1);
  const t0 = times[i], t1 = times[i + 1];
  const dt = t1 - t0;
  if (dt <= 1e-9) return values[i] ?? 0;
  const ratio = (time - t0) / dt;
  const v0 = values[i] ?? 0, v1 = values[i + 1] ?? v0;
  return v0 + ratio * (v1 - v0);
}

export const numberLabel = (value: number | undefined, digits = 2) => typeof value === "number" && Number.isFinite(value)
  ? new Intl.NumberFormat("vi-VN", { maximumFractionDigits: digits }).format(value) : "—";