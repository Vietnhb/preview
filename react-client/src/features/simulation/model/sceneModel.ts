import type { SolverTimeline } from "./svgScene";

/**
 * Semantic description of backend solver fields.
 *
 * Nothing here dispatches on schema, capability or lesson identity. The scene is
 * inferred from the physical *quantity* of each solver output (position, velocity,
 * angle, force …) and from the solver data itself (a constant distance to the
 * origin means a fixed-length link; force proportional to −displacement means a
 * restoring spring). Any new capability that emits canonical quantity keys is
 * rendered without code changes; unknown quantities still appear in the charts.
 */
export type QuantityKind =
  | "position" | "velocity" | "acceleration" | "angle" | "angular_velocity"
  | "force" | "energy" | "scalar";
export type QuantityAxis = "x" | "y" | "along" | "none";
export type QuantityInfo = { kind: QuantityKind; axis: QuantityAxis; label: string; symbol: string; unit: string };
export type FieldMeta = QuantityInfo & { key: string; participantId: string; quantity: string; min: number; max: number;
  /** Renderer role declared by the approved capability (state_value, position …), when the backend supplied it. */
  role?: string };
/** Optional per-field metadata from the backend (units/labels taken from the approved schema). */
export type BackendFieldMeta = Record<string, { unit?: string; label?: string; quantity?: string; rendererRole?: string }>;
/** Input names that mean "uniform gravitational field" (not the gravitational constant). */
const GRAVITY_INPUT = /gravitational_acceleration|(^|_)gravity(_|$)/i;
/** Display form of SI unit strings used in schemas (m/s^2 → m/s², degC → °C, ohm → Ω, m3 → m³). */
export const prettyUnit = (unit: string) => unit
  .replace(/\bdegC\b/g, "°C").replace(/\bdegF\b/g, "°F").replace(/\bohm\b/g, "Ω")
  .replace(/\^2|(?<=[a-zA-Z])2(?![0-9])/g, "²").replace(/\^3|(?<=[a-zA-Z])3(?![0-9])/g, "³")
  .replace(/\*/g, "·");
export type SimulationModelRef = { id: string; label?: string; capabilityId?: string; inputs?: Record<string, string | number> };

export type SceneParticipant = {
  id: string;
  label: string;
  colorIndex: number;
  /** 2 = planar (x, y); 1 = along one axis; 0 = no spatial quantity (charts only). */
  dims: 0 | 1 | 2;
  /** 1-D motion measured along the vertical (height) axis. */
  vertical: boolean;
  fields: Partial<Record<
    "x" | "y" | "position" | "velocity" | "vx" | "vy" | "acceleration" | "ax" | "ay"
    | "angle" | "angularVelocity" | "force", string>>;
  /** Inferred from data: body stays at a fixed distance from the frame origin. */
  link: { radius: number } | null;
  /** Inferred from data: force ≈ −k · displacement over the whole run. */
  spring: { stiffness: number } | null;
  /** Has a place in space that never changes during the run (a probe, a fixed source, an obstacle). */
  stationary: boolean;
};
export type SceneDescriptor = {
  participants: SceneParticipant[];
  fields: Record<string, FieldMeta>;
  durationSeconds: number;
};

const Q = (kind: QuantityKind, axis: QuantityAxis, label: string, symbol: string, unit: string): QuantityInfo =>
  ({ kind, axis, label, symbol, unit });

/** Canonical physical quantity vocabulary (shared by every topic schema). */
const QUANTITIES: Record<string, QuantityInfo> = {
  position: Q("position", "along", "Tọa độ", "x", "m"),
  displacement: Q("position", "along", "Li độ", "x", "m"),
  elongation: Q("position", "along", "Độ biến dạng", "Δl", "m"),
  height: Q("position", "y", "Độ cao", "h", "m"),
  x: Q("position", "x", "Tọa độ x", "x", "m"),
  y: Q("position", "y", "Tọa độ y", "y", "m"),
  distance: Q("scalar", "none", "Quãng đường", "s", "m"),
  velocity: Q("velocity", "along", "Vận tốc", "v", "m/s"),
  vx: Q("velocity", "x", "Vận tốc vₓ", "vₓ", "m/s"),
  vy: Q("velocity", "y", "Vận tốc v_y", "v_y", "m/s"),
  speed: Q("scalar", "none", "Tốc độ", "|v|", "m/s"),
  acceleration: Q("acceleration", "along", "Gia tốc", "a", "m/s²"),
  ax: Q("acceleration", "x", "Gia tốc aₓ", "aₓ", "m/s²"),
  ay: Q("acceleration", "y", "Gia tốc a_y", "a_y", "m/s²"),
  angle: Q("angle", "none", "Li độ góc", "θ", "rad"),
  angular_velocity: Q("angular_velocity", "none", "Tốc độ góc", "ω", "rad/s"),
  angular_acceleration: Q("scalar", "none", "Gia tốc góc", "γ", "rad/s²"),
  force: Q("force", "along", "Lực", "F", "N"),
  fx: Q("force", "x", "Lực Fₓ", "Fₓ", "N"),
  fy: Q("force", "y", "Lực F_y", "F_y", "N"),
  momentum: Q("scalar", "none", "Động lượng", "p", "kg·m/s"),
  kinetic_energy: Q("energy", "none", "Động năng", "Wđ", "J"),
  potential_energy: Q("energy", "none", "Thế năng", "Wt", "J"),
  mechanical_energy: Q("energy", "none", "Cơ năng", "W", "J"),
  energy: Q("energy", "none", "Năng lượng", "W", "J"),
  current: Q("scalar", "none", "Cường độ dòng điện", "I", "A"),
  voltage: Q("scalar", "none", "Hiệu điện thế", "U", "V"),
  charge: Q("scalar", "none", "Điện tích", "q", "C"),
  temperature: Q("scalar", "none", "Nhiệt độ", "T", "K"),
  pressure: Q("scalar", "none", "Áp suất", "p", "Pa"),
  volume: Q("scalar", "none", "Thể tích", "V", "m³"),
  power: Q("scalar", "none", "Công suất", "P", "W"),
  work: Q("energy", "none", "Công", "A", "J"),
};

export function quantityInfo(quantity: string): QuantityInfo {
  const known = QUANTITIES[quantity];
  if (known) return known;
  const readable = quantity.replace(/[_-]+/g, " ").trim();
  return Q("scalar", "none", readable.charAt(0).toUpperCase() + readable.slice(1), readable, "");
}

function splitKey(key: string): [string, string] | null {
  const index = key.indexOf(".");
  return index > 0 ? [key.slice(0, index), key.slice(index + 1)] : null;
}

function pearson(a: number[], b: number[]) {
  const n = a.length;
  if (n < 3) return 0;
  let ma = 0, mb = 0;
  for (let i = 0; i < n; i++) { ma += a[i]; mb += b[i]; }
  ma /= n; mb /= n;
  let sab = 0, saa = 0, sbb = 0;
  for (let i = 0; i < n; i++) { const da = a[i] - ma, db = b[i] - mb; sab += da * db; saa += da * da; sbb += db * db; }
  return saa > 0 && sbb > 0 ? sab / Math.sqrt(saa * sbb) : 0;
}

/** Build a render-ready description of the solver output. Pure and serialisable. */
export function describeScene(timeline: SolverTimeline, models: readonly SimulationModelRef[] = [],
  backendMeta: BackendFieldMeta = {}): SceneDescriptor {
  const fields: Record<string, FieldMeta> = {};
  const series: Record<string, number[]> = {};
  for (const frame of timeline.frames) for (const [key, value] of Object.entries(frame.values)) {
    if (key === "t" || !Number.isFinite(value)) continue;
    (series[key] ??= []).push(value);
  }
  // A time-valued solver field that merely repeats the clock (an elapsed-time state) adds nothing.
  for (const key of Object.keys(series)) {
    const values = series[key];
    const timeValued = backendMeta[key]?.unit === "s" || /(^|[._])(elapsed_)?time$/i.test(key);
    if (timeValued && values.length === timeline.frames.length && values.length > 2
      && timeline.frames.every((frame, i) => Math.abs(values[i] - frame.t) <= 1e-9 * Math.max(1, Math.abs(frame.t)))) delete series[key];
  }
  const order: string[] = models.map(model => model.id);
  for (const key of Object.keys(series)) {
    const parts = splitKey(key);
    const participantId = parts ? parts[0] : "_";
    const quantity = parts ? parts[1] : key;
    if (!order.includes(participantId)) order.push(participantId);
    let min = Infinity, max = -Infinity;
    for (const value of series[key]) { if (value < min) min = value; if (value > max) max = value; }
    const info = quantityInfo(quantity), supplied = backendMeta[key];
    fields[key] = { ...info, key, participantId, quantity, min, max,
      unit: supplied?.unit ? prettyUnit(supplied.unit) : info.unit,
      label: info.kind === "scalar" && supplied?.label ? supplied.label.charAt(0).toUpperCase() + supplied.label.slice(1) : info.label,
      ...(supplied?.rendererRole ? { role: supplied.rendererRole } : {}) };
  }
  const participants: SceneParticipant[] = [];
  for (const id of order) {
    const own = Object.values(fields).filter(field => field.participantId === id);
    if (!own.length) continue;
    const model = models.find(item => item.id === id);
    /* The approved capability's renderer roles are the authority on what is a place in space: a field it
       declares as something else (e.g. an elongation shown as a state value) is never drawn as motion, and a
       field it declares as a position is used even if its name is not in the shared vocabulary. */
    const roleOf = (key: string) => backendMeta[key]?.rendererRole;
    const SPATIAL = /position|trajectory|displacement/;
    /* when every role the capability declares for this participant is a plain state value, nothing of it is drawn as motion */
    const declared = own.map(field => roleOf(field.key)).filter((role): role is string => !!role);
    const noPlace = declared.length > 0 && declared.every(role => role === "state_value");
    const placeable = noPlace ? [] : own.filter(field => { const role = roleOf(field.key); return !role || SPATIAL.test(role); });
    const findIn = (pool: FieldMeta[], ...names: string[]) => names.map(name => pool.find(field => field.quantity === name)?.key).find(Boolean);
    const find = (...names: string[]) => findIn(own, ...names);
    const findPlace = (...names: string[]) => findIn(placeable, ...names);
    const byRole = (pattern: RegExp) => placeable.find(field => pattern.test(roleOf(field.key) ?? ""))?.key;
    const f: SceneParticipant["fields"] = {
      x: findPlace("x") ?? byRole(/horizontal_position|trajectory_x/), y: findPlace("y") ?? byRole(/vertical_position|trajectory_y/),
      position: findPlace("position", "displacement", "height", "elongation") ?? byRole(/^(position|displacement|trajectory)$/),
      velocity: find("velocity"), vx: find("vx"), vy: find("vy"),
      acceleration: find("acceleration"), ax: find("ax"), ay: find("ay"),
      angle: find("angle"), angularVelocity: find("angular_velocity"), force: find("force"),
    };
    for (const name of Object.keys(f) as Array<keyof typeof f>) if (!f[name]) delete f[name];
    // A lone x or y is still one-dimensional motion.
    if (!f.position && (f.x ? !f.y : !!f.y)) {
      f.position = f.x ?? f.y;
      if (!f.velocity) f.velocity = f.x ? f.vx : f.vy;
      if (!f.acceleration) f.acceleration = f.x ? f.ax : f.ay;
    }
    const dims: 0 | 1 | 2 = f.x && f.y ? 2 : f.position ? 1 : 0;
    const inputs = Object.keys(model?.inputs ?? {});
    const positionMeta = f.position ? fields[f.position] : undefined;
    /* the approved capability declares the axis (renderer role); the input-name pattern is only a fallback without backend metadata */
    const role = f.position ? backendMeta[f.position]?.rendererRole : undefined;
    const vertical = dims === 1 && (positionMeta?.axis === "y" || /vertical/.test(role ?? "")
      || (role === undefined && inputs.some(name => GRAVITY_INPUT.test(name))));
    let link: SceneParticipant["link"] = null;
    if (dims === 2 && f.angle) {
      const radii = series[f.x!].map((x, i) => Math.hypot(x, series[f.y!][i]));
      const mean = radii.reduce((sum, value) => sum + value, 0) / radii.length;
      const spread = Math.max(...radii) - Math.min(...radii);
      /* a connector is drawn only when the approved capability declares one (role
         connector_angle); without backend metadata fall back to the data pattern */
      const declared = Object.keys(backendMeta).length === 0 || backendMeta[f.angle]?.rendererRole === "connector_angle";
      if (mean > 0 && spread <= 1e-3 * mean && declared) link = { radius: mean };
    }
    let spring: SceneParticipant["spring"] = null;
    if (dims === 1 && f.force && f.position && series[f.force].length === series[f.position].length) {
      const x = series[f.position], force = series[f.force];
      if (pearson(x, force) < -0.999) {
        let sxx = 0, sxf = 0;
        for (let i = 0; i < x.length; i++) { sxx += x[i] * x[i]; sxf += x[i] * force[i]; }
        if (sxx > 0) spring = { stiffness: -sxf / sxx };
      }
    }
    const still = (key?: string) => !key || (fields[key] && fields[key].max - fields[key].min <= 1e-12 * Math.max(1, Math.abs(fields[key].max), Math.abs(fields[key].min)));
    const stationary = dims > 0 && still(f.x) && still(f.y) && still(f.position);
    participants.push({ id, label: model?.label?.trim() || id, colorIndex: participants.length, dims, vertical, fields: f, link, spring, stationary });
  }
  return { participants, fields, durationSeconds: timeline.durationSeconds };
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

/** Values shown to learners: angles in degrees, everything else in its SI unit. */
export function displayValue(meta: Pick<FieldMeta, "kind" | "unit">, value: number) {
  if (meta.kind === "angle" && meta.unit === "rad") return { value: value * 180 / Math.PI, unit: "°" };
  if (meta.kind === "angular_velocity" && meta.unit === "rad/s") return { value, unit: "rad/s" };
  return { value, unit: meta.unit };
}

/**
 * Physical runs last from nanoseconds to years; playback maps the whole run onto a
 * watchable 4–30 s. Returns simulated seconds per real second at 1× speed.
 */
export function presentationRate(durationSeconds: number, timeline?: SolverTimeline) {
  if (!(durationSeconds > 0)) return 1;
  const rate = durationSeconds / Math.min(30, Math.max(4, durationSeconds));
  /* never replay an oscillation faster than the eye (and a 60 fps screen) can follow: at most
     MAX_SCREEN_HZ cycles per real second, unless that would make the replay longer than MAX_REPLAY_SECONDS */
  const frequency = timeline ? fastestOscillation(timeline) : 0;
  if (!(frequency * rate > MAX_SCREEN_HZ)) return rate;
  return Math.max(MAX_SCREEN_HZ / frequency, durationSeconds / MAX_REPLAY_SECONDS);
}
const MAX_SCREEN_HZ = 2, MAX_REPLAY_SECONDS = 120;
/** Highest oscillation frequency (Hz, simulated time) among the solver fields, from sign changes about each field's mean. */
export function fastestOscillation(timeline: SolverTimeline) {
  const frames = timeline.frames, duration = timeline.durationSeconds;
  if (frames.length < 3 || !(duration > 0)) return 0;
  let fastest = 0;
  for (const key of Object.keys(frames[0].values)) {
    if (key === "t") continue;
    let min = Infinity, max = -Infinity, sum = 0;
    for (const frame of frames) { const v = frame.values[key]; min = Math.min(min, v); max = Math.max(max, v); sum += v; }
    if (!(max - min > 1e-9 * Math.max(Math.abs(max), Math.abs(min)))) continue;
    const mean = sum / frames.length, band = (max - min) * 0.05;
    let side = 0, changes = 0;
    for (const frame of frames) {
      const d = frame.values[key] - mean;
      const s = d > band ? 1 : d < -band ? -1 : 0;
      if (s && side && s !== side) changes++;
      if (s) side = s;
    }
    fastest = Math.max(fastest, changes / 2 / duration);
  }
  return fastest;
}
/** True when even the slowed replay shows an oscillation too fast to follow (the charts then carry the detail). */
export function replayTooFast(timeline: SolverTimeline) {
  return fastestOscillation(timeline) * presentationRate(timeline.durationSeconds, timeline) > 3 * MAX_SCREEN_HZ;
}

const TIME_UNITS: Array<[number, string]> = [[31557600, "năm"], [86400, "ngày"], [3600, "h"], [60, "min"], [1, "s"],
  [1e-3, "ms"], [1e-6, "µs"], [1e-9, "ns"]];
/** Unit that suits a whole run (e.g. 6000 s → h, 1.3e-7 s → ns). */
export function timeUnitFor(durationSeconds: number): [number, string] {
  return TIME_UNITS.find(([size]) => durationSeconds / size >= 1.5) ?? TIME_UNITS[TIME_UNITS.length - 1];
}
export function formatTime(seconds: number, durationSeconds: number, digits = 2) {
  const [size, unit] = timeUnitFor(durationSeconds);
  return (seconds / size).toFixed(digits) + " " + unit;
}