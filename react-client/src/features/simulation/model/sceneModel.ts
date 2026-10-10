import type { SolverTimeline } from "./svgScene";

/** Metadata for the numeric readouts and charts outside the authored stage. No apparatus, layout or quantity vocabulary of its own. */
export type FieldMeta = { key: string; participantId: string; quantity: string; label: string; symbol: string; unit: string; min: number; max: number;
  /** Renderer role declared by the approved capability (state_value, position …), when the backend supplied it. */
  role?: string;
  /** A value the learner watches: its name in the user's words and the described object it belongs to. */
  caption?: string; object?: string };
/** A value the plan asks the learner to watch: a solver field, the object it belongs to and its name in the user's words. */
export type SceneObservable = { field: string; object?: string; label?: string };
/** Optional per-field metadata from the backend (units/labels taken from the approved schema). */
export type BackendFieldMeta = Record<string, { unit?: string; label?: string; symbol?: string; quantity?: string; rendererRole?: string;
  /** a fixed value of the plan (an input bound to a number), which the stage can show beside its object */
  constant?: number }>;
/** Display form of SI unit strings used in schemas (m/s^2 → m/s², degC → °C, ohm → Ω, m3 → m³; a pure number "1" has none). */
export const prettyUnit = (unit: string) => unit.trim() === "1" ? "" : unit
  .replace(/\bdegC\b/g, "°C").replace(/\bdegF\b/g, "°F").replace(/\bohm\b/g, "Ω")
  .replace(/\^2|(?<=[a-zA-Z])2(?![0-9])/g, "²").replace(/\^3|(?<=[a-zA-Z])3(?![0-9])/g, "³")
  .replace(/\*/g, "·");
export type SimulationModelRef = { id: string; label?: string; capabilityId?: string; inputs?: Record<string, string | number> };

export type SceneParticipant = {
  id: string;
  label: string;
  colorIndex: number;

};
export type SceneDescriptor = {
  participants: SceneParticipant[];
  fields: Record<string, FieldMeta>;
  durationSeconds: number;
  /** Keys of the values the learner watches, most important first (empty when the plan names none). */
  observables: string[];
  /** The plan's parameters (slider name → label and display unit), so a drawing can show their current value. */
  parameters?: Record<string, { label: string; unit: string }>;
  /** Fixed values of the plan ("<participantId>.<input>" → label, display unit, value), shown the same way. */
  constants?: Record<string, { label: string; unit: string; value: number }>;
};

/** A readable name for a result the backend sent without one (older saved runs). */
const readable = (quantity: string) => {
  const words = quantity.replace(/[_-]+/g, " ").trim();
  return words.charAt(0).toUpperCase() + words.slice(1);
};

function splitKey(key: string): [string, string] | null {
  const index = key.indexOf(".");
  return index > 0 ? [key.slice(0, index), key.slice(index + 1)] : null;
}

/** Group backend fields for the numeric data panel. Pure and serialisable. */
export function describeScene(timeline: SolverTimeline, models: readonly SimulationModelRef[] = [],
  backendMeta: BackendFieldMeta = {}, observables: readonly SceneObservable[] = [],
  parameters: readonly { name: string; label?: string; unit?: string }[] = []): SceneDescriptor {
  const fields: Record<string, FieldMeta> = {};
  const series: Record<string, number[]> = {};
  for (const frame of timeline.frames) for (const [key, value] of Object.entries(frame.values)) {
    if (key === "t" || !Number.isFinite(value)) continue;
    (series[key] ??= []).push(value);
  }
  const order: string[] = models.map(model => model.id);
  for (const key of Object.keys(series)) {
    const parts = splitKey(key);
    const participantId = parts ? parts[0] : "_";
    const quantity = parts ? parts[1] : key;
    if (!order.includes(participantId)) order.push(participantId);
    let min = Infinity, max = -Infinity;
    for (const value of series[key]) { if (value < min) min = value; if (value > max) max = value; }
    /* names, symbols and units are the approved catalog's, sent by the backend with the run */
    const supplied = backendMeta[key], label = supplied?.label?.trim() || readable(quantity);
    fields[key] = { key, participantId, quantity, min, max, label, symbol: supplied?.symbol?.trim() || label,
      unit: typeof supplied?.unit === "string" ? prettyUnit(supplied.unit) : "",
      ...(supplied?.rendererRole ? { role: supplied.rendererRole } : {}) };
  }
  const participants: SceneParticipant[] = [];
  for (const id of order) {
    const own = Object.values(fields).filter(field => field.participantId === id);
    if (!own.length) continue;
    const model = models.find(item => item.id === id);
    participants.push({ id, label: model?.label?.trim() || id, colorIndex: participants.length });
  }
  /* the plan names what the learner watches: such a value carries its name in the user's words and its object */
  const watched: string[] = [];
  for (const item of observables) {
    const meta = item && typeof item.field === "string" ? fields[item.field] : undefined;
    if (!meta || watched.includes(meta.key)) continue;
    watched.push(meta.key);
    if (typeof item.label === "string" && item.label.trim()) meta.caption = item.label.trim();
    if (typeof item.object === "string" && item.object.trim()) meta.object = item.object.trim();
  }
  return { participants, fields, durationSeconds: timeline.durationSeconds, observables: watched,
    parameters: Object.fromEntries(parameters.filter(item => item && typeof item.name === "string")
      .map(item => [item.name, { label: item.label?.trim() || item.name, unit: item.unit ? prettyUnit(item.unit) : "" }])),
    constants: Object.fromEntries(Object.entries(backendMeta).filter(([, meta]) => typeof meta.constant === "number")
      .map(([key, meta]) => [key, { label: meta.label?.trim() || key, unit: meta.unit ? prettyUnit(meta.unit) : "", value: meta.constant! }])) };
}

/** Participant colours — validated for contrast on both workspace themes. */
export const SERIES_COLORS = {
  LIGHT: ["#2563eb", "#dc2626", "#059669", "#d97706", "#7c3aed", "#db2777", "#0891b2", "#65a30d"],
  DARK: ["#60a5fa", "#f87171", "#34d399", "#fbbf24", "#a78bfa", "#f472b6", "#22d3ee", "#a3e635"],
} as const;
export type ThemeName = keyof typeof SERIES_COLORS;
export const seriesColor = (theme: ThemeName, index: number) =>
  SERIES_COLORS[theme][((index % 8) + 8) % 8];

/** "Nice" tick spacing (1, 2, 2.5, 5 × 10ⁿ) for axes, rulers and strobe intervals. */
export function niceStep(span: number, targetTicks = 6) {
  if (!(span > 0) || !Number.isFinite(span)) return 1;
  const raw = span / Math.max(1, targetTicks);
  const power = 10 ** Math.floor(Math.log10(raw));
  const fraction = raw / power;
  const nice = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 2.5 ? 2.5 : fraction <= 5 ? 5 : 10;
  return nice * power;
}

/**
 * scale: typical magnitude of the quantity (range or tick step). Only values negligible against it
 * print as 0, so 5×10⁻²⁹ kg (a mass defect) or 1.6×10⁻¹⁹ C are shown, while rounding noise at a zero tick is not.
 */
export function formatNumber(value: number, significant = 4, scale = 0) {
  if (!Number.isFinite(value)) return "—";
  if (value === 0 || Math.abs(value) < (scale > 0 ? scale * 1e-9 : 1e-300)) return "0";
  const magnitude = Math.abs(value);
  if (magnitude >= 1e5 || magnitude < 1e-3) {
    const [mantissa, exponent] = value.toExponential(2).split("e");
    const sup: Record<string, string> = { "-": "⁻", "0": "⁰", "1": "¹", "2": "²", "3": "³", "4": "⁴", "5": "⁵", "6": "⁶", "7": "⁷", "8": "⁸", "9": "⁹" };
    return mantissa.replace("-", "−") + "×10" + exponent.replace("+", "").split("").map(ch => sup[ch] ?? ch).join("");
  }
  const rounded = Number(value.toPrecision(significant));
  return String(rounded).replace("-", "−");
}

const TIME_UNITS: Array<[number, string]> = [[31557600, "năm"], [86400, "ngày"], [3600, "h"], [60, "min"], [1, "s"],
  [1e-3, "ms"], [1e-6, "µs"], [1e-9, "ns"]];
/** Unit that suits a whole run (e.g. 6000 s → h, 1.3e-7 s → ns). */
export function timeUnitFor(durationSeconds: number): [number, string] {
  return TIME_UNITS.find(([size]) => durationSeconds / size >= 1) ?? TIME_UNITS[TIME_UNITS.length - 1];
}
export function formatTime(seconds: number, durationSeconds: number, digits = 2) {
  const [size, unit] = timeUnitFor(durationSeconds);
  return Number((seconds / size).toFixed(digits)) + " " + unit;
}
