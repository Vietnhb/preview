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
export type FieldMeta = QuantityInfo & { key: string; participantId: string; quantity: string; min: number; max: number };
/** Optional per-field metadata from the backend (units/labels taken from the approved schema). */
export type BackendFieldMeta = Record<string, { unit?: string; label?: string; quantity?: string }>;
const prettyUnit = (unit: string) => unit.replace(/\^2/g, "²").replace(/\^3/g, "³").replace(/\*/g, "·");
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
      label: info.kind === "scalar" && supplied?.label ? supplied.label.charAt(0).toUpperCase() + supplied.label.slice(1) : info.label };
  }
  const participants: SceneParticipant[] = [];
  for (const id of order) {
    const own = Object.values(fields).filter(field => field.participantId === id);
    if (!own.length) continue;
    const model = models.find(item => item.id === id);
    const find = (...names: string[]) => names.map(name => own.find(field => field.quantity === name)?.key).find(Boolean);
    const f: SceneParticipant["fields"] = {
      x: find("x"), y: find("y"), position: find("position", "displacement", "height", "elongation"),
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
    const vertical = dims === 1 && (positionMeta?.axis === "y" || inputs.some(name => /gravit/i.test(name)));
    let link: SceneParticipant["link"] = null;
    if (dims === 2 && f.angle) {
      const radii = series[f.x!].map((x, i) => Math.hypot(x, series[f.y!][i]));
      const mean = radii.reduce((sum, value) => sum + value, 0) / radii.length;
      const spread = Math.max(...radii) - Math.min(...radii);
      if (mean > 0 && spread <= 1e-3 * mean) link = { radius: mean };
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
    participants.push({ id, label: model?.label?.trim() || id, colorIndex: participants.length, dims, vertical, fields: f, link, spring });
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

export function formatNumber(value: number, significant = 4) {
  if (!Number.isFinite(value)) return "—";
  if (value === 0 || Math.abs(value) < 1e-12) return "0";
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
