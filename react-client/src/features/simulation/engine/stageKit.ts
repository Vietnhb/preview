import type * as PixiNS from "pixi.js";
import type { FieldMeta, SceneDescriptor, SceneParticipant } from "../model/sceneModel";

/**
 * Rendering toolkit injected into the sandboxed PixiJS worker as `api.kit`.
 *
 * IMPORTANT: `createStageKit` is serialised with Function#toString and evaluated
 * inside the worker, so it must stay self-contained (no references to imports or
 * module-level values; type-only imports are erased).
 *
 * It provides the academically standard layer every physics visual needs —
 * a measured coordinate frame fitted to the backend solver ranges, rulers/axes
 * with SI units, trajectories, strobe (multiple-exposure) marks, vectors, labels
 * and a time/readout HUD — and `standardScene()`, a complete visual assembled
 * only from those primitives and the semantic scene descriptor. Dispatch is by
 * quantity semantics and solver data, never by schema, lesson or object identity.
 */
export type StageHost = {
  data(): { timeline: { durationSeconds: number; frames: Array<{ t: number; values: Record<string, number> }> };
    scene: SceneDescriptor; theme: "LIGHT" | "DARK"; verificationStatus: string; parameters?: Record<string, number> };
  sample(t: number): Record<string, number>;
  /** Rasterise SVG markup into a texture (bridge-side sanitising); `screen` = viewport-sized art. */
  svg?(markup: string, options?: { screen?: boolean; width?: number; height?: number }): Promise<PixiNS.Texture>;
  /** Free a texture created by `svg` (returns its pixels to the memory budget). */
  release?(texture: PixiNS.Texture): void;
  /** Report an asynchronous rendering failure to the host. */
  fail?(message: string): void;
  /** Average luminance (0–1) of a viewport texture, when the bridge measured it. */
  luma?(texture: PixiNS.Texture): number | undefined;
  /**
   * Private channel to the trusted runtime (never exposed to generated code): every kit
   * scene registers its internals so the runtime can keep solver-bound visuals in place
   * each frame and cross-check the drawn stage against the verified solver timeline.
   */
  register?(scene: KitSceneInternals): void;
};
/** Trusted-runtime view of a kit scene (see StageHost.register). */
export type KitSceneInternals = {
  root: PixiNS.Container;
  /** Call before the generated update(frame). */
  begin(): void;
  /** Call after the generated update(frame): runs the kit update if the program skipped it, then re-applies every solver-bound placement. */
  finish(frame: { t: number; fields: Record<string, number> }): void;
  /** Visual cross-check: render at each time via run(t) and compare the stage with the solver timeline. Returns problems (empty = pass). */
  verify(run: (t: number) => void, times: number[]): string[];
  /** Rebuild from the current data/theme (the runtime calls it even when generated setData() does not). */
  refresh(): void;
};

export function createStageKit(PIXI: typeof PixiNS, app: PixiNS.Application, host: StageHost) {
  type Pt = { x: number; y: number };
  type Bounds = { minX: number; maxX: number; minY: number; maxY: number };
  type Rect = { x: number; y: number; w: number; h: number };
  type Kind = "velocity" | "acceleration" | "force";
  type SceneLayout = {
    width: number; height: number; theme: string; palette: Record<string, unknown>; mode: string;
    view: Rect; ground: number | null;
    lanes: Array<{ id: string; label: string; y: number; top: number; bottom: number }>;
    columns: Array<{ id: string; label: string; x: number }>;
    pivots: Array<{ id: string; x: number; y: number; length: number }>;
    walls: Array<{ id: string; x: number; y: number }>;
    participants: Array<{ id: string; label: string; dims: number; start: Pt | null; end: Pt | null; path: Pt[] }>;
    scale: Pt; toScreen: (x: number, y: number) => Pt;
  };
  const FONT = 'Inter, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';

  const palettes = {
    LIGHT: { ink: "#0f172a", muted: "#64748b", faint: "#94a3b8", grid: "#e2e8f0", gridMajor: "#cbd5e1", axis: "#475569",
      halo: "#ffffff", panel: "#ffffff", ground: "#94a3b8", groundFill: "#e2e8f0", spring: "#475569",
      velocity: "#16a34a", acceleration: "#ea580c", force: "#9333ea",
      series: ["#2563eb", "#dc2626", "#059669", "#d97706", "#7c3aed", "#db2777", "#0891b2", "#65a30d"] },
    DARK: { ink: "#e2e8f0", muted: "#94a3b8", faint: "#64748b", grid: "#1e293b", gridMajor: "#334155", axis: "#94a3b8",
      halo: "#0b1220", panel: "#111a2b", ground: "#64748b", groundFill: "#1e293b", spring: "#cbd5e1",
      velocity: "#4ade80", acceleration: "#fb923c", force: "#c084fc",
      series: ["#60a5fa", "#f87171", "#34d399", "#fbbf24", "#a78bfa", "#f472b6", "#22d3ee", "#a3e635"] },
  };
  /** In-scene marks follow the brightness of generated environment art; HUD panels follow the workspace theme. */
  let sceneTone: "LIGHT" | "DARK" | null = null;
  const hudPalette = () => palettes[host.data().theme === "DARK" ? "DARK" : "LIGHT"];
  const palette = () => sceneTone ? palettes[sceneTone] : hudPalette();
  const color = (index: number) => palette().series[((index % 8) + 8) % 8];

  function niceStep(span: number, target = 6) {
    if (!(span > 0) || !Number.isFinite(span)) return 1;
    const raw = span / Math.max(1, target), power = 10 ** Math.floor(Math.log10(raw)), f = raw / power;
    return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10) * power;
  }
  /** scale: typical magnitude of the quantity; only values negligible against it print as 0 (so 5×10⁻²⁹ kg is shown). */
  /** "label (symbol)", or the label alone when the quantity has no symbol of its own. */
  function named(meta: { label: string; symbol: string }) {
    return meta.symbol && meta.symbol.toLowerCase() !== meta.label.toLowerCase() ? meta.label + " (" + meta.symbol + ")" : meta.label;
  }
  /** Shortens a one-line label with an ellipsis until it fits the given width. */
  function fit(label: PixiNS.Text, width: number) {
    const full = label.text;
    for (let keep = full.length; label.width > width && keep > 1; keep--) label.text = full.slice(0, keep - 1).trimEnd() + "…";
    return label;
  }

  function format(value: number, unit = "", digits = 3, scale = 0) {
    if (!Number.isFinite(value)) return "—";
    let text: string;
    const magnitude = Math.abs(value);
    if (magnitude === 0 || magnitude < (scale > 0 ? scale * 1e-9 : 1e-300)) text = "0";
    else if (magnitude >= 1e5 || magnitude < 1e-3) {
      const [mantissa, exponent] = value.toExponential(2).split("e");
      const sup: Record<string, string> = { "-": "⁻", "0": "⁰", "1": "¹", "2": "²", "3": "³", "4": "⁴", "5": "⁵", "6": "⁶", "7": "⁷", "8": "⁸", "9": "⁹" };
      text = mantissa + "×10" + exponent.replace("+", "").split("").map(ch => sup[ch] ?? ch).join("");
    }
    else text = String(Number(value.toFixed(Math.max(0, digits - Math.max(0, Math.floor(Math.log10(magnitude)) + 1) + 1))));
    text = text.replace("-", "−");
    return unit ? text + " " + unit : text;
  }
  function tickLabel(value: number, step: number) {
    const decimals = Math.max(0, Math.min(6, -Math.floor(Math.log10(step) + 1e-9) + (step / 10 ** Math.floor(Math.log10(step)) === 2.5 ? 1 : 0)));
    const text = Math.abs(value) < step * 1e-6 ? "0" : value.toFixed(decimals);
    return text.replace("-", "−");
  }

  function text(content: string, options: { size?: number; color?: string; weight?: "400" | "500" | "600" | "700"; halo?: boolean;
    anchorX?: number; anchorY?: number; mono?: boolean; hud?: boolean } = {}) {
    const p = options.hud ? hudPalette() : palette();
    const style: Partial<PixiNS.TextStyleOptions> = {
      fontFamily: options.mono ? '"JetBrains Mono", "SFMono-Regular", Consolas, "Liberation Mono", monospace' : FONT,
      fontSize: options.size ?? 12, fontWeight: options.weight ?? "500", fill: options.color ?? p.ink,
    };
    if (options.halo !== false) style.stroke = { color: p.halo, width: 3, join: "round" };
    const item = new PIXI.Text({ text: content, style });
    item.anchor.set(options.anchorX ?? 0, options.anchorY ?? 0);
    return item;
  }

  function camera(view: Rect, bounds: Bounds, equalScale: boolean) {
    const spanX = Math.max(bounds.maxX - bounds.minX, 1e-9), spanY = Math.max(bounds.maxY - bounds.minY, 1e-9);
    let sx = view.w / spanX, sy = view.h / spanY;
    if (equalScale) sx = sy = Math.min(sx, sy);
    const offsetX = (view.w - spanX * sx) / 2, offsetY = (view.h - spanY * sy) / 2;
    const toScreen = (x: number, y: number): Pt => ({
      x: view.x + offsetX + (x - bounds.minX) * sx,
      y: view.y + view.h - offsetY - (y - bounds.minY) * sy,
    });
    const visible: Bounds = {
      minX: bounds.minX - offsetX / sx, maxX: bounds.maxX + offsetX / sx,
      minY: bounds.minY - offsetY / sy, maxY: bounds.maxY + offsetY / sy,
    };
    return { view, bounds, visible, sx, sy, toScreen,
      toWorld: (px: number, py: number): Pt => ({ x: bounds.minX + (px - view.x - offsetX) / sx, y: bounds.minY + (view.y + view.h - offsetY - py) / sy }) };
  }
  type Camera = ReturnType<typeof camera>;

  function dashed(g: PixiNS.Graphics, x1: number, y1: number, x2: number, y2: number, dash = 6, gap = 5) {
    const length = Math.hypot(x2 - x1, y2 - y1);
    if (!(length > 0)) return g;
    const ux = (x2 - x1) / length, uy = (y2 - y1) / length;
    for (let s = 0; s < length; s += dash + gap) {
      const e = Math.min(length, s + dash);
      g.moveTo(x1 + ux * s, y1 + uy * s).lineTo(x1 + ux * e, y1 + uy * e);
    }
    return g;
  }
  function arrow(g: PixiNS.Graphics, x1: number, y1: number, x2: number, y2: number,
    style: { color: string; width?: number; alpha?: number; head?: number } ) {
    const length = Math.hypot(x2 - x1, y2 - y1);
    if (!(length > 1.5)) return false;
    const width = style.width ?? 2.5, head = Math.min(style.head ?? 9 + width, length * 0.6);
    const ux = (x2 - x1) / length, uy = (y2 - y1) / length;
    const bx = x2 - ux * head, by = y2 - uy * head, nx = -uy * head * 0.45, ny = ux * head * 0.45;
    g.moveTo(x1, y1).lineTo(bx + ux * 0.5, by + uy * 0.5).stroke({ color: style.color, width, alpha: style.alpha ?? 1, cap: "round" });
    g.poly([x2, y2, bx + nx, by + ny, bx - nx, by - ny]).fill({ color: style.color, alpha: style.alpha ?? 1 });
    return true;
  }
  function hatch(g: PixiNS.Graphics, x1: number, x2: number, y: number, direction: 1 | -1, color: string) {
    g.moveTo(x1, y).lineTo(x2, y).stroke({ color, width: 2 });
    for (let x = x1 + 4; x < x2; x += 9) g.moveTo(x, y).lineTo(x - 7, y + 7 * direction);
    g.stroke({ color, width: 1, alpha: 0.7 });
  }
  function spring(g: PixiNS.Graphics, x1: number, x2: number, y: number, amplitude: number, color: string) {
    const length = x2 - x1, lead = Math.min(12, Math.abs(length) * 0.12), coils = 12;
    const start = x1 + Math.sign(length) * lead, end = x2 - Math.sign(length) * lead, step = (end - start) / (coils * 2);
    g.moveTo(x1, y).lineTo(start, y);
    for (let i = 0; i < coils * 2; i++) g.lineTo(start + step * (i + 0.5), y + (i % 2 ? amplitude : -amplitude));
    g.lineTo(end, y).lineTo(x2, y).stroke({ color, width: 1.8, join: "round" });
  }
  function body(g: PixiNS.Graphics, x: number, y: number, radius: number, fill: string) {
    const p = palette();
    g.circle(x, y + 1.5, radius + 1).fill({ color: "#000000", alpha: 0.12 });
    g.circle(x, y, radius).fill({ color: fill }).stroke({ color: p.halo, width: 2, alpha: 0.9 });
    g.circle(x - radius * 0.32, y - radius * 0.36, radius * 0.34).fill({ color: "#ffffff", alpha: 0.45 });
  }

  /** Map of field key → [min, max] across the whole backend timeline. */
  function fieldRanges() {
    const result: Record<string, { min: number; max: number }> = {};
    for (const [key, meta] of Object.entries(host.data().scene.fields)) result[key] = { min: meta.min, max: meta.max };
    return result;
  }

  // ------------------------------------------------------------------ standard scene
  type SceneOptions = { bodies?: boolean; labels?: boolean; hud?: boolean; strobe?: boolean; vectors?: boolean; axes?: boolean;
    trails?: boolean; grid?: boolean; links?: boolean; supports?: boolean; angles?: boolean };
  function standardScene(options: SceneOptions = {}) {
    /* The stage shows the phenomenon only: clock, legend, live values and the verification state belong to
       the surrounding interface, which renders them from the same solver data. */
    const show = { bodies: true, labels: true, hud: false, strobe: true, vectors: true, axes: true, trails: true,
      links: true, supports: true, angles: true, ...options, hud: false };
    const root = new PIXI.Container();
    root.label = "physlive-standard-scene";
    app.stage.addChild(root);
    const backdropLayer = new PIXI.Container(), propLayer = new PIXI.Container(), hudPanel = new PIXI.Graphics();
    const staticLayer = new PIXI.Graphics(), dynamicLayer = new PIXI.Graphics(), bodyLayer = new PIXI.Graphics();
    const staticText = new PIXI.Container(), dynamicText = new PIXI.Container(), artLayer = new PIXI.Container();
    const decorLayer = new PIXI.Container(), connectorLayer = new PIXI.Container(), gaugeLayer = new PIXI.Container();
    const followLayer = new PIXI.Container(), instrumentLayer = new PIXI.Container(), fixtureLayer = new PIXI.Container();
    root.addChild(backdropLayer, decorLayer, staticLayer, propLayer, fixtureLayer, connectorLayer, dynamicLayer, bodyLayer, artLayer, followLayer,
      gaugeLayer, instrumentLayer, hudPanel, staticText, dynamicText);
    /* Everything in these layers is placed by the kit from solver data (propLayer holds static generated props). */
    const kitLayers: PixiNS.Container[] = [backdropLayer, decorLayer, staticLayer, propLayer, fixtureLayer, connectorLayer, dynamicLayer, bodyLayer,
      artLayer, followLayer, gaugeLayer, instrumentLayer, hudPanel, staticText, dynamicText];
    /** Generated SVG textures for the physical roles the kit infers from solver data. */
    const decor: { surface?: PixiNS.Texture; support?: PixiNS.Texture; connector?: PixiNS.Texture } = {};
    const connectors = new Map<string, PixiNS.Sprite>();
    type ArtOptions = { size?: number; sizeMeters?: number; autoScale?: boolean; facing?: "right" | "left" | "none";
      rotate?: "none" | "velocity" | "link"; anchor?: "bottom" | "center";
      /** Name of the track/road/line the body moves on: bodies with the same name share one line. */
      line?: string;
      /** A rigid body: nothing solid may pass through it (checked against the solver motion). */
      solid?: boolean };
    /** Participant artwork supplied by generated code, positioned by the kit. */
    const attached = new Map<string, { item: PixiNS.Container; options: ArtOptions; baseW: number; baseH: number; direction: number;
      bounds: { x: number; y: number; width: number; height: number };
      /** Solver point (screen) and the exact transform the kit gave the artwork in the last update. */
      point?: Pt; placed?: { x: number; y: number; sx: number; sy: number; rotation: number; pivotY: number; pivotX: number } }>();
    /** Generated display objects that ride along with a participant (labels, riders, attached decorations). */
    const followers = new Map<PixiNS.Container, { id: string; dx: number; dy: number; placed?: Pt }>();
    /** Solver-driven straight connections (ropes, wires, rods) between participants and/or fixed world points. */
    /* a participant id, a point [x, y] of the scene frame, or a point [x, y, id] of participant id's own frame
       (independent systems laid out side by side each keep their own origin: a pivot, a wall, a track start) */
    type LinkEnd = string | [number, number] | [number, number, string];
    let links: Array<{ from: LinkEnd; to: LinkEnd; color?: string; width: number; dashed: boolean }> = [];
    /**
     * Instruments: generated artwork whose moving part the kit drives from one solver field through a
     * mapping the generator DECLARES (no built-in instrument shapes): the part turns about a pivot,
     * slides or is revealed along a path, scales, fades, or travels round a path at a speed ∝ the field.
     * The scale's low/high ends map to drive.from/drive.to, so any dial, gauge, tank, lamp, piston … works.
     */
    type DriveProperty = "rotate" | "translate" | "scale" | "reveal" | "opacity" | "travel" | "none";
    type Drive = { property: DriveProperty; pivot: Pt; path: Pt[]; from: number; to: number; copies: number;
      useFieldAngle: boolean; min: number | null; max: number | null };
    type Instrument = { key: string; participantId: string; drive: Drive; art: PixiNS.Container; parts: PixiNS.Container[];
      movers: PixiNS.Container[]; mask: PixiNS.Graphics | null;
      label: string; inner: PixiNS.Container; caption: PixiNS.Text | null; readout: PixiNS.Text | null;
      ticks: PixiNS.Container; baseW: number; baseH: number; lo: number; hi: number; k: number; center: Pt;
      flow: { t: number[]; q: number[]; norm: number } | null;
      shown: { t: number; value: number; fraction: number } | null;
      /** the instrument whose artwork this one's moving part is drawn on (one apparatus, several fields) */
      on: Instrument | null };
    let instruments: Instrument[] = [];
    /**
     * Fixtures: generated artwork of things that do not move (wall, stop, pulley block, fixed charge, platform…)
     * placed by the kit at a world position in metres — a number or the name of a plan parameter, so the
     * artwork follows parameter edits. A solid fixture is also a physical claim that the cross-check verifies:
     * no moving participant may pass through it.
     */
    type Coordinate = number | string | null;
    /* Orientation and sense can come from the plan too: rotation (screen degrees, clockwise) = angleScale × angle,
       where angle is a number or a parameter (e.g. an incline angle, a field direction), and a parameter in flipBy
       mirrors the art when its value is negative (field direction, polarity, current sense). */
    type Fixture = { item: PixiNS.Container; x: Coordinate; y: Coordinate; anchor: Pt; sizeMeters: number; solid: boolean;
      angle: Coordinate; angleScale: number; flipBy: string;
      /** participant whose own frame x / y are measured in ('' = the scene frame) */
      frame: string;
      baseW: number; baseH: number; world: Pt | null; screen: Pt | null; k: number; rotation: number; mirror: number };
    let fixtures: Fixture[] = [];
    /** Participants whose non-spatial state is visible on stage (instrument or meter card), rebuilt by build(). */
    let meterCovered = new Set<string>();
    let updatedThisFrame = false;
    let laneHeight = 80;
    /** Viewport-sized environment artwork (generated SVG) redrawn from the physics layout. */
    let backdropSource: ((layout: SceneLayout) => string | Promise<string>) | null = null;
    let backdropSprite: PixiNS.Sprite | null = null, backdropTimer: ReturnType<typeof setTimeout> | null = null, backdropVersion = 0;
    let groundY: number | null = null;
    let disposed = false;
    /** Declarative environment art, optionally anchored so one horizontal line of it sits on the physical reference line. */
    let environmentSpec: { svg: string; anchorY: number } | null = null;
    const illustrated = () => backdropSource !== null || environmentSpec !== null;

    type Track = { p: SceneParticipant; color: string; lane: number;
      screen: (values: Record<string, number>) => Pt | null; path: Array<Pt & { t: number }>; strobe: Array<Pt & { t: number }>;
      monotonic: boolean; originX: number; quiet?: boolean;
      /** Line the body moves on (bodies on the same line can touch) and the side (±1 along it) of the body it touches. */
      lineKey?: string | null; along?: "x" | "y" | null; contact?: number; label?: PixiNS.Text; vectorLabels: Partial<Record<Kind, PixiNS.Text>>; angleLabel?: PixiNS.Text; wallX?: number };
    let tracks: Track[] = [];
    let medium: Track[] = [];
    /** Instrument panel for state quantities (no spatial motion): one meter per solver field. */
    type Gauge = { key: string; x: number; y: number; w: number; bar: PixiNS.Graphics; value: PixiNS.Text; min: number; max: number; color: string };
    let gauges: Gauge[] = [];
    let cam: Camera | null = null;
    let mode: "plane" | "lanes" | "columns" | "board" = "board";
    let vectorScale: Record<Kind, number> = { velocity: 0, acceleration: 0, force: 0 };
    let laneY: (lane: number) => number = () => 0;
    let columnX: (lane: number) => number = () => 0;
    let hudTime: PixiNS.Text | null = null, hudBadge: PixiNS.Text | null = null;
    let legend: Array<{ dot: PixiNS.Graphics; name: PixiNS.Text; values: PixiNS.Text; track: Track }> = [];
    let lastWidth = 0, lastHeight = 0, compact = false, exaggerated = false;

    const value = (values: Record<string, number>, key?: string) => key === undefined ? NaN : values[key];
    function vectorsOf(track: Track, values: Record<string, number>): Array<{ kind: Kind; dx: number; dy: number; magnitude: number }> {
      const f = track.p.fields, out: Array<{ kind: Kind; dx: number; dy: number; magnitude: number }> = [];
      const axisVector = (key: string | undefined, kind: Kind) => {
        const v = value(values, key);
        if (!Number.isFinite(v)) return;
        if (track.p.vertical) out.push({ kind, dx: 0, dy: v, magnitude: Math.abs(v) });
        else out.push({ kind, dx: v, dy: 0, magnitude: Math.abs(v) });
      };
      if (track.p.dims === 2) {
        const vx = value(values, f.vx), vy = value(values, f.vy);
        if (Number.isFinite(vx) && Number.isFinite(vy)) out.push({ kind: "velocity", dx: vx, dy: vy, magnitude: Math.hypot(vx, vy) });
        const ax = value(values, f.ax), ay = value(values, f.ay);
        if (Number.isFinite(ax) && Number.isFinite(ay)) out.push({ kind: "acceleration", dx: ax, dy: ay, magnitude: Math.hypot(ax, ay) });
      } else if (track.p.dims === 1) {
        axisVector(f.velocity, "velocity");
        axisVector(f.acceleration, "acceleration");
        axisVector(f.force, "force");
      }
      return out;
    }

    function clearText(container: PixiNS.Container) {
      for (const child of container.removeChildren()) child.destroy();
    }

    // ---------------------------------------------------------------- instruments
    /** Readable number of meter cards beside a moving scene; beyond it the values stay in the data table. */
    const METER_STRIP_MAX = 8;
    /** A participant whose changing state is not shown by motion: no place in space, or a fixed place (probe, source). */
    function needsMeter(participant: SceneParticipant) {
      if (participant.dims === 0) return true;
      if (!participant.stationary) return false;
      return Object.values(host.data().scene.fields).some(meta => meta.participantId === participant.id
        && meta.max - meta.min > 1e-12 * Math.max(1, Math.abs(meta.max), Math.abs(meta.min)));
    }
    /** Meter-card fields for state-only participants that have no instrument (the declared state_value first). */
    function meterKeys(stateOnly: SceneParticipant[], max: number): FieldMeta[] {
      const scene = host.data().scene, out: FieldMeta[] = [];
      for (const participant of stateOnly) {
        if (out.length >= max || instruments.some(item => item.participantId === participant.id && item.drive.property !== "none")) continue;
        const own = Object.values(scene.fields).filter(meta => meta.participantId === participant.id && (meta.kind !== "angle" || meta.unit !== "rad"));
        const pick = own.find(meta => meta.role === "state_value") ?? own.find(meta => meta.max - meta.min > 1e-12 * Math.max(1, Math.abs(meta.max))) ?? own[0];
        if (pick) out.push(pick);
      }
      return out;
    }
    /** Readable scale for an instrument: includes 0 when the data is near it, rounded to nice ticks. */
    function niceRange(min: number, max: number) {
      let lo = min, hi = max;
      if (lo >= 0 && lo <= 0.6 * hi) lo = 0;
      if (hi <= 0 && hi >= 0.6 * lo) hi = 0;
      /* a constant reading still gets a readable scale from 0 to its value (or 0…1 for a constant 0) */
      if (!(hi > lo)) { lo = Math.min(0, min); hi = Math.max(0, max); if (!(hi > lo)) hi = lo + 1; }
      const step = niceStep(hi - lo, 4);
      lo = Math.floor(lo / step + 1e-9) * step; hi = Math.ceil(hi / step - 1e-9) * step;
      if (!(hi > lo)) hi = lo + step;
      return { lo, hi, step };
    }
    function angleOf(meta: FieldMeta | undefined, value: number) {
      return meta && /deg|°/.test(meta.unit) ? value * Math.PI / 180 : value;
    }
    function fractionOf(ins: Instrument, value: number) {
      return Math.max(0, Math.min(1, (value - ins.lo) / (ins.hi - ins.lo)));
    }
    /** Cumulative ∫value dt over the timeline (a travelling part's phase, so its speed ∝ the field). */
    function flowTable(key: string, laps: number) {
      const frames = host.data().timeline.frames, t: number[] = [], q: number[] = [];
      let acc = 0, peak = 0;
      frames.forEach((frame, i) => {
        const v = frame.values[key];
        if (i) acc += (frame.t - frames[i - 1].t) * (v + frames[i - 1].values[key]) / 2;
        peak = Math.max(peak, Math.abs(v));
        t.push(frame.t); q.push(acc);
      });
      const duration = Math.max(host.data().timeline.durationSeconds, 1e-300);
      /* at the peak |value| held for the whole run the part completes `laps` laps */
      return { t, q, norm: peak > 0 && laps > 0 ? peak * duration / laps : Infinity };
    }
    function flowAt(table: { t: number[]; q: number[] }, time: number) {
      const { t, q } = table;
      if (!t.length) return 0;
      if (time <= t[0]) return q[0];
      if (time >= t[t.length - 1]) return q[q.length - 1];
      let lo = 0, hi = t.length - 1;
      while (lo + 1 < hi) { const mid = (lo + hi) >>> 1; if (t[mid] <= time) lo = mid; else hi = mid; }
      const r = t[hi] === t[lo] ? 0 : (time - t[lo]) / (t[hi] - t[lo]);
      return q[lo] + (q[hi] - q[lo]) * r;
    }
    /** Point and direction at fraction s (0…1) of a polyline's length; closed paths wrap. */
    function alongPath(path: Pt[], s: number, closed: boolean) {
      const hit = pointAlong(path, 0, closed), total = hit.total;
      const fraction = closed ? ((s % 1) + 1) % 1 : Math.max(0, Math.min(1, s));
      return pointAlong(path, fraction * total, closed);
    }
    const scaleOf = (ins: Instrument) => Math.max(Math.abs(ins.lo), Math.abs(ins.hi));
    const lerp = (ins: Instrument, f: number) => ins.drive.from + f * (ins.drive.to - ins.drive.from);
    /** Lay out instrument cells inside rect (null = none visible) and draw their scale marks where the mapping has a place for them. */
    function placeInstruments(rect: Rect | null, p: ReturnType<typeof palette>) {
      const scene = host.data().scene;
      instruments.forEach((ins, index) => {
        /* the scale follows the current solver timeline unless the generator fixed it (parameter edits recompute it) */
        const current = scene.fields[ins.key], d = ins.drive;
        if (current) {
          const fixed = d.min !== null && d.max !== null && d.max > d.min;
          const range = fixed ? { lo: d.min!, hi: d.max! } : niceRange(current.min, current.max);
          ins.lo = range.lo; ins.hi = range.hi;
          if (d.property === "travel") ins.flow = flowTable(ins.key, d.to);
        }
        clearText(ins.ticks);
        ins.caption = null; ins.readout = null;
        ins.inner.visible = !!rect;
        if (!rect) return;
        const standalone = instruments.filter(item => !item.on), slot = standalone.indexOf(ins.on ?? ins);
        const n = standalone.length, columns = mode === "board" ? Math.min(n, 4) : n, rows = Math.ceil(n / columns);
        const cw = rect.w / columns, ch = rect.h / rows;
        const cell = { x: rect.x + (slot % columns) * cw, y: rect.y + Math.floor(slot / columns) * ch, w: cw, h: ch };
        const captionH = 34, area = { x: cell.x + 6, y: cell.y + 4, w: Math.max(10, cell.w - 12), h: Math.max(10, cell.h - captionH - 8) };
        if (ins.on) {
          /* drawn in the frame of the apparatus it belongs to */
          ins.k = ins.on.k; ins.center = ins.on.center;
          ins.inner.pivot.set(ins.on.baseW / 2, ins.on.baseH / 2);
        } else {
          ins.k = Math.min(area.w / Math.max(1, ins.baseW), area.h / Math.max(1, ins.baseH)) * 0.9;
          ins.center = { x: area.x + area.w / 2, y: area.y + area.h / 2 };
          ins.inner.pivot.set(ins.baseW / 2, ins.baseH / 2);
        }
        const k = ins.k;
        /* the apparatus's readings share its caption row, one column per field */
        const group = instruments.filter(item => (item.on ?? item) === (ins.on ?? ins)), column = group.indexOf(ins);
        const colW = cell.w / group.length, colX = cell.x + colW * (column + 0.5);
        const meta = scene.fields[ins.key];
        const track = tracks.find(item => item.p.id === ins.participantId);
        const who = scene.participants.length > 1 && track && group.length === 1 ? track.p.label + " · " : "";
        ins.caption = fit(text(ins.label || (who + (meta ? named(meta) : ins.key)),
          { size: 11.5, weight: "600", color: p.muted, anchorX: 0.5, anchorY: 0 }), colW - 8);
        ins.caption.position.set(colX, cell.y + cell.h - captionH);
        ins.readout = text("", { size: 14, weight: "700", color: p.ink, anchorX: 0.5, anchorY: 0, mono: true });
        ins.readout.position.set(colX, cell.y + cell.h - captionH + 15);
        ins.ticks.addChild(ins.caption, ins.readout);
        /* scale marks (lo / mid / hi) wherever the declared mapping puts those values */
        const local = (x: number, y: number): Pt => ({ x: ins.center.x + (x - ins.baseW / 2) * k, y: ins.center.y + (y - ins.baseH / 2) * k });
        const unit = meta?.unit ?? "";
        const mark = (value: number, at: Pt, dx: number, dy: number) => {
          const label = text(format(value, unit, 3, scaleOf(ins)), { size: 10, color: p.muted, anchorX: 0.5 - dx * 0.5, anchorY: 0.5 - dy * 0.5, weight: "500" });
          label.position.set(at.x + dx * 4, at.y + dy * 4); ins.ticks.addChild(label);
        };
        const values: Array<[number, number]> = [[ins.lo, 0], [(ins.lo + ins.hi) / 2, 0.5], [ins.hi, 1]];
        /* a part drawn on a set-up has its reading printed under the drawing; its scale is the generator's own art */
        if (ins.on) {
          /* no scale marks */
        } else if (d.property === "rotate" && !d.useFieldAngle && ins.parts[0]) {
          /* on the largest circle about the pivot that stays inside the instrument body */
          const b = ins.art.getLocalBounds();
          const reach = Math.max(1, Math.min(d.pivot.x - b.x, b.x + b.width - d.pivot.x, d.pivot.y - b.y, b.y + b.height - d.pivot.y)) * 1.04;
          for (const [value, f] of values) {
            const a = lerp(ins, f) * Math.PI / 180 - Math.PI / 2, at = local(d.pivot.x + Math.cos(a) * reach, d.pivot.y + Math.sin(a) * reach);
            mark(value, at, Math.cos(a), Math.sin(a));
          }
        } else if ((d.property === "translate" || d.property === "reveal") && d.path.length >= 2) {
          for (const [value, f] of values) {
            const q = alongPath(d.path, lerp(ins, f), false), at = local(q.x, q.y);
            /* beside the path (to its right-hand side on screen) */
            mark(value, at, -Math.sin(q.angle), Math.cos(q.angle));
          }
        }
      });
      /* parts travelling on one drawing in the same unit share one speed scale, so the drawing compares them
         (twice the current = twice as fast, whatever the length of each path); the first one sets the scale */
      const scales = new Map<string, number>();
      for (const ins of instruments) {
        if (ins.drive.property !== "travel" || !ins.flow || !Number.isFinite(ins.flow.norm) || ins.drive.path.length < 2) continue;
        const group = instruments.indexOf(ins.on ?? ins) + "\u0000" + (scene.fields[ins.key]?.unit ?? "");
        const length = pointAlong(ins.drive.path, 0, false).total;
        if (!(length > 0)) continue;
        const perUnit = scales.get(group);
        if (perUnit === undefined) scales.set(group, length / ins.flow.norm);
        else ins.flow.norm = length / perUnit;
      }
    }
    function drawInstrument(ins: Instrument, value: number, time: number) {
      const d = ins.drive, f = fractionOf(ins, value), amount = lerp(ins, f);
      ins.inner.position.set(ins.center.x, ins.center.y);
      ins.inner.scale.set(ins.k, ins.k);
      ins.inner.rotation = 0;
      ins.art.visible = true; ins.art.alpha = 1;
      for (const mover of ins.movers) { mover.position.set(0, 0); mover.pivot.set(0, 0); mover.rotation = 0; mover.scale.set(1, 1); mover.alpha = 1; mover.visible = true; }
      const [mover] = ins.movers;
      if (mover && d.property === "rotate") {
        mover.pivot.set(d.pivot.x, d.pivot.y); mover.position.set(d.pivot.x, d.pivot.y);
        mover.rotation = d.useFieldAngle ? -angleOf(host.data().scene.fields[ins.key], value) : amount * Math.PI / 180;
      } else if (mover && d.property === "scale") {
        const s = Math.max(0, amount);
        mover.pivot.set(d.pivot.x, d.pivot.y); mover.position.set(d.pivot.x, d.pivot.y); mover.scale.set(s, s);
      } else if (mover && d.property === "opacity") {
        mover.alpha = Math.max(0, Math.min(1, amount));
      } else if (mover && d.property === "translate" && d.path.length >= 2) {
        const q = alongPath(d.path, amount, false);
        mover.position.set(q.x - d.path[0].x, q.y - d.path[0].y);
      } else if (mover && d.property === "reveal" && d.path.length >= 2 && ins.mask) {
        /* the part is visible from path[0] up to the fraction `amount` of the path, perpendicular cut */
        const a = d.path[0], b = d.path[d.path.length - 1], length = Math.hypot(b.x - a.x, b.y - a.y) || 1;
        const ux = (b.x - a.x) / length, uy = (b.y - a.y) / length, nx = -uy, ny = ux;
        const big = 4 * Math.hypot(ins.baseW, ins.baseH), reach = Math.max(0, Math.min(1, amount)) * length;
        ins.mask.clear().poly([a.x - ux * big + nx * big, a.y - uy * big + ny * big, a.x + ux * reach + nx * big, a.y + uy * reach + ny * big,
          a.x + ux * reach - nx * big, a.y + uy * reach - ny * big, a.x - ux * big - nx * big, a.y - uy * big - ny * big]).fill({ color: "#ffffff" });
        mover.mask = ins.mask;
      } else if (d.property === "travel" && d.path.length >= 2 && ins.flow) {
        const phase = Number.isFinite(ins.flow.norm) ? flowAt(ins.flow, time) / ins.flow.norm : 0;
        const closed = Math.hypot(d.path[0].x - d.path[d.path.length - 1].x, d.path[0].y - d.path[d.path.length - 1].y) < 1e-6;
        ins.movers.forEach((copy, i) => {
          const q = alongPath(d.path, i / ins.movers.length + phase, closed);
          copy.position.set(q.x - d.path[0].x, q.y - d.path[0].y);
        });
      }
      const meta = host.data().scene.fields[ins.key];
      if (ins.readout) ins.readout.text = d.property === "none" || !meta ? "" : meta && meta.kind === "angle" && meta.unit === "rad"
        ? format(value * 180 / Math.PI, "°").replace(" °", "°") : format(value, meta?.unit ?? "", 3, scaleOf(ins));
      ins.shown = { t: time, value, fraction: f };
    }
    /**
     * Register an instrument: art = static body, part = the moving part (drawn in the same local frame),
     * drive = how the field moves it (see Drive). Without part/drive it is a static picture of the participant.
     */
    function instrument(spec: { field: string; art?: PixiNS.Container | null; part?: PixiNS.Container | null; drive?: Partial<Drive>; label?: string;
      on?: string | PixiNS.Container }) {
      const scene = host.data().scene, meta = spec && scene.fields[spec.field];
      /* field "" = a drawing of the set-up itself: static, no reading; other instruments draw their parts on it */
      const setup = !!spec && spec.field === "" && !spec.part;
      if (!spec || (!meta && !setup)) throw Error('instrument(): unknown solver field "' + (spec && spec.field) + '". Fields: ' + Object.keys(scene.fields).join(", "));
      /* on = an instrument registered before (its field, or the container instrument() returned for it): this part
         is drawn on that instrument's artwork, in its coordinates (one set-up showing several fields) */
      const on = spec.on ? instruments.find(item => !item.on && (typeof spec.on === "string" ? item.key === spec.on : item.inner === spec.on)) ?? null : null;
      if (spec.on && !on) throw Error('instrument(): "on" must name an instrument registered before ("' + String(spec.on) + '").');
      if (on && !spec.art) spec.art = new PIXI.Container();
      if (!spec.art || typeof spec.art.getLocalBounds !== "function")
        throw Error("instrument(): pass the instrument body as a PIXI display object (e.g. new PIXI.Sprite(await api.svgTexture(svg))).");
      if (instruments.some(item => item.art === spec.art)) throw Error("instrument(): this artwork is already an instrument.");
      const raw = spec.drive ?? {}, properties: DriveProperty[] = ["rotate", "translate", "scale", "reveal", "opacity", "travel", "none"];
      const property = (properties.includes(raw.property as DriveProperty) ? raw.property : spec.part ? undefined : "none") as DriveProperty | undefined;
      if (!property) throw Error("instrument(): drive.property must be one of " + properties.join(", ") + ".");
      const point = (value: unknown): Pt | null => {
        const q = value as Pt;
        return q && Number.isFinite(Number(q.x)) && Number.isFinite(Number(q.y)) ? { x: Number(q.x), y: Number(q.y) } : null;
      };
      const path = (Array.isArray(raw.path) ? raw.path : []).map(point).filter((q): q is Pt => !!q);
      if (["translate", "reveal", "travel"].includes(property) && path.length < 2)
        throw Error('instrument(): drive.property "' + property + '" needs drive.path with at least 2 points.');
      if (property !== "none" && !spec.part) throw Error('instrument(): drive.property "' + property + '" needs the moving part (part).');
      const number = (value: unknown, fallback: number) => Number.isFinite(Number(value)) ? Number(value) : fallback;
      const nullable = (value: unknown) => value === null || value === undefined || !Number.isFinite(Number(value)) ? null : Number(value);
      spec.art.removeFromParent();
      spec.art.position.set(0, 0); spec.art.scale.set(1); spec.art.rotation = 0;
      const b = spec.art.getLocalBounds();
      const baseW = on ? on.baseW : Math.max(1, b.x + b.width), baseH = on ? on.baseH : Math.max(1, b.y + b.height);
      const drive: Drive = { property, pivot: point(raw.pivot) ?? { x: baseW / 2, y: baseH / 2 }, path,
        from: number(raw.from, 0),
        to: number(raw.to, 1), copies: Math.max(1, Math.min(60, Math.round(number(raw.copies, 1)))),
        useFieldAngle: !!raw.useFieldAngle, min: nullable(raw.min), max: nullable(raw.max) };
      const inner = new PIXI.Container(), ticks = new PIXI.Container();
      inner.addChild(spec.art);
      const parts: PixiNS.Container[] = [], movers: PixiNS.Container[] = [];
      let mask: PixiNS.Graphics | null = null;
      if (spec.part && property !== "none") {
        const count = property === "travel" ? drive.copies : 1;
        for (let i = 0; i < count; i++) {
          let part = spec.part;
          if (i > 0) {
            if (!(spec.part instanceof PIXI.Sprite)) break;
            part = new PIXI.Sprite(spec.part.texture);
            part.position.copyFrom(spec.part.position); part.scale.copyFrom(spec.part.scale);
          }
          const mover = new PIXI.Container();
          part.removeFromParent(); mover.addChild(part); inner.addChild(mover);
          parts.push(part); movers.push(mover);
        }
        if (property === "reveal") { mask = new PIXI.Graphics(); inner.addChild(mask); }
      }
      instrumentLayer.addChild(inner, ticks);
      instruments.push({ key: spec.field, participantId: meta ? meta.participantId : "", drive, art: spec.art, parts, movers, mask,
        label: typeof spec.label === "string" ? spec.label.slice(0, 60) : "", inner, caption: null, readout: null, ticks,
        baseW, baseH, lo: 0, hi: 1, k: 1, center: { x: 0, y: 0 }, flow: null, shown: null, on });
      build();
      return inner;
    }
    function coordinate(value: Coordinate): number | null {
      if (typeof value === "number") return Number.isFinite(value) ? value : null;
      if (typeof value === "string" && value) {
        const parameters = host.data().parameters ?? {};
        return Number.isFinite(parameters[value]) ? parameters[value] : null;
      }
      return null;
    }
    /** null when a named parameter is missing (never guess a position) */
    function fixtureWorld(f: Fixture): Pt | null {
      const x = coordinate(f.x), y = coordinate(f.y);
      if ((typeof f.x === "string" && f.x && x === null) || (typeof f.y === "string" && f.y && y === null)) return null;
      return { x: x ?? 0, y: y ?? 0 };
    }
    /** Register static artwork at a world position (metres or a parameter name); anchor = the art's point that sits there. */
    function fixture(item: PixiNS.Container, spec: { x?: Coordinate; y?: Coordinate; anchor?: Partial<Pt>; sizeMeters?: number; solid?: boolean;
      angle?: Coordinate; angleScale?: number; flipBy?: string; frame?: string } = {}) {
      if (!item || typeof item.getLocalBounds !== "function") throw Error("fixture(): pass a PIXI display object.");
      if (spec.frame && !tracks.some(entry => entry.p.id === spec.frame)) throw Error('fixture(): unknown participant frame "' + spec.frame + '".');
      const parameters = host.data().parameters ?? {};
      for (const value of [spec.x, spec.y, spec.angle, spec.flipBy]) if (typeof value === "string" && value && !(value in parameters))
        throw Error('fixture(): unknown parameter "' + value + '". Parameters: ' + Object.keys(parameters).join(", "));
      item.removeFromParent(); item.scale.set(1); item.rotation = 0;
      const b = item.getLocalBounds();
      const anchor = { x: Number.isFinite(Number(spec.anchor?.x)) ? Number(spec.anchor!.x) : b.x + b.width / 2,
        y: Number.isFinite(Number(spec.anchor?.y)) ? Number(spec.anchor!.y) : b.y + b.height };
      fixtureLayer.addChild(item);
      fixtures.push({ item, x: spec.x ?? null, y: spec.y ?? null, anchor, sizeMeters: Number(spec.sizeMeters) > 0 ? Number(spec.sizeMeters) : 0,
        solid: !!spec.solid, angle: spec.angle ?? null, angleScale: Number.isFinite(Number(spec.angleScale)) && spec.angleScale !== 0 ? Number(spec.angleScale) : 1,
        flipBy: typeof spec.flipBy === "string" ? spec.flipBy : "", frame: typeof spec.frame === "string" ? spec.frame : "",
        baseW: Math.max(1, b.width), baseH: Math.max(1, b.height), world: null, screen: null, k: 1, rotation: 0, mirror: 1 });
      build();
      return item;
    }
    function placeFixtures() {
      const c = cam, W = app.screen.width, H = app.screen.height;
      for (const f of fixtures) {
        f.world = null; f.screen = null;
        f.item.visible = !!c;
        const w = fixtureWorld(f);
        if (!c || !w) continue;
        let at: Pt;
        const framed = f.frame ? framePoint(f.frame, w.x, w.y) : null;
        if (framed) at = mode === "lanes" ? { x: framed.x, y: framed.y + 15 } : framed;
        else if (mode === "lanes") {
          const roads = tracks.filter(track => track.p.dims > 0).map(track => laneY(track.lane) + 15);
          at = { x: c.toScreen(w.x, 0).x, y: roads.length ? Math.max(...roads) : c.view.y + c.view.h };
        } else if (mode === "columns") {
          const columns = tracks.filter(track => track.p.dims > 0).map(track => columnX(track.lane));
          at = { x: columns.length ? columns.reduce((a, b) => a + b, 0) / columns.length : c.view.x + c.view.w / 2, y: c.toScreen(0, w.y).y };
        } else at = c.toScreen(w.x, w.y);
        const perMetre = mode === "columns" ? c.sy : mode === "lanes" ? c.sx : Math.min(c.sx, c.sy);
        const readable = Math.max(40, Math.min(W, H) * 0.14);
        const target = f.sizeMeters > 0 ? Math.max(24, Math.min(Math.min(W, H) * 0.6, f.sizeMeters * perMetre)) : readable;
        f.k = target / Math.max(f.baseW, f.baseH);
        const angle = coordinate(f.angle);
        f.rotation = angle === null ? 0 : angle * f.angleScale * Math.PI / 180;
        const sense = f.flipBy ? coordinate(f.flipBy) : null;
        f.mirror = sense !== null && sense < 0 ? -1 : 1;
        f.world = w; f.screen = at;
      }
    }
    /**
     * Which bodies share a line on screen and which of them touch (from the solver data, never from names):
     * lanes / columns by lane; in the plane, 1-D bodies on their axis and planar bodies whose other coordinate
     * never changes. A body touching something on one side is drawn with that face at its solver point.
     */
    function lineOf(track: Track): { key: string; along: "x" | "y" } | null {
      const fields = host.data().scene.fields, f = track.p.fields;
      const still = (key?: string) => { const m = key ? fields[key] : undefined; return !!m && m.max - m.min <= 1e-9 * Math.max(1, Math.abs(m.max), Math.abs(m.min)); };
      if (track.p.dims === 0) return null;
      if (mode === "lanes") return { key: "lane" + track.lane, along: "x" };
      if (mode === "columns") return { key: "column" + track.lane, along: "y" };
      if (track.p.dims === 1) return track.p.vertical ? { key: "v0", along: "y" } : { key: "h0", along: "x" };
      if (still(f.y)) return { key: "h" + fields[f.y!].min.toPrecision(6), along: "x" };
      if (still(f.x)) return { key: "v" + (fields[f.x!].min + track.originX).toPrecision(6), along: "y" };
      return null;
    }
    function screenRelation(a: (values: Record<string, number>) => number | null, b: (values: Record<string, number>) => number | null) {
      const frames = host.data().timeline.frames;
      let pos = false, neg = false, closest = Infinity, step = 0, previous = NaN, firstCross: number | null = null;
      for (const frame of frames) {
        const va = a(frame.values), vb = b(frame.values);
        if (va === null || vb === null) continue;
        const d = vb - va;
        if (d > 1.5) pos = true; else if (d < -1.5) neg = true;
        if (pos && neg && firstCross === null) firstCross = frame.t;
        closest = Math.min(closest, Math.abs(d));
        if (Number.isFinite(previous)) step = Math.max(step, Math.abs(d - previous));
        previous = d;
      }
      const cross = pos && neg;
      return { cross, firstCross, contact: !cross && closest <= Math.max(1.5, step), side: pos ? 1 : neg ? -1 : 0 };
    }
    function findContacts() {
      const spatialTracks = tracks.filter(track => track.p.dims > 0);
      for (const track of spatialTracks) { const line = lineOf(track); track.lineKey = line?.key ?? null; track.along = line?.along ?? null; track.contact = 0; }
      const coordinate = (track: Track) => (values: Record<string, number>) => { const q = track.screen(values); return q ? (track.along === "y" ? q.y : q.x) : null; };
      const sides = new Map<Track, Set<number>>();
      const note = (track: Track, side: number) => { if (side) (sides.get(track) ?? sides.set(track, new Set()).get(track)!).add(side); };
      for (let i = 0; i < spatialTracks.length; i++) for (let j = i + 1; j < spatialTracks.length; j++) {
        const a = spatialTracks[i], b = spatialTracks[j];
        if (!a.lineKey || a.lineKey !== b.lineKey) continue;
        const r = screenRelation(coordinate(a), coordinate(b));
        if (r.contact) { note(a, r.side); note(b, -r.side); }
      }
      for (const f of fixtures) {
        if (!f.solid || !f.screen) continue;
        const box = f.item.getBounds();
        for (const track of spatialTracks) {
          if (!track.lineKey) continue;
          const across = track.along === "x" ? track.path[0]?.y : track.path[0]?.x;
          if (across === undefined || (track.along === "x" ? across < box.minY - 2 || across > box.maxY + 2 : across < box.minX - 2 || across > box.maxX + 2)) continue;
          const face = track.along === "x" ? f.screen.x : f.screen.y;
          const r = screenRelation(coordinate(track), () => face);
          if (r.contact) note(track, r.side);
        }
      }
      for (const [track, set] of sides) track.contact = set.size === 1 ? [...set][0] : 0;
    }
    function applyFixtures() {
      for (const f of fixtures) {
        if (!f.screen) { f.item.visible = false; continue; }
        if (f.item.parent !== fixtureLayer) fixtureLayer.addChild(f.item);
        f.item.pivot.set(f.anchor.x, f.anchor.y);
        f.item.position.set(f.screen.x, f.screen.y);
        f.item.scale.set(f.k * f.mirror, f.k); f.item.rotation = f.rotation;
        f.item.visible = true; f.item.alpha = 1; f.item.renderable = true;
      }
    }
    /** Keep a generated display object at a participant's solver position (+dx, +dy px): labels, riders, decorations. */
    function follow(id: string, item: PixiNS.Container, options: { dx?: number; dy?: number } = {}) {
      const track = tracks.find(entry => entry.p.id === id);
      if (!track || track.p.dims === 0)
        throw Error('follow(): "' + id + '" is not a moving participant. Moving participants: ' + tracks.filter(entry => entry.p.dims > 0).map(entry => entry.p.id).join(", "));
      if (!item || typeof item.getLocalBounds !== "function") throw Error("follow(): pass a PIXI display object.");
      followLayer.addChild(item);
      followers.set(item, { id, dx: Number(options.dx) || 0, dy: Number(options.dy) || 0 });
      return item;
    }
    /** Straight solver-bound connection between two ends: a participant id or a fixed world point [x, y] in metres. */
    function link(from: LinkEnd, to: LinkEnd, options: { color?: string; width?: number; dashed?: boolean } = {}) {
      const check = (end: LinkEnd) => {
        if (Array.isArray(end)) {
          if (end.length < 2 || !Number.isFinite(end[0]) || !Number.isFinite(end[1])) throw Error("link(): a point is [x, y] in metres, or [x, y, participantId] in that participant's own frame.");
          if (end.length > 2 && !tracks.some(entry => entry.p.id === end[2])) throw Error('link(): unknown participant frame "' + String(end[2]) + '".');
          return;
        }
        const track = tracks.find(entry => entry.p.id === end);
        if (!track || track.p.dims === 0) throw Error('link(): "' + String(end) + '" is not a moving participant.');
      };
      check(from); check(to);
      links.push({ from, to, color: options.color, width: Math.max(0.5, Math.min(12, Number(options.width) || 2)), dashed: !!options.dashed });
    }
    /** Screen position of the point (x, y) metres in a participant's own frame (where the kit laid that participant out). */
    function framePoint(id: string, x: number, y: number): Pt | null {
      const track = tracks.find(entry => entry.p.id === id);
      if (!cam || !track) return null;
      if (mode === "lanes") return { x: cam.toScreen(x, 0).x, y: laneY(track.lane) };
      if (mode === "columns") return { x: columnX(track.lane), y: cam.toScreen(0, y).y };
      return cam.toScreen(x + track.originX, y);
    }
    /** True when participants are laid out in frames of their own, so a bare scene point is ambiguous. */
    const ownFrames = () => tracks.some(track => track.originX !== 0);
    function endPoint(end: LinkEnd, values: Record<string, number>): Pt | null {
      if (Array.isArray(end)) return end.length > 2 ? framePoint(end[2] as string, end[0], end[1]) : cam ? cam.toScreen(end[0], end[1]) : null;
      const track = tracks.find(entry => entry.p.id === end);
      return track ? track.screen(values) : null;
    }

    function build(refreshBackdrop = true) {
      const data = host.data(), scene = data.scene, p = palette();
      const hp = hudPalette();
      const W = app.screen.width, H = app.screen.height;
      lastWidth = W; lastHeight = H;
      staticLayer.clear(); clearText(staticText); clearText(dynamicText);
      legend = []; hudTime = hudBadge = null;
      const frames = data.timeline.frames;
      const spatial = scene.participants.filter(item => item.dims > 0);
      mode = spatial.some(item => item.dims === 2) ? "plane"
        : spatial.length && spatial.every(item => item.vertical) ? "columns" : spatial.length ? "lanes" : "board";
      const legendWidth = show.hud ? Math.min(250, W * 0.34) : 0;
      // legend rows: two-line entries for few participants, one-line entries for many
      const compactLegend = scene.participants.length > 2;
      const legendRowH = compactLegend ? 19 : 36, legendRows = Math.min(scene.participants.length, compactLegend ? 6 : 3);
      const legendBottom = 12 + legendRows * legendRowH + (scene.participants.length > legendRows ? 16 : 0);
      /* instrument strip: meters for participants without spatial motion, when something else moves in space */
      const stateOnly = scene.participants.filter(needsMeter);
      /* values are listed under the stage; the stage only carries meter cards when it would otherwise be empty */
      const stripKeys: FieldMeta[] = [];
      const cells = instruments.filter(item => !item.on).length;
      const stripCount = mode === "board" ? 0 : cells + stripKeys.length;
      const stripH = stripCount ? Math.max(88, Math.min(170, H * 0.26)) : 0;
      const top = show.hud ? Math.max(68, legendBottom + 16) : 20, bottom = H - (show.axes ? 46 : 20) - (stripH ? stripH + 10 : 0);
      const left = show.axes ? 58 : 20, right = W - 24;
      const view: Rect = { x: left, y: top, w: Math.max(40, right - left), h: Math.max(40, bottom - top) };
      tracks = scene.participants.map(participant => ({ p: participant, color: color(participant.colorIndex), lane: 0,
        screen: () => null, path: [], strobe: [], vectorLabels: {}, monotonic: true, originX: 0 }));
      const spatialTracks = tracks.filter(track => track.p.dims > 0);
      /* Lines from the data: bodies on one axis that come into contact without passing through each other
         interact on the SAME line (a collision, a push, fragments separating); bodies that pass each other are on
         different lines. The generator may also name lines explicitly (art option "line"). */
      const along1D = (track: Track, values: Record<string, number>) => values[track.p.fields.position!];
      const relation = (a: Track, b: Track) => {
        let lo = Infinity, hi = -Infinity, pos = false, neg = false, closest = Infinity, step = 0, previous = NaN;
        for (const frame of frames) {
          const va = along1D(a, frame.values), vb = along1D(b, frame.values);
          lo = Math.min(lo, va, vb); hi = Math.max(hi, va, vb);
        }
        const tol = Math.max(1e-12, (hi - lo) * 1e-3);
        for (const frame of frames) {
          const d = along1D(b, frame.values) - along1D(a, frame.values);
          if (d > tol) pos = true; else if (d < -tol) neg = true;
          closest = Math.min(closest, Math.abs(d));
          if (Number.isFinite(previous)) step = Math.max(step, Math.abs(d - previous));
          previous = d;
        }
        const cross = pos && neg;
        return { cross, contact: !cross && closest <= Math.max(tol, step), side: pos ? 1 : neg ? -1 : 0 };
      };
      const oneAxis = spatialTracks.filter(track => track.p.dims === 1 && (mode === "lanes" || mode === "columns"));
      const parent = new Map<Track, Track>(oneAxis.map(track => [track, track]));
      const rootOf = (track: Track): Track => { let r = track; while (parent.get(r) !== r) r = parent.get(r)!; return r; };
      const join = (a: Track, b: Track) => parent.set(rootOf(a), rootOf(b));
      for (let i = 0; i < oneAxis.length; i++) for (let j = i + 1; j < oneAxis.length; j++) {
        const a = oneAxis[i], b = oneAxis[j], la = attached.get(a.p.id)?.options.line, lb = attached.get(b.p.id)?.options.line;
        if ((la && la === lb) || relation(a, b).contact) join(a, b);
      }
      const laneIndex = new Map<Track, number>();
      spatialTracks.forEach(track => {
        const key = parent.has(track) ? rootOf(track) : track;
        if (!laneIndex.has(key)) laneIndex.set(key, laneIndex.size);
        track.lane = laneIndex.get(key)!;
      });
      const laneCount = Math.max(1, laneIndex.size);

      // --- world bounds from the full solver timeline (not only initial inputs)
      const bounds: Bounds = { minX: Infinity, maxX: -Infinity, minY: Infinity, maxY: -Infinity };
      const grow = (x: number, y: number) => {
        if (Number.isFinite(x)) { bounds.minX = Math.min(bounds.minX, x); bounds.maxX = Math.max(bounds.maxX, x); }
        if (Number.isFinite(y)) { bounds.minY = Math.min(bounds.minY, y); bounds.maxY = Math.max(bounds.maxY, y); }
      };
      let groundAtZero = false;
      // Independent bodies on fixed-length links each need their own support:
      // lay the pivots side by side (world x offset) instead of stacking them.
      const linked = spatialTracks.filter(track => track.p.link && track.p.dims === 2);
      if (mode === "plane" && linked.length > 1) {
        let swing = 0, radius = 0;
        for (const track of linked) {
          radius = Math.max(radius, track.p.link!.radius);
          for (const frame of frames) swing = Math.max(swing, Math.abs(frame.values[track.p.fields.x!]));
        }
        const gap = Math.max(2 * swing + 0.25 * radius, 0.45 * radius);
        linked.forEach((track, index) => { track.originX = (index - (linked.length - 1) / 2) * gap; });
      }
      const pivotOffsets = linked.length > 1;
      if (mode === "plane") {
        for (const frame of frames) for (const track of spatialTracks) {
          const f = track.p.fields;
          if (track.p.dims === 2) grow(frame.values[f.x!] + track.originX, frame.values[f.y!]);
          else if (track.p.vertical) grow(0, frame.values[f.position!]);
          else grow(frame.values[f.position!], 0);
        }
        for (const track of spatialTracks) if (track.p.link) grow(track.originX, 0);
        const spanY = bounds.maxY - bounds.minY;
        groundAtZero = !spatialTracks.some(track => track.p.link) && bounds.minY >= -1e-6 * Math.max(1, spanY);
        if (groundAtZero) grow(NaN, 0);
      } else if (mode === "lanes" || mode === "columns") {
        for (const frame of frames) for (const track of spatialTracks) {
          const v = frame.values[track.p.fields.position!];
          if (mode === "lanes") grow(v, NaN); else grow(NaN, v);
        }
        if (mode === "lanes") grow(0, NaN); else grow(NaN, 0);
        if (mode === "columns") groundAtZero = bounds.minY >= -1e-9;
      }
      if (mode === "lanes") {
        const span = Math.max(bounds.maxX - bounds.minX, 1e-6);
        for (const track of spatialTracks) if (track.p.spring) {
          const f = scene.fields[track.p.fields.position!];
          const amplitude = Math.max(Math.abs(f.min), Math.abs(f.max), span * 0.25);
          track.wallX = Math.min(f.min, 0) - amplitude * 1.6;
          grow(track.wallX, NaN);
        }
      }
      /* fixtures are part of the measured scene: the frame must include where they stand */
      for (const f of fixtures) {
        const w = fixtureWorld(f);
        if (!w) continue;
        const shift = f.frame ? tracks.find(entry => entry.p.id === f.frame)?.originX ?? 0 : 0;
        if (mode === "plane") grow(w.x + shift, w.y); else if (mode === "lanes") grow(w.x, NaN); else if (mode === "columns") grow(NaN, w.y);
      }
      // padding + minimum spans so nothing touches the frame
      const padBounds = (minSpan: number, independentAxes = false) => {
        let spanX = bounds.maxX - bounds.minX, spanY = bounds.maxY - bounds.minY;
        if (!Number.isFinite(spanX)) { bounds.minX = -1; bounds.maxX = 1; spanX = 2; }
        if (!Number.isFinite(spanY)) { bounds.minY = -1; bounds.maxY = 1; spanY = 2; }
        const reference = independentAxes ? 0 : Math.max(spanX, spanY, minSpan);
        if (spanX < reference * 0.25) { const c = (bounds.minX + bounds.maxX) / 2; bounds.minX = c - reference * 0.125; bounds.maxX = c + reference * 0.125; spanX = reference * 0.25; }
        if (spanY < reference * 0.25) { const c = (bounds.minY + bounds.maxY) / 2; bounds.minY = c - reference * 0.125; bounds.maxY = c + reference * 0.125; spanY = reference * 0.25; }
        bounds.minX -= spanX * 0.08; bounds.maxX += spanX * 0.08;
        bounds.minY -= groundAtZero ? spanY * 0.04 : spanY * 0.1; bounds.maxY += spanY * 0.12;
      };
      exaggerated = false;
      if (mode === "plane") {
        /* Equal metre scale unless a varying axis spans < 1/6 of the other (small transverse
           displacements); bodies on fixed-length links always keep true proportions. */
        const spanX = bounds.maxX - bounds.minX, spanY = bounds.maxY - bounds.minY;
        const lopsided = Math.min(spanX, spanY) > 0 && Math.max(spanX, spanY) > 6 * Math.min(spanX, spanY);
        exaggerated = lopsided && !groundAtZero && !spatialTracks.some(track => track.p.link) && spatialTracks.every(track => track.p.dims === 2);
        padBounds(1e-6, exaggerated);
        cam = camera(view, bounds, !exaggerated);
      } else if (mode === "lanes") {
        padBounds(1e-6);
        laneHeight = Math.min(104, view.h / laneCount);
        const blockTop = view.y + (view.h - laneHeight * laneCount) / 2 + 10;
        laneY = lane => blockTop + laneHeight * (lane + 0.5);
        cam = camera(view, { ...bounds, minY: 0, maxY: 1 }, false);
      } else if (mode === "columns") {
        padBounds(1e-6);
        const columnWidth = Math.min(170, (view.w - legendWidth * 0.3) / laneCount);
        const blockLeft = view.x + (view.w - columnWidth * laneCount) / 2;
        columnX = lane => blockLeft + columnWidth * (lane + 0.5);
        cam = camera(view, { ...bounds, minX: 0, maxX: 1 }, false);
      } else cam = null;
      const c = cam;
      groundY = c && groundAtZero && mode !== "lanes" ? c.toScreen(0, 0).y : null;
      /* decorative grid, ground fill and plain roads give way to generated environment art */
      const art = show.grid === false || (illustrated() && show.grid !== true);

      for (const track of tracks) {
        const f = track.p.fields;
        if (!c || track.p.dims === 0) continue;
        if (mode === "plane") track.screen = values => {
          const x = track.p.dims === 2 ? values[f.x!] + track.originX : track.p.vertical ? 0 : values[f.position!];
          const y = track.p.dims === 2 ? values[f.y!] : track.p.vertical ? values[f.position!] : 0;
          return Number.isFinite(x) && Number.isFinite(y) ? c.toScreen(x, y) : null;
        };
        else if (mode === "lanes") track.screen = values => Number.isFinite(values[f.position!])
          ? { x: c.toScreen(values[f.position!], 0).x, y: laneY(track.lane) } : null;
        else track.screen = values => Number.isFinite(values[f.position!])
          ? { x: columnX(track.lane), y: c.toScreen(0, values[f.position!]).y } : null;
        const every = Math.max(1, Math.floor(frames.length / 600));
        track.path = [];
        for (let i = 0; i < frames.length; i += every) {
          const point = track.screen(frames[i].values);
          if (point) track.path.push({ ...point, t: frames[i].t });
        }
        const last = frames[frames.length - 1];
        const endPoint = last && track.screen(last.values);
        if (endPoint) track.path.push({ ...endPoint, t: last.t });
        if (track.p.dims === 1) {
          let direction = 0;
          track.monotonic = true;
          for (let i = 1; i < frames.length; i++) {
            const delta = frames[i].values[f.position!] - frames[i - 1].values[f.position!];
            const sign = Math.abs(delta) < 1e-12 ? 0 : Math.sign(delta);
            if (sign && direction && sign !== direction) { track.monotonic = false; break; }
            if (sign) direction = sign;
          }
        }
        const strobeStep = niceStep(data.timeline.durationSeconds, 10);
        track.strobe = [];
        for (let t = 0; t <= data.timeline.durationSeconds + 1e-9; t += strobeStep) {
          const point = track.screen(host.sample(t));
          if (point) track.strobe.push({ ...point, t });
        }
      }

      // --- vector scales shared per kind so magnitudes are comparable
      vectorScale = { velocity: 0, acceleration: 0, force: 0 };
      const maxMagnitude: Record<Kind, number> = { velocity: 0, acceleration: 0, force: 0 };
      for (const frame of frames) for (const track of spatialTracks)
        for (const vector of vectorsOf(track, frame.values)) maxMagnitude[vector.kind] = Math.max(maxMagnitude[vector.kind], vector.magnitude);
      const maxLength = Math.max(48, Math.min(150, mode === "plane" ? Math.min(view.w, view.h) * 0.32 : (mode === "lanes" ? view.w : view.h) * 0.22));
      for (const kind of Object.keys(maxMagnitude) as Kind[]) if (maxMagnitude[kind] > 1e-12) vectorScale[kind] = maxLength / maxMagnitude[kind];

      // --- background: grid, axes, ground
      if (c && show.axes) {
        const vis = c.visible;
        if (mode === "plane") {
          const step = exaggerated ? niceStep(vis.maxX - vis.minX, Math.max(3, Math.round(view.w / 90)))
            : niceStep(Math.max(vis.maxX - vis.minX, vis.maxY - vis.minY) / (Math.max(view.w, view.h) / 90), 1);
          const stepY = exaggerated ? niceStep(vis.maxY - vis.minY, Math.max(3, Math.round(view.h / 60))) : step;
          for (let x = Math.ceil(vis.minX / step) * step; x <= vis.maxX; x += step) {
            const sx = c.toScreen(x, 0).x;
            staticLayer.moveTo(sx, view.y).lineTo(sx, view.y + view.h);
          }
          for (let y = Math.ceil(vis.minY / stepY) * stepY; y <= vis.maxY; y += stepY) {
            const sy = c.toScreen(0, y).y;
            staticLayer.moveTo(view.x, sy).lineTo(view.x + view.w, sy);
          }
          if (!art) staticLayer.stroke({ color: p.grid, width: 1 }); else staticLayer.clear();
          const axisY = Math.min(Math.max(0, vis.minY), vis.maxY), axisX = Math.min(Math.max(0, vis.minX), vis.maxX);
          const origin = c.toScreen(axisX, axisY);
          arrow(staticLayer, view.x, origin.y, view.x + view.w + 10, origin.y, { color: p.axis, width: 1.4, head: 8 });
          arrow(staticLayer, origin.x, view.y + view.h, origin.x, view.y - 10, { color: p.axis, width: 1.4, head: 8 });
          for (let x = Math.ceil(vis.minX / step) * step; x <= vis.maxX - step * 0.3; x += step) {
            const sx = c.toScreen(x, 0).x;
            staticLayer.moveTo(sx, origin.y - 4).lineTo(sx, origin.y + 4);
            /* with side-by-side supports each x is measured from its own pivot */
            if (!pivotOffsets && Math.abs(x) > step * 1e-6) staticText.addChild(Object.assign(text(tickLabel(x, step), { size: 10.5, color: p.muted, anchorX: 0.5 }), { x: sx, y: Math.min(origin.y + 6, view.y + view.h + 6) }));
          }
          for (let y = Math.ceil(vis.minY / stepY) * stepY; y <= vis.maxY - stepY * 0.3; y += stepY) {
            const sy = c.toScreen(0, y).y;
            staticLayer.moveTo(origin.x - 4, sy).lineTo(origin.x + 4, sy);
            if (Math.abs(y) > stepY * 1e-6) staticText.addChild(Object.assign(text(tickLabel(y, stepY), { size: 10.5, color: p.muted, anchorX: 1, anchorY: 0.5 }), { x: Math.max(origin.x - 7, view.x - 8), y: sy }));
          }
          staticLayer.stroke({ color: p.axis, width: 1.2 });
          staticText.addChild(Object.assign(text("O", { size: 11, color: p.muted, anchorX: 1, weight: "600" }), { x: origin.x - 5, y: origin.y + 4 }));
          staticText.addChild(Object.assign(text("x (m)", { size: 11.5, color: p.axis, anchorX: 1, anchorY: 1, weight: "600" }), { x: view.x + view.w + 8, y: origin.y - 6 }));
          staticText.addChild(Object.assign(text(exaggerated ? "y (m) · khác tỉ lệ trục x" : "y (m)", { size: 11.5, color: p.axis, anchorX: 0, anchorY: 0.5, weight: "600" }), { x: origin.x + 10, y: view.y - 8 }));
          if (groundAtZero && !art) {
            const g0 = c.toScreen(0, 0).y;
            staticLayer.rect(view.x, g0, view.w, Math.max(0, view.y + view.h - g0)).fill({ color: p.groundFill, alpha: 0.55 });
            hatch(staticLayer, view.x, view.x + view.w, g0, 1, p.ground);
          }
          for (const track of spatialTracks) if (track.p.link) {
            const pivot = c.toScreen(track.originX, 0);
            if (show.supports && !decor.support) {
              staticLayer.rect(pivot.x - 34, pivot.y - 8, 68, 8).fill({ color: p.groundFill });
              hatch(staticLayer, pivot.x - 34, pivot.x + 34, pivot.y - 8, -1, p.ground);
            }
            if (show.angles) dashed(staticLayer, pivot.x, pivot.y, pivot.x, pivot.y + track.p.link.radius * c.sy * 1.08, 5, 5).stroke({ color: p.faint, width: 1.2 });
          }
        } else if (mode === "lanes") {
          const step = niceStep(vis.maxX - vis.minX, Math.max(3, Math.round(view.w / 90)));
          const rulerY = view.y + view.h;
          for (let x = Math.ceil(vis.minX / step) * step; x <= vis.maxX; x += step) {
            const sx = c.toScreen(x, 0).x;
            if (!art) staticLayer.moveTo(sx, view.y + 8).lineTo(sx, rulerY);
          }
          if (!art) staticLayer.stroke({ color: p.grid, width: 1 });
          for (let x = Math.ceil(vis.minX / (step / 5)) * (step / 5); x <= vis.maxX; x += step / 5) {
            const sx = c.toScreen(x, 0).x;
            staticLayer.moveTo(sx, rulerY).lineTo(sx, rulerY + 4);
          }
          staticLayer.stroke({ color: p.faint, width: 1 });
          arrow(staticLayer, view.x - 8, rulerY, view.x + view.w + 12, rulerY, { color: p.axis, width: 1.4, head: 8 });
          for (let x = Math.ceil(vis.minX / step) * step; x <= vis.maxX - step * 0.2; x += step) {
            const sx = c.toScreen(x, 0).x;
            staticLayer.moveTo(sx, rulerY - 5).lineTo(sx, rulerY + 7);
            staticText.addChild(Object.assign(text(Math.abs(x) < step * 1e-6 ? "O" : tickLabel(x, step), { size: 10.5, color: p.muted, anchorX: 0.5, weight: Math.abs(x) < step * 1e-6 ? "700" : "500" }), { x: sx, y: rulerY + 9 }));
          }
          staticLayer.stroke({ color: p.axis, width: 1.2 });
          staticText.addChild(Object.assign(text("x (m)", { size: 11.5, color: p.axis, anchorX: 1, anchorY: 1, weight: "600" }), { x: view.x + view.w + 10, y: rulerY - 6 }));
          for (const track of spatialTracks) {
            const y = laneY(track.lane), roadY = y + 15;
            if (!art) staticLayer.roundRect(view.x - 4, roadY, view.w + 8, 5, 2.5).fill({ color: p.groundFill, alpha: 0.9 });
            if (track.wallX !== undefined) {
              const wx = c.toScreen(track.wallX, 0).x;
              if (show.supports && !decor.support) {
                staticLayer.rect(wx - 10, y - 24, 10, 42).fill({ color: p.groundFill });
                staticLayer.moveTo(wx, y - 24).lineTo(wx, y + 18).stroke({ color: p.ground, width: 2 });
                for (let hy = y - 20; hy < y + 18; hy += 8) staticLayer.moveTo(wx, hy).lineTo(wx - 7, hy + 7);
                staticLayer.stroke({ color: p.ground, width: 1, alpha: 0.7 });
              }
              const eq = c.toScreen(0, 0).x;
              dashed(staticLayer, eq, y - 26, eq, y + 20, 4, 4).stroke({ color: p.faint, width: 1.2 });
              staticText.addChild(Object.assign(text("VTCB", { size: 10, color: p.muted, anchorX: 0.5, anchorY: 1 }), { x: eq, y: y - 27 }));
            }
          }
        } else if (mode === "columns") {
          const step = niceStep(vis.maxY - vis.minY, Math.max(3, Math.round(view.h / 70)));
          for (let y = Math.ceil(vis.minY / step) * step; y <= vis.maxY; y += step) {
            const sy = c.toScreen(0, y).y;
            if (!art) staticLayer.moveTo(view.x, sy).lineTo(view.x + view.w, sy);
          }
          if (!art) staticLayer.stroke({ color: p.grid, width: 1 });
          arrow(staticLayer, view.x, view.y + view.h + 8, view.x, view.y - 12, { color: p.axis, width: 1.4, head: 8 });
          for (let y = Math.ceil(vis.minY / step) * step; y <= vis.maxY - step * 0.2; y += step) {
            const sy = c.toScreen(0, y).y;
            staticLayer.moveTo(view.x - 5, sy).lineTo(view.x + 5, sy);
            staticText.addChild(Object.assign(text(tickLabel(y, step), { size: 10.5, color: p.muted, anchorX: 1, anchorY: 0.5 }), { x: view.x - 8, y: sy }));
          }
          staticLayer.stroke({ color: p.axis, width: 1.2 });
          const axisName = spatialTracks.some(track => scene.fields[track.p.fields.position!]?.quantity === "height") ? "h (m)" : "y (m)";
          staticText.addChild(Object.assign(text(axisName, { size: 11.5, color: p.axis, anchorX: 0, anchorY: 0.5, weight: "600" }), { x: view.x + 10, y: view.y - 8 }));
          if (groundAtZero && !art) {
            const g0 = c.toScreen(0, 0).y;
            staticLayer.rect(view.x, g0, view.w, Math.max(0, H - g0)).fill({ color: p.groundFill, alpha: 0.55 });
            hatch(staticLayer, view.x, view.x + view.w, g0, 1, p.ground);
          }
          if (!art) for (const track of spatialTracks) {
            const x = columnX(track.lane);
            dashed(staticLayer, x, view.y, x, view.y + view.h, 3, 6).stroke({ color: p.gridMajor, width: 1 });
          }
        }
      }
      // --- points of a continuous medium: >= 3 bodies that each stay at their own x
      medium = [];
      if (mode === "plane") {
        const fixedX = spatialTracks.filter(track => track.p.dims === 2 && !track.p.link && (() => {
          const meta = scene.fields[track.p.fields.x!];
          return meta && Math.abs(meta.max - meta.min) <= 1e-9 * Math.max(1, Math.abs(meta.max));
        })());
        if (fixedX.length >= 3) medium = fixedX.sort((a, b) => scene.fields[a.p.fields.x!].min - scene.fields[b.p.fields.x!].min);
      }
      const crowded = spatialTracks.length > 6;
      // --- full trajectories (faint), labels and vector labels
      for (const track of tracks) {
        if (track.p.dims === 0) continue;
        track.quiet = medium.includes(track);
        if (show.trails && mode === "plane" && track.path.length > 1 && !track.quiet) {
          for (let i = 1; i < track.path.length; i += 2) {
            staticLayer.moveTo(track.path[i - 1].x, track.path[i - 1].y).lineTo(track.path[i].x, track.path[i].y);
          }
          staticLayer.stroke({ color: track.color, width: 1.5, alpha: 0.35 });
        }
        if (show.labels && !crowded) {
          track.label = text(track.p.label, { size: 12, weight: "600", color: p.ink, anchorX: 0.5, anchorY: 1 });
          dynamicText.addChild(track.label);
        }
        if (show.vectors && !crowded) for (const kind of ["velocity", "acceleration", "force"] as Kind[]) if (vectorScale[kind] > 0) {
          const label = text("", { size: 11, weight: "600", color: p[kind], anchorX: 0.5, anchorY: 1 });
          track.vectorLabels[kind] = label; dynamicText.addChild(label);
        }
        if (track.p.link && track.p.fields.angle && show.angles) {
          track.angleLabel = text("", { size: 11, weight: "600", color: p.muted, anchorX: 0.5, anchorY: 0 });
          dynamicText.addChild(track.angleLabel);
        }
      }
      // --- instrument panel when nothing moves in space (circuits, heat, decay …)
      for (const child of gaugeLayer.removeChildren()) child.destroy({ children: true });
      gauges = [];
      meterCovered = new Set(instruments.filter(item => item.drive.property !== "none").map(item => item.participantId));
      const bound = new Set(instruments.filter(item => item.drive.property !== "none").map(item => item.key));
      // Always shown in board mode: without it a non-spatial phenomenon would render an empty stage.
      let cardKeys: FieldMeta[] = [], cardRect: Rect = view;
      if (mode === "board") {
        /* every participant's main quantity first, then the rest, so no participant is left without a meter */
        const shown = Object.values(scene.fields).filter(meta => !bound.has(meta.key) && (meta.kind !== "angle" || meta.unit !== "rad"));
        const primary = meterKeys(scene.participants.filter(item => !meterCovered.has(item.id)), Infinity);
        /* with drawings on stage the drawings get the room: one card per participant they do not show (every value
           is also listed under the stage); without drawings the cards are the stage */
        cardKeys = instruments.length ? [] : [...primary, ...shown.filter(meta => !primary.includes(meta))].slice(0, Math.max(primary.length, 12));
        if (instruments.length) {
          const cardRows = Math.ceil(cardKeys.length / 4), cardsH = cardKeys.length ? Math.min(view.h * 0.3, cardRows * 62 + (cardRows - 1) * 14 + 10) : 0;
          placeInstruments({ x: view.x, y: view.y, w: view.w, h: view.h - cardsH }, p);
          cardRect = { x: view.x, y: view.y + view.h - cardsH, w: view.w, h: cardsH };
        }
      } else {
        const strip: Rect = { x: 12, y: H - stripH - 8, w: W - 24, h: stripH };
        if (stripCount) {
          const cellW = strip.w / stripCount;
          placeInstruments({ x: strip.x, y: strip.y, w: cellW * cells, h: strip.h }, p);
          cardKeys = stripKeys;
          cardRect = { x: strip.x + cellW * cells, y: strip.y, w: cellW * stripKeys.length, h: strip.h };
        } else placeInstruments(null, p);
      }
      for (const meta of cardKeys) meterCovered.add(meta.participantId);
      if (cardKeys.length) {
        const keys = cardKeys, box = cardRect;
        const columns = mode !== "board" ? keys.length : instruments.length ? Math.min(4, keys.length) : keys.length > 6 ? 3 : keys.length > 2 ? 2 : 1;
        const gap = 14, cardW = Math.min(360, (box.w - gap * (columns - 1)) / columns), cardH = 62;
        const rows = Math.ceil(keys.length / columns), blockH = rows * cardH + (rows - 1) * gap;
        const left = box.x + (box.w - (cardW * columns + gap * (columns - 1))) / 2;
        const top = Math.max(box.y, box.y + (box.h - blockH) / 2);
        keys.forEach((meta, index) => {
          const x = left + (index % columns) * (cardW + gap), y = top + Math.floor(index / columns) * (cardH + gap);
          const track = tracks.find(item => item.p.id === meta.participantId);
          const color = track ? track.color : p.axis;
          const card = new PIXI.Graphics().roundRect(x, y, cardW, cardH, 10).fill({ color: p.panel, alpha: 0.9 }).stroke({ color: p.grid, width: 1 });
          const who = scene.participants.length > 1 && track ? track.p.label + " · " : "";
          const name = fit(text(who + named(meta), { size: 12, weight: "600", color: p.muted, halo: false }), cardW - 24);
          name.position.set(x + 12, y + 7);
          const value = text("", { size: 17, weight: "700", color: p.ink, halo: false, mono: true, anchorX: 1 });
          value.position.set(x + cardW - 12, y + 22);
          const bar = new PIXI.Graphics();
          gaugeLayer.addChild(card, bar, name, value);
          gauges.push({ key: meta.key, x: x + 12, y: y + cardH - 18, w: cardW - 24, bar, value, min: Math.min(0, meta.min), max: Math.max(0, meta.max), color });
        });
      }
      // --- HUD: time, legend with live readouts, verification badge
      hudPanel.clear();
      if (show.hud && art) {
        const legendW = Math.min(compactLegend ? 380 : 250, W * (compactLegend ? 0.6 : 0.36));
        hudPanel.roundRect(8, 8, 262, 46, 10).fill({ color: hp.panel, alpha: 0.86 });
        if (tracks.length) hudPanel.roundRect(W - legendW - 8, 6, legendW, legendBottom - 2, 10).fill({ color: hp.panel, alpha: 0.86 });
      }
      if (show.hud) {
        hudTime = text("t = 0.00 s", { size: 15, weight: "700", mono: true, color: hp.ink, hud: true });
        hudTime.position.set(16, 14);
        dynamicText.addChild(hudTime);
        let y = 12;
        for (const track of tracks.slice(0, legendRows)) {
          const dot = new PIXI.Graphics().circle(0, 0, 5).fill({ color: track.color });
          const name = text(track.p.label, { size: 12, weight: "600", anchorX: 1, hud: true });
          const values = text("", { size: 11, color: hp.muted, anchorX: 1, mono: true, hud: true });
          dot.position.set(W - 16, y + 8);
          name.position.set(W - 27, y);
          values.position.set(W - 16, compactLegend ? y + 1 : y + 16);
          dynamicText.addChild(dot, name, values);
          legend.push({ dot, name, values, track });
          y += legendRowH;
        }
        if (tracks.length > legendRows)
          dynamicText.addChild(Object.assign(text("+" + (tracks.length - legendRows) + " … (xem Bảng số liệu)", { size: 10.5, color: hp.muted, anchorX: 1, hud: true }), { x: W - 16, y }));
        compact = compactLegend;
        const status = data.verificationStatus;
        const verified = status === "VERIFIED_ANALYTICAL" || status === "VERIFIED_NUMERICAL";
        hudBadge = text(verified ? "✓ Dữ liệu vật lý đã được backend xác minh" : status === "PENDING" ? "Đang tính lại…" : "⚠ Minh họa — chưa xác minh vật lý",
          { size: 10.5, color: verified ? hp.velocity : hp.acceleration, anchorX: 1, anchorY: 1, weight: "600", hud: true });
        hudBadge.anchor.set(0, 0);
        hudBadge.position.set(16, 36);
        dynamicText.addChild(hudBadge);
      }
      placeDecor();
      placeFixtures(); applyFixtures();
      findContacts();
      if (refreshBackdrop) scheduleBackdrop();
    }

    /** Stretch a connector texture (drawn along +x, its height = thickness in px) between two points. */
    function stretch(sprite: PixiNS.Sprite, from: Pt, to: Pt) {
      const length = Math.hypot(to.x - from.x, to.y - from.y);
      sprite.visible = length > 1;
      sprite.position.set(from.x, from.y);
      sprite.rotation = Math.atan2(to.y - from.y, to.x - from.x);
      sprite.width = Math.max(1, length);
      sprite.height = Math.max(1.5, Math.min(30, sprite.texture.height));
    }

    /**
     * Place generated role artwork on the inferred physical geometry:
     *  surface  – tiled along every lane surface and along the ground line;
     *  support  – at every fixed point a connector hangs from (pivots, walls), its
     *             attachment edge (bottom of the art) turned towards the body;
     *  connector– one stretched sprite per linked / spring-bound participant.
     */
    function placeDecor() {
      for (const child of decorLayer.removeChildren()) child.destroy();
      const c = cam, W = app.screen.width;
      if (!c) return;
      if (decor.surface) {
        const tex = decor.surface;
        const strip = (y: number, height: number) => {
          const tile = new PIXI.TilingSprite({ texture: tex, width: W, height });
          const k = height / Math.max(1, tex.height);
          tile.tileScale.set(k, k);
          tile.position.set(0, y);
          decorLayer.addChild(tile);
        };
        if (mode === "lanes") for (const track of tracks) if (track.p.dims > 0 && track.wallX === undefined)
          strip(laneY(track.lane) + 15, Math.max(10, Math.min(30, laneHeight * 0.3)));
        if (groundY !== null) strip(groundY, Math.max(14, Math.min(40, (app.screen.height - groundY))));
        /* a body moving along a straight slanted line rests on a slanted surface */
        if (mode === "plane") for (const track of tracks) {
          if (track.p.link || track.path.length < 3) continue;
          const a = track.path[0], b = track.path[track.path.length - 1];
          const length = Math.hypot(b.x - a.x, b.y - a.y), angle = Math.atan2(b.y - a.y, b.x - a.x);
          const slope = Math.abs(Math.atan2(Math.abs(b.y - a.y), Math.abs(b.x - a.x)));
          if (length < 20 || slope < 0.08 || slope > 1.49) continue;
          const straight = track.path.every(q => Math.abs((q.x - a.x) * (b.y - a.y) - (q.y - a.y) * (b.x - a.x)) / length < 1.5);
          if (!straight) continue;
          const height = Math.max(10, Math.min(26, length * 0.06));
          const tile = new PIXI.TilingSprite({ texture: tex, width: length + 40, height });
          const k = height / Math.max(1, tex.height);
          tile.tileScale.set(k, k);
          tile.position.set(a.x - Math.cos(angle) * 20, a.y - Math.sin(angle) * 20);
          tile.rotation = angle;
          decorLayer.addChild(tile);
        }
      }
      if (decor.support) {
        const place = (at: Pt, toward: Pt, span: number) => {
          const sprite = new PIXI.Sprite(decor.support!);
          sprite.anchor.set(0.5, 1);
          const k = span / Math.max(1, sprite.texture.width);
          sprite.scale.set(k, k);
          sprite.position.set(at.x, at.y);
          sprite.rotation = Math.atan2(toward.y - at.y, toward.x - at.x) - Math.PI / 2;
          decorLayer.addChild(sprite);
        };
        for (const track of tracks) {
          if (track.p.link) {
            const pivot = c.toScreen(track.originX, 0), rest = track.path.length
              ? track.path.reduce((sum, q) => ({ x: sum.x + q.x / track.path.length, y: sum.y + q.y / track.path.length }), { x: 0, y: 0 })
              : { x: pivot.x, y: pivot.y + 1 };
            place(pivot, Math.hypot(rest.x - pivot.x, rest.y - pivot.y) > 2 ? rest : { x: pivot.x, y: pivot.y + 1 },
              Math.max(40, Math.min(130, track.p.link.radius * c.sy * 0.35)));
          }
          if (track.wallX !== undefined) {
            const at = { x: c.toScreen(track.wallX, 0).x, y: laneY(track.lane) };
            place(at, { x: at.x + 1, y: at.y }, Math.max(40, Math.min(110, laneHeight * 0.9)));
          }
        }
      }
      if (decor.connector) for (const track of tracks) if ((track.p.link || track.wallX !== undefined) && !connectors.has(track.p.id)) {
        const sprite = new PIXI.Sprite(decor.connector);
        sprite.anchor.set(0, 0.5);
        connectorLayer.addChild(sprite);
        connectors.set(track.p.id, sprite);
      }
    }
    function setDecor(textures: { surface?: PixiNS.Texture; support?: PixiNS.Texture; connector?: PixiNS.Texture }) {
      Object.assign(decor, textures);
      build();
    }

    /**
     * Physics-aligned layout for environment artwork, in screen pixels: where the
     * ground is, where each lane's road surface is, pivots and walls, the start/end
     * and sampled path of every participant, and a world→screen mapping.
     */
    function layout(): SceneLayout {
      const data = host.data(), W = app.screen.width, H = app.screen.height, c = cam;
      const every = (n: number) => Math.max(1, Math.ceil(n / 48));
      return {
        width: W, height: H, theme: data.theme, palette: palette(), mode,
        view: c ? { ...c.view } : { x: 0, y: 0, w: W, h: H },
        /** Screen y of the physical ground (y = 0) when motion rests on/above it; null otherwise. */
        ground: groundY,
        lanes: mode === "lanes" ? tracks.filter(track => track.p.dims > 0).map(track => ({
          id: track.p.id, label: track.p.label, y: laneY(track.lane) + 15,
          top: laneY(track.lane) + 15 - laneHeight * 0.8, bottom: laneY(track.lane) + 15 + laneHeight * 0.2 })) : [],
        columns: mode === "columns" ? tracks.filter(track => track.p.dims > 0).map(track => ({
          id: track.p.id, label: track.p.label, x: columnX(track.lane) })) : [],
        pivots: c ? tracks.filter(track => track.p.link).map(track => ({ id: track.p.id, ...c.toScreen(track.originX, 0),
          length: track.p.link!.radius * c.sy })) : [],
        walls: c ? tracks.filter(track => track.wallX !== undefined).map(track => ({ id: track.p.id,
          x: c.toScreen(track.wallX!, 0).x, y: laneY(track.lane) + 15 })) : [],
        participants: tracks.map(track => ({ id: track.p.id, label: track.p.label, dims: track.p.dims,
          start: track.path[0] ? { x: track.path[0].x, y: track.path[0].y } : null,
          end: track.path.length ? { x: track.path[track.path.length - 1].x, y: track.path[track.path.length - 1].y } : null,
          path: track.path.filter((_, i) => i % every(track.path.length) === 0).map(q => ({ x: q.x, y: q.y })) })),
        /** Pixels per metre (x, y) of the measured frame. */
        scale: c ? { x: c.sx, y: c.sy } : { x: 1, y: 1 },
        toScreen: (x: number, y: number) => c ? c.toScreen(x, y) : { x, y },
      };
    }

    /** Screen y of the main physical reference line (supports, ground or the lowest track). */
    function referenceLine(): number | null {
      const c = cam;
      if (!c) return null;
      const pivots = tracks.filter(track => track.p.link).map(track => c.toScreen(track.originX, 0).y);
      if (pivots.length) return Math.min(...pivots);
      if (groundY !== null) return groundY;
      if (mode === "lanes") {
        const lanes = tracks.filter(track => track.p.dims > 0);
        if (lanes.length) return Math.max(...lanes.map(track => laneY(track.lane) + 15));
      }
      return null;
    }
    function viewBoxOf(svg: string) {
      const box = /viewBox\s*=\s*["']\s*([-\d.eE]+)[\s,]+([-\d.eE]+)[\s,]+([-\d.eE]+)[\s,]+([-\d.eE]+)/.exec(svg);
      const w = box ? Number(box[3]) : NaN, h = box ? Number(box[4]) : NaN;
      return w > 0 && h > 0 ? { w, h, y0: Number(box![2]) || 0 } : null;
    }
    function scheduleBackdrop() {
      if ((!backdropSource && !environmentSpec) || !host.svg) return;
      if (backdropTimer) clearTimeout(backdropTimer);
      const version = ++backdropVersion;
      backdropTimer = setTimeout(async () => {
        backdropTimer = null;
        try {
          const W = app.screen.width, H = app.screen.height;
          let markup: string, box = { x: 0, y: 0, w: W, h: H };
          if (environmentSpec) {
            markup = environmentSpec.svg;
            const vb = viewBoxOf(markup), target = referenceLine();
            if (vb) {
              /* cover the stage; when possible put viewBox line anchorY on the reference line */
              let k = Math.max(W / vb.w, H / vb.h), top = (H - vb.h * k) / 2;
              const a = environmentSpec.anchorY - vb.y0;
              if (target !== null && a > 0 && a < vb.h) {
                const need = Math.max(k, target / a, (H - target) / (vb.h - a));
                if (need * vb.h <= 4 * H) { k = need; top = target - a * k; }
              }
              box = { x: (W - vb.w * k) / 2, y: top, w: vb.w * k, h: vb.h * k };
            }
          } else {
            markup = await backdropSource!(layout());
          }
          if (typeof markup !== "string" || !markup.trim()) throw Error("backdrop() must return SVG markup.");
          const texture = await host.svg!(markup, { screen: true, width: box.w, height: box.h });
          if (disposed || version !== backdropVersion) { host.release?.(texture); return; }
          const previous = backdropSprite;
          backdropSprite = new PIXI.Sprite(texture);
          backdropSprite.position.set(box.x, box.y);
          backdropSprite.width = box.w; backdropSprite.height = box.h;
          backdropLayer.addChild(backdropSprite);
          if (previous) { previous.removeFromParent(); host.release?.(previous.texture); previous.destroy(); }
          const luma = host.luma?.(texture);
          const tone = luma === undefined ? null : luma < 0.5 ? "DARK" : "LIGHT";
          if (tone !== sceneTone) { sceneTone = tone; build(false); }
        } catch (error) {
          host.fail?.("backdrop(): " + String((error as Error)?.message || error));
        }
      }, backdropSprite ? 140 : 0);
    }
    function screenOf(id: string, values = host.sample(0)) {
      const track = tracks.find(item => item.p.id === id);
      return track ? track.screen(values) : null;
    }

    function update(frame: { t: number; fields: Record<string, number> }) {
      updatedThisFrame = true;
      if (app.screen.width !== lastWidth || app.screen.height !== lastHeight) build();
      const p = palette(), values = frame.fields;
      dynamicLayer.clear(); bodyLayer.clear();
      if (medium.length >= 3) {
        /* smooth curve through the medium's points (the shape of the rope / string / surface) */
        const pts = medium.map(track => track.screen(values)).filter((q): q is Pt => !!q);
        if (pts.length >= 3) {
          dynamicLayer.moveTo(pts[0].x, pts[0].y);
          for (let i = 1; i < pts.length - 1; i++) {
            const mx = (pts[i].x + pts[i + 1].x) / 2, my = (pts[i].y + pts[i + 1].y) / 2;
            dynamicLayer.quadraticCurveTo(pts[i].x, pts[i].y, mx, my);
          }
          dynamicLayer.lineTo(pts[pts.length - 1].x, pts[pts.length - 1].y).stroke({ color: p.axis, width: 2.5, alpha: 0.75, cap: "round", join: "round" });
        }
      }
      for (const track of tracks) {
        if (track.p.dims === 0 || !cam) continue;
        const point = track.screen(values);
        if (!point) continue;
        // travelled path + strobe marks (textbook multiple-exposure picture)
        if (show.trails && track.path.length > 1 && (mode === "plane" || track.monotonic) && !track.quiet) {
          let started = false;
          for (const q of track.path) {
            if (q.t > frame.t) break;
            if (!started) { dynamicLayer.moveTo(q.x, q.y); started = true; } else dynamicLayer.lineTo(q.x, q.y);
          }
          if (started) dynamicLayer.lineTo(point.x, point.y).stroke({ color: track.color, width: mode === "plane" ? 2.6 : 3, alpha: 0.85, cap: "round", join: "round" });
        }
        if (show.strobe && !track.quiet) for (const q of track.strobe) {
          if (q.t > frame.t + 1e-9) break;
          dynamicLayer.circle(q.x, q.y, 3.6).fill({ color: track.color, alpha: 0.35 }).stroke({ color: track.color, width: 1.2, alpha: 0.9 });
        }
        const connector = connectors.get(track.p.id);
        if (track.p.link) {
          const pivot = cam.toScreen(track.originX, 0);
          if (connector) stretch(connector, pivot, point);
          else if (show.links) {
            dynamicLayer.moveTo(pivot.x, pivot.y).lineTo(point.x, point.y).stroke({ color: p.axis, width: 2 });
            dynamicLayer.circle(pivot.x, pivot.y, 4).fill({ color: p.axis });
          }
          const theta = values[track.p.fields.angle!];
          if (Number.isFinite(theta) && track.angleLabel) {
            const r = Math.max(26, Math.min(60, Math.hypot(point.x - pivot.x, point.y - pivot.y) * 0.3));
            const down = Math.PI / 2, rod = Math.atan2(point.y - pivot.y, point.x - pivot.x);
            if (Math.abs(rod - down) > 0.01) {
              const a0 = Math.min(down, rod), a1 = Math.max(down, rod);
              dynamicLayer.moveTo(pivot.x + Math.cos(a0) * r, pivot.y + Math.sin(a0) * r)
                .arc(pivot.x, pivot.y, r, a0, a1).stroke({ color: p.muted, width: 1.5 });
            }
            track.angleLabel.text = "θ = " + format(theta * 180 / Math.PI, "°", 3).replace(" °", "°");
            const mid = (down + rod) / 2;
            track.angleLabel.position.set(pivot.x + Math.cos(mid) * (r + 14), pivot.y + Math.sin(mid) * (r + 4));
          }
        }
        if (track.wallX !== undefined && cam) {
          const wx = cam.toScreen(track.wallX, 0).x;
          if (connector) stretch(connector, { x: wx, y: point.y }, point);
          else if (show.links) spring(dynamicLayer, wx, point.x - 12, point.y, 7, p.spring);
        }
        // vectors (shared scale per kind so learners can compare magnitudes)
        const radius = mode === "plane" ? 10 : 12;
        let offset = 0;
        for (const vector of vectorsOf(track, values)) {
          const scale = vectorScale[vector.kind], label = track.vectorLabels[vector.kind];
          if (!show.vectors || !(scale > 0)) continue;
          let dx = vector.dx * scale, dy = -vector.dy * scale;
          const along = mode === "lanes" ? { x: 0, y: offset } : mode === "columns" ? { x: offset, y: 0 } : { x: 0, y: 0 };
          const x0 = point.x + along.x, y0 = point.y + along.y;
          {
            /* keep the arrow head on screen; the label still shows the true magnitude */
            const W = app.screen.width, H = app.screen.height;
            let k = 1;
            if (x0 + dx < 8) k = Math.min(k, (x0 - 8) / -dx); else if (x0 + dx > W - 8) k = Math.min(k, (W - 8 - x0) / dx);
            if (y0 + dy < 8) k = Math.min(k, (y0 - 8) / -dy); else if (y0 + dy > H - 8) k = Math.min(k, (H - 8 - y0) / dy);
            if (k < 1) { k = Math.max(0, k); dx *= k; dy *= k; }
          }
          const drawn = Math.hypot(dx, dy) > radius + 2 && arrow(dynamicLayer, x0, y0, x0 + dx, y0 + dy, { color: p[vector.kind], width: 2.6 });
          if (label) {
            label.visible = drawn;
            if (drawn) {
              const symbol = vector.kind === "velocity" ? "v" : vector.kind === "acceleration" ? "a" : "F";
              const unit = vector.kind === "velocity" ? "m/s" : vector.kind === "acceleration" ? "m/s²" : "N";
              label.text = symbol + " = " + format(vector.magnitude, unit);
              const nx = x0 + dx, ny = y0 + dy;
              if (mode === "lanes") {
                label.anchor.set(dx >= 0 ? 0 : 1, offset === 0 ? 1 : 0);
                label.position.set(nx + (dx >= 0 ? 6 : -6), offset === 0 ? ny - 3 : ny + 5);
              } else if (mode === "columns") {
                label.anchor.set(0, 0.5);
                label.position.set(nx + 8, ny);
              }
              else label.position.set(nx + (dx >= 0 ? 6 : -6), ny + (dy > 0 ? 16 : -4));
            }
          }
          if (label && label.visible) {
            const w = label.width, h = label.height, ax = label.anchor.x, ay = label.anchor.y;
            label.x = Math.max(4 + w * ax, Math.min(app.screen.width - 4 - w * (1 - ax), label.x));
            label.y = Math.max(4 + h * ay, Math.min(app.screen.height - 4 - h * (1 - ay), label.y));
          }
          offset += 14;
        }
        const art = attached.get(track.p.id);
        if (art) {
          const o = art.options, view = cam.view;
          const bottom = (o.anchor ?? (mode === "lanes" ? "bottom" : "center")) === "bottom";
          let target = o.size ?? (mode === "lanes" ? Math.max(40, Math.min(84, laneHeight * 0.72))
            : mode === "plane" ? Math.max(28, Math.min(64, Math.min(view.w, view.h) * 0.1)) : 52);
          if (o.sizeMeters && o.sizeMeters > 0) {
            const perMetre = mode === "columns" ? cam.sy : mode === "lanes" ? cam.sx : Math.min(cam.sx, cam.sy);
            /* true scale when it stays readable; otherwise the readable size (textbook exaggeration) */
            target = Math.max(target * 0.85, Math.min(target * 1.8, o.sizeMeters * perMetre));
          }
          const k = o.autoScale === false || !(art.baseW > 0 && art.baseH > 0) ? 1 : target / Math.max(art.baseW, art.baseH);
          /* direction of motion on screen (keeps the last heading while at rest) */
          const f = track.p.fields;
          let hx = NaN, hy = NaN;
          const read = (key?: string) => key !== undefined && key in values ? values[key] : NaN;
          if (track.p.dims === 2) { hx = read(f.vx); hy = -read(f.vy); }
          else if (track.p.dims === 1) { const v = read(f.velocity); if (track.p.vertical) { hx = 0; hy = -v; } else { hx = v; hy = 0; } }
          if (!Number.isFinite(hx) || !Number.isFinite(hy)) {
            const ahead = track.screen(host.sample(Math.min(host.data().timeline.durationSeconds, frame.t + 0.02)));
            hx = ahead ? ahead.x - point.x : 0; hy = ahead ? ahead.y - point.y : 0;
          }
          if (Math.abs(hx) > 1e-6) art.direction = Math.sign(hx);
          const rotate = o.rotate ?? (track.p.link ? "link" : "none");
          let rotation = 0, flip = 1;
          if (rotate === "velocity" && Math.hypot(hx, hy) > 1e-6) rotation = Math.atan2(hy, hx) + (o.facing === "left" ? Math.PI : 0);
          else if (rotate === "link" && track.p.link) {
            const pivot = cam.toScreen(track.originX, 0);
            rotation = -Math.atan2(point.x - pivot.x, point.y - pivot.y);
          }
          if (rotate !== "velocity" && o.facing !== "none") flip = art.direction * (o.facing === "left" ? -1 : 1);
          art.item.scale.set(k * flip, k);
          art.item.rotation = rotation;
          const h = art.baseH * k;
          /* 1-D horizontal lanes: artwork stands on its road; otherwise centred on the body */
          const baseY = mode === "lanes" ? point.y + 15 : point.y;
          art.item.position.set(point.x, bottom && mode !== "lanes" ? point.y + h / 2 : baseY);
          art.item.pivot.y = bottom ? art.bounds.y + art.bounds.height : art.bounds.y + art.bounds.height / 2;
          /* touching another body or a solid fixture: the solver point is the contact face, not the centre,
             so the drawings meet exactly where the point bodies meet (no overlap, no gap) */
          let pivotX = art.bounds.x + art.bounds.width / 2;
          if (track.contact && rotate === "none") {
            if (track.along === "x") pivotX += track.contact * flip * art.bounds.width / 2;
            else if (track.along === "y") { art.item.pivot.y = track.contact > 0 ? art.bounds.y + art.bounds.height : art.bounds.y; art.item.position.y = point.y; }
          }
          art.item.pivot.x = pivotX;
          art.item.visible = true;
          art.point = { x: point.x, y: point.y };
          art.placed = { x: art.item.x, y: art.item.y, sx: k * flip, sy: k, rotation, pivotY: art.item.pivot.y, pivotX };
          if (track.label) track.label.position.set(point.x, (mode === "lanes" ? baseY - h : point.y - h / 2) - 6);
        } else {
          if (show.bodies) body(bodyLayer, point.x, point.y, radius, track.color);
          if (track.label) track.label.position.set(point.x, point.y - radius - (mode === "lanes" ? 18 : 6));
        }
      }
      /* solver-bound links, riders and instruments */
      for (const item of links) {
        let a = endPoint(item.from, values), b = endPoint(item.to, values);
        /* lanes / columns have one measured axis: a fixed point shares the other axis with the body it holds */
        const bare = (end: LinkEnd) => Array.isArray(end) && end.length === 2;
        if (a && b && mode === "lanes") { if (bare(item.from)) a = { x: a.x, y: b.y }; else if (bare(item.to)) b = { x: b.x, y: a.y }; }
        if (a && b && mode === "columns") { if (bare(item.from)) a = { x: b.x, y: a.y }; else if (bare(item.to)) b = { x: a.x, y: b.y }; }
        if (!a || !b) continue;
        if (item.dashed) dashed(dynamicLayer, a.x, a.y, b.x, b.y);
        else dynamicLayer.moveTo(a.x, a.y).lineTo(b.x, b.y);
        dynamicLayer.stroke({ color: item.color ?? p.spring, width: item.width, cap: "round" });
      }
      for (const [item, rider] of followers) {
        const track = tracks.find(entry => entry.p.id === rider.id);
        const point = track ? track.screen(values) : null;
        rider.placed = point ? { x: point.x + rider.dx, y: point.y + rider.dy } : undefined;
        if (rider.placed) item.position.set(rider.placed.x, rider.placed.y);
        item.visible = !!rider.placed;
      }
      for (const ins of instruments) drawInstrument(ins, ins.key ? values[ins.key] : NaN, frame.t);
      // keep lane labels from colliding with velocity labels in 1-D lanes
      for (const gauge of gauges) {
        const v = values[gauge.key], span = gauge.max - gauge.min || 1;
        const clamp = (q: number) => Math.min(gauge.x + gauge.w, Math.max(gauge.x, q));
        const zero = clamp(gauge.x + (0 - gauge.min) / span * gauge.w), at = clamp(gauge.x + (v - gauge.min) / span * gauge.w);
        gauge.bar.clear().roundRect(gauge.x, gauge.y, gauge.w, 8, 4).fill({ color: p.grid });
        if (Number.isFinite(v)) gauge.bar.rect(Math.min(zero, at), gauge.y, Math.max(1.5, Math.abs(at - zero)), 8).fill({ color: gauge.color });
        const meta = host.data().scene.fields[gauge.key];
        gauge.value.text = meta ? format(v, meta.unit, 3, Math.max(Math.abs(meta.min), Math.abs(meta.max))) : format(v);
      }
      if (hudTime) {
        /* time in a unit suited to the whole run (ns … years) */
        const duration = host.data().timeline.durationSeconds;
        const units: Array<[number, string]> = [[31557600, "năm"], [86400, "ngày"], [3600, "h"], [60, "min"], [1, "s"], [1e-3, "ms"], [1e-6, "µs"], [1e-9, "ns"]];
        const [size, unit] = units.find(([value]) => duration / value >= 1.5) ?? units[units.length - 1];
        hudTime.text = "t = " + (frame.t / size).toFixed(2) + " " + unit;
      }
      const alignLegend = () => {
        if (!compact) return;
        /* one line per participant: ● name | values, names aligned in one column */
        const column = Math.max(0, ...legend.map(entry => entry.values.width));
        for (const entry of legend) {
          entry.name.x = entry.values.x - column - 12;
          entry.dot.x = entry.name.x - entry.name.width - 9;
        }
      };
      for (const entry of legend) {
        const f = entry.track.p.fields, parts: string[] = [], meta = host.data().scene.fields;
        const add = (key: string | undefined) => {
          if (!key || !meta[key]) return;
          const m = meta[key], v = values[key];
          if (m.kind === "angle" && m.unit === "rad") parts.push(m.symbol + "=" + format(v * 180 / Math.PI, "°").replace(" °", "°"));
          else parts.push(m.symbol + "=" + format(v, m.unit, 3, Math.max(Math.abs(m.min), Math.abs(m.max))));
        };
        if (entry.track.p.dims === 2) { add(f.x); add(f.y); if (f.angle) add(f.angle); }
        else add(f.position);
        if (entry.track.p.dims === 2 && f.vx && f.vy) parts.push("|v|=" + format(Math.hypot(values[f.vx], values[f.vy]), "m/s"));
        else add(f.velocity);
        if (!parts.length) for (const key of Object.keys(meta).filter(k => meta[k].participantId === entry.track.p.id).slice(0, 2)) add(key);
        entry.values.text = parts.join("  ");
      }
      alignLegend();
    }

    // ------------------------------------------------------------ trusted runtime hooks
    /** Undo anything generated code did to solver-bound visuals after the kit placed them this frame. */
    function enforce() {
      if (disposed) return;
      if (root.parent !== app.stage) app.stage.addChildAt(root, 0);
      const reset = (item: PixiNS.Container) => { item.position.set(0, 0); item.scale.set(1, 1); item.rotation = 0; item.pivot.set(0, 0);
        item.skew.set(0, 0); item.visible = true; item.alpha = 1; item.renderable = true; };
      reset(root);
      kitLayers.forEach((layer, index) => { if (layer.parent !== root) root.addChildAt(layer, Math.min(index, root.children.length)); reset(layer); });
      for (const art of attached.values()) {
        if (!art.placed) continue;
        if (art.item.parent !== artLayer) artLayer.addChild(art.item);
        art.item.position.set(art.placed.x, art.placed.y);
        art.item.scale.set(art.placed.sx, art.placed.sy);
        art.item.rotation = art.placed.rotation;
        art.item.pivot.y = art.placed.pivotY; art.item.pivot.x = art.placed.pivotX;
        art.item.visible = true; art.item.alpha = 1; art.item.renderable = true;
      }
      applyFixtures();
      for (const [item, rider] of followers) {
        if (item.parent !== followLayer) followLayer.addChild(item);
        if (rider.placed) item.position.set(rider.placed.x, rider.placed.y);
      }
      for (const ins of instruments) {
        if (ins.inner.parent !== instrumentLayer) instrumentLayer.addChild(ins.inner);
        if (ins.art.parent !== ins.inner) ins.inner.addChildAt(ins.art, 0);
        ins.movers.forEach((mover, index) => {
          if (mover.parent !== ins.inner) ins.inner.addChild(mover);
          if (ins.parts[index] && ins.parts[index].parent !== mover) mover.addChild(ins.parts[index]);
        });
        if (ins.mask && ins.mask.parent !== ins.inner) ins.inner.addChild(ins.mask);
        ins.inner.visible = true; ins.inner.alpha = 1; ins.inner.renderable = true;
        if (ins.shown) drawInstrument(ins, ins.shown.value, ins.shown.t);
      }
    }
    function shown(item: PixiNS.Container) {
      let alpha = 1;
      for (let node: PixiNS.Container | null = item; node; node = node.parent) {
        if (!node.visible || node.renderable === false) return 0;
        alpha *= node.alpha;
        if (node === app.stage) return alpha;
      }
      return 0;
    }
    /** Generated (non-kit) display objects: stage children beside the kit, extra children of the kit root, static props. */
    const NODE_BUDGET = 4000;
    function generatedNodes() {
      const out: PixiNS.Container[] = [];
      const visit = (node: PixiNS.Container) => {
        if (out.length > NODE_BUDGET) return;
        out.push(node);
        for (const child of node.children ?? []) visit(child);
      };
      for (const child of app.stage.children) if (child !== root) visit(child);
      for (const child of root.children) if (!kitLayers.includes(child)) visit(child);
      for (const child of propLayer.children) visit(child);
      return out;
    }
    function describeNode(node: PixiNS.Container) {
      const kind = node instanceof PIXI.Text ? "Text" : node instanceof PIXI.Graphics ? "Graphics" : node instanceof PIXI.Sprite ? "Sprite" : "Container";
      return kind + (node.label && node.label !== kind ? ' "' + String(node.label).slice(0, 40) + '"' : "");
    }
    function verify(run: (t: number) => void, times: number[]): string[] {
      const issues: string[] = [];
      const add = (message: string) => { if (!issues.includes(message) && issues.length < 14) issues.push(message); };
      const scene = host.data().scene;
      const fmt = (v: number) => format(v, "", 4);
      type Snap = { t: number; nodes: Map<PixiNS.Container, { cx: number; cy: number; w: number; h: number; on: boolean; text: boolean }> };
      const snaps: Snap[] = [];
      for (const t of times) {
        run(t);
        const values = host.sample(t), W = app.screen.width, H = app.screen.height;
        const when = " at t = " + fmt(t) + " s";
        // 1. every moving participant is drawn exactly where the verified solver puts it
        for (const track of tracks) {
          if (track.p.dims === 0) continue;
          const expected = track.screen(values);
          if (!expected) { add(track.p.id + ": solver position is not finite" + when + "."); continue; }
          if (expected.x < -1 || expected.x > W + 1 || expected.y < -1 || expected.y > H + 1)
            add(track.p.id + ": solver position falls outside the stage" + when + " (kit camera).");
          if (cam && mode !== "board") {
            const world = cam.toWorld(expected.x, expected.y), f = track.p.fields;
            const check = (key: string | undefined, drawn: number, span: Bounds, axis: "x" | "y") => {
              if (!key) return;
              const extent = axis === "x" ? span.maxX - span.minX : span.maxY - span.minY;
              if (Math.abs(drawn - values[key] - (axis === "x" ? track.originX : 0)) > Math.max(1e-9, extent * 0.002))
                add(track.p.id + ": drawn at " + key + " = " + fmt(drawn) + " but the solver gives " + fmt(values[key]) + when + ".");
            };
            if (track.p.dims === 2) { check(f.x, world.x, cam.bounds, "x"); check(f.y, world.y, cam.bounds, "y"); }
            else if (mode === "lanes" || (mode === "plane" && !track.p.vertical)) check(f.position, world.x, cam.bounds, "x");
            else check(f.position, world.y, cam.bounds, "y");
          }
          const art = attached.get(track.p.id);
          if (art) {
            if (!art.point || Math.hypot(art.point.x - expected.x, art.point.y - expected.y) > 1)
              add(track.p.id + ": artwork was not placed at its solver position" + when + ".");
            else if (art.placed) {
              const at = art.item.parent ? art.item.parent.toGlobal({ x: art.item.x, y: art.item.y }) : null;
              const want = root.toGlobal({ x: art.placed.x, y: art.placed.y });
              if (!at || Math.hypot(at.x - want.x, at.y - want.y) > 1.5) add(track.p.id + ": artwork was moved away from its solver position" + when + ".");
            }
            if (shown(art.item) < 0.2) add(track.p.id + ": artwork is hidden or transparent" + when + ".");
          } else if (!show.bodies) add(track.p.id + ": moving participant has no visible body (attach artwork or keep the kit body marker).");
        }
        // 2. every state-only participant has a meter or instrument showing the verified value
        const metered = scene.participants.filter(needsMeter);
        /* only a stage made of meter cards must show every value itself; otherwise values are listed under the stage */
        if (mode === "board" && !instruments.length) for (const participant of metered) if (!meterCovered.has(participant.id))
          add(participant.id + ": its solver state is not shown on stage (add an instrument for one of its fields).");
        for (const ins of instruments) {
          if (shown(ins.art) < 0.2 && !ins.key) add("instrument drawing \"" + ins.label + "\": artwork is hidden" + when + ".");
          if (!ins.key) continue;
          const value = values[ins.key];
          if (!ins.shown || Math.abs(ins.shown.t - t) > 1e-9 * Math.max(1, t) || Math.abs(ins.shown.fraction - fractionOf(ins, value)) > 1e-6)
            add("instrument " + ins.key + ": indicator does not match the solver value " + fmt(value) + when + ".");
          if (shown(ins.art) < 0.2) add("instrument " + ins.key + ": artwork is hidden" + when + ".");
        }
        // 3. snapshot generated objects: anything outside the kit must stay put
        const nodes: Snap["nodes"] = new Map();
        const generated = generatedNodes();
        /* fail closed: what cannot be inspected is not accepted */
        if (generated.length > NODE_BUDGET) add("the program creates more than " + NODE_BUDGET + " display objects of its own, too many to cross-check; "
          + "draw repeated items as one SVG, or let the kit repeat a moving part (instrument drive travel copies).");
        for (const node of generated) {
          const on = shown(node) > 0.02, isText = node instanceof PIXI.Text;
          let cx = 0, cy = 0, w = 0, h = 0;
          if (on) {
            if (isText) { const g = node.getGlobalPosition(); cx = g.x; cy = g.y; }
            else {
              const b = node.getBounds();
              if (Number.isFinite(b.minX) && b.width + b.height > 0) { cx = (b.minX + b.maxX) / 2; cy = (b.minY + b.maxY) / 2; w = b.width; h = b.height; }
            }
          }
          nodes.set(node, { cx, cy, w, h, on, text: isText });
        }
        snaps.push({ t, nodes });
      }
      // 4. solid fixtures: over the WHOLE timeline no moving participant may pass through one
      if (cam) for (const f of fixtures) {
        if (!f.world || !f.screen) { add("a fixture's position parameter (" + [f.x, f.y].filter(v => typeof v === "string" && v).join(", ") + ") is not in the plan; use one of: "
          + Object.keys(host.data().parameters ?? {}).join(", ") + "."); continue; }
        if (!f.solid) continue;
        const bounds = f.item.getBounds();
        for (const track of tracks) {
          if (track.p.dims === 0) continue;
          const frames = host.data().timeline.frames, fields = host.data().scene.fields, pf = track.p.fields;
          /* motion along a single axis (1-D, or planar data that never leaves one line) is checked as a side test */
          const still = (key?: string) => { const m = key ? fields[key] : undefined; return !!m && m.max - m.min <= 1e-9 * Math.max(1, Math.abs(m.max), Math.abs(m.min)); };
          const axis: "x" | "y" | null = track.p.dims === 1 ? (mode === "columns" || (mode === "plane" && track.p.vertical) ? "y" : "x")
            : still(pf.y) ? "x" : still(pf.x) ? "y" : null;
          let side = 0, flip: number | null = null, inside: number | null = null;
          for (const frame of frames) {
            const q = track.screen(frame.values);
            if (!q) continue;
            if (axis) {
              /* one axis: the body must stay on one side of the fixture's anchor line */
              const along = axis === "y" ? q.y - f.screen.y : q.x - f.screen.x;
              const s = Math.abs(along) <= 1 ? 0 : Math.sign(along);
              if (s && side && s !== side) { flip = frame.t; break; }
              if (s) side = s;
            } else if (q.x > bounds.minX + 2 && q.x < bounds.maxX - 2 && q.y > bounds.minY + 2 && q.y < bounds.maxY - 2) { inside = frame.t; break; }
          }
          const at = flip ?? inside;
          if (at !== null) add(track.p.id + " passes through the solid fixture placed at (" + fmt(f.world.x) + " m, " + fmt(f.world.y) + " m) at t = " + fmt(at)
            + " s: the fixture is not where the verified physics puts the obstacle. Place it with the plan parameter (or value) the solver uses.");
        }
      }
      // 4b. independent systems drawn side by side have frames of their own: fixed points must say whose frame they are in
      if (ownFrames()) {
        const names = tracks.filter(track => track.p.dims > 0).map(track => track.p.id).join(", ");
        if (links.some(item => [item.from, item.to].some(end => Array.isArray(end) && end.length === 2)))
          add("a link uses a scene point \"x,y\", but the participants (" + names + ") are laid out side by side, each with its own origin; write the point in the participant's own frame as \"<id>:x,y\" (e.g. its pivot is \"<id>:0,0\").");
        if (fixtures.some(f => !f.frame))
          add("a fixture has no frame, but the participants (" + names + ") are laid out side by side, each with its own origin; give the fixture the participant whose frame its position is measured in (frame).");
      }
      // 5. solid bodies on one line may touch but never pass through each other
      {
        const solid = tracks.filter(track => track.lineKey && attached.get(track.p.id)?.options.solid);
        const coordinate = (track: Track) => (values: Record<string, number>) => { const q = track.screen(values); return q ? (track.along === "y" ? q.y : q.x) : null; };
        for (let i = 0; i < solid.length; i++) for (let j = i + 1; j < solid.length; j++) {
          const a = solid[i], b = solid[j];
          if (a.lineKey !== b.lineKey) continue;
          const r = screenRelation(coordinate(a), coordinate(b));
          if (r.cross) add(a.p.id + " and " + b.p.id + " are solid bodies on the same line but pass through each other at t = " + fmt(r.firstCross ?? 0)
            + " s: the plan's positions/velocities of the two do not describe the same interaction (or they are not on the same line — give them different \"line\" names).");
        }
      }
      const W = app.screen.width, H = app.screen.height, tol = Math.max(2, 0.004 * Math.min(W, H));
      const first = snaps[0];
      const reported = new Set<PixiNS.Container>();
      if (first) for (const snap of [...snaps.slice(1)].reverse()) {
        const unmatched: string[] = [];
        for (const [node, a] of snap.nodes) {
          const b = first.nodes.get(node);
          if (reported.has(node)) continue;
          if (!b) { if (a.on && !a.text && a.w + a.h > 0) unmatched.push(describeNode(node) + " (" + Math.round(a.cx) + ", " + Math.round(a.cy) + ")"); continue; }
          if (a.on !== b.on) { reported.add(node); add(describeNode(node) + " appears/disappears between t = " + fmt(first.t) + " s and t = " + fmt(snap.t) + " s without being bound to solver data."); continue; }
          if (!a.on) continue;
          const moved = Math.hypot(a.cx - b.cx, a.cy - b.cy), resized = a.text ? 0 : Math.max(Math.abs(a.w - b.w), Math.abs(a.h - b.h));
          if (moved > tol || resized > 2 * tol) reported.add(node);
          if (moved > tol || resized > 2 * tol)
            add(describeNode(node) + " near (" + Math.round(b.cx) + ", " + Math.round(b.cy) + ") " + (moved > tol ? "moves by " + Math.round(moved) + " px" : "changes size by " + Math.round(resized) + " px")
              + " between t = " + fmt(first.t) + " s and t = " + fmt(snap.t) + " s, but it is not driven by the kit from solver data.");
        }
        if (unmatched.length) {
          /* objects rebuilt every frame are fine only if their geometry never changes */
          const key = (e: { cx: number; cy: number; w: number; h: number }) => [e.cx, e.cy, e.w, e.h].map(v => Math.round(v / tol)).join(",");
          const before = new Set([...first.nodes.values()].filter(e => e.on && !e.text).map(key));
          const changed = [...snap.nodes.entries()].filter(([node, e]) => !first.nodes.has(node) && e.on && !e.text && e.w + e.h > 0 && !before.has(key(e)));
          if (changed.length) add("objects re-created in update() change geometry between t = " + fmt(first.t) + " s and t = " + fmt(snap.t)
            + " s (" + changed.slice(0, 3).map(([node, e]) => describeNode(node) + " at (" + Math.round(e.cx) + ", " + Math.round(e.cy) + ")").join(", ") + ") without being bound to solver data.");
        }
      }
      return issues;
    }
    host.register?.({
      root,
      begin() { updatedThisFrame = false; },
      finish(frame) { if (!updatedThisFrame) update(frame); enforce(); },
      refresh() { if (!disposed) build(); },
      verify,
    });
    build();
    /**
     * Attach contextual artwork (Sprite/Graphics/Container) to a participant. The kit
     * normalises its size, anchors it (bottom-centre on 1-D lanes, centre elsewhere),
     * moves it with the solver data every frame and hides the default body marker.
     */
    function attach(id: string, item: PixiNS.Container, options: ArtOptions = {}) {
      if (!tracks.some(track => track.p.id === id))
        throw Error('attach(): unknown participant "' + id + '". Participants: ' + tracks.map(track => track.p.id).join(", "));
      if (!item || typeof (item as PixiNS.Container).getLocalBounds !== "function")
        throw Error("attach(): pass a PIXI display object (e.g. new PIXI.Sprite(await api.svgTexture(svg))).");
      item.scale.set(1);
      const b = item.getLocalBounds();
      item.pivot.set(b.x + b.width / 2, b.y + b.height / 2);
      item.visible = false;
      artLayer.addChild(item);
      attached.set(id, { item, options, baseW: b.width, baseH: b.height, direction: 1,
        bounds: { x: b.x, y: b.y, width: b.width, height: b.height } });
      /* line grouping and contact faces depend on the artwork's options */
      build();
      return item;
    }
    /** Register viewport-sized environment artwork: (layout) => SVG markup (width/height = layout.width/height). */
    function backdrop(source: (layout: SceneLayout) => string | Promise<string>) {
      if (typeof source !== "function") throw Error("backdrop() expects a function (layout) => svgMarkup.");
      backdropSource = source;
      build();
    }
    function environment(svg: string, anchorY = -1) {
      if (typeof svg !== "string" || !svg.trim()) throw Error("environment() expects SVG markup.");
      environmentSpec = { svg, anchorY: Number(anchorY) };
      build();
    }
    function dispose() {
      disposed = true;
      if (backdropTimer) clearTimeout(backdropTimer);
      if (backdropSprite) host.release?.(backdropSprite.texture);
      attached.clear(); followers.clear(); instruments = []; links = []; fixtures = []; root.destroy({ children: true });
    }
    return {
      container: root,
      update,
      instrument,
      follow,
      link,
      fixture,
      resize() { build(); },
      setData() { build(); },
      screenOf,
      attach,
      backdrop,
      environment,
      setDecor,
      layout,
      /** Container drawn above the environment and measurement frame, below participants: static props. */
      props: propLayer,
      detach(id: string) { const art = attached.get(id); if (art) { art.item.removeFromParent(); attached.delete(id); } },
      camera: () => cam,
      mode: () => mode,
      dispose,
      destroy: dispose,
    };
  }

  type BodySpec = { id: string; svg: string; facing?: "right" | "left" | "none"; rotate?: "none" | "velocity" | "link";
    anchor?: "auto" | "bottom" | "center"; sizeMeters?: number; size?: number; line?: string; solid?: boolean };
  type InstrumentSpec = { field: string; svg: string; part?: string; label?: string; on?: string;
    drive?: { property?: string; pivot?: Pt; path?: Pt[]; from?: number; to?: number; copies?: number; useFieldAngle?: boolean;
      min?: number | null; max?: number | null } };
  type DriveName = "rotate" | "translate" | "scale" | "reveal" | "opacity" | "travel" | "none";
  type FixtureSpec = { svg: string; x?: number | null; xParameter?: string; y?: number | null; yParameter?: string;
    anchor?: Pt; sizeMeters?: number; solid?: boolean; angle?: number | null; angleParameter?: string; angleScale?: number; flipBy?: string; frame?: string };
  type LinkSpec = { from: string; to: string; color?: string; width?: number; dashed?: boolean };
  type IllustratedSpec = { environment?: string; environmentAnchorY?: number; surface?: string; support?: string; connector?: string; bodies?: BodySpec[];
    instruments?: InstrumentSpec[]; links?: LinkSpec[]; fixtures?: FixtureSpec[];
    overlay?: { links?: boolean; axes?: boolean; grid?: boolean; trails?: boolean; strobe?: boolean; vectors?: boolean; labels?: boolean; angles?: boolean } };
  /**
   * Declarative illustrated scene: every visual comes from generated SVG, the kit
   * only decides WHERE things are (from solver data). Roles are physical, not
   * topical: environment (full scene), surface (what bodies move on), support
   * (fixed point a connector hangs from), connector (string/rod/spring between
   * support and body) and one body per participant.
   */
  async function illustratedScene(spec: IllustratedSpec) {
    if (!spec || typeof spec !== "object") throw Error("illustratedScene(spec): spec object required.");
    const o = spec.overlay ?? {};
    const text_ = (value: unknown) => typeof value === "string" && value.trim() ? value : undefined;
    const base = standardScene({ bodies: true, grid: o.grid ?? false, axes: o.axes ?? true, trails: o.trails ?? true,
      strobe: o.strobe ?? false, vectors: o.vectors ?? true, labels: o.labels ?? true, angles: o.angles ?? false,
      links: (o.links ?? true) && !text_(spec.connector), supports: !text_(spec.support) });
    const environment = text_(spec.environment);
    if (environment) base.environment(environment, Number(spec.environmentAnchorY ?? -1));
    const load = async (svg?: string) => svg && host.svg ? host.svg(svg) : undefined;
    const [surface, support, connector] = await Promise.all([load(text_(spec.surface)), load(text_(spec.support)), load(text_(spec.connector))]);
    base.setDecor({ surface, support, connector });
    const ids = new Set(host.data().scene.participants.map(item => item.id));
    for (const body of Array.isArray(spec.bodies) ? spec.bodies : []) {
      if (!body || !ids.has(body.id) || !text_(body.svg)) continue;
      const sprite = new PIXI.Sprite(await host.svg!(body.svg));
      /* a participant that does not move in space: its picture stands (static) beside its instruments */
      const still = host.data().scene.participants.find(item => item.id === body.id);
      if (still && still.dims === 0) {
        const field = Object.values(host.data().scene.fields).find(meta => meta.participantId === body.id);
        if (field) base.instrument({ field: field.key, art: sprite, label: still.label });
        continue;
      }
      base.attach(body.id, sprite, { facing: body.facing, rotate: body.rotate,
        anchor: body.anchor === "bottom" || body.anchor === "center" ? body.anchor : undefined,
        sizeMeters: Number(body.sizeMeters) > 0 ? Number(body.sizeMeters) : undefined, size: body.size,
        line: typeof body.line === "string" && body.line.trim() ? body.line.trim() : undefined, solid: !!body.solid });
    }
    /* instruments: pivot/path are given in the body's SVG viewBox units; the moving part is drawn in the same
       coordinate system (its own viewBox is placed where it lies in the body's) */
    const viewBox = (svg: string) => {
      const box = /viewBox\s*=\s*["']\s*([-\d.eE]+)[\s,]+([-\d.eE]+)[\s,]+([-\d.eE]+)[\s,]+([-\d.eE]+)/.exec(svg);
      const vb = box ? { x: Number(box[1]) || 0, y: Number(box[2]) || 0, w: Number(box[3]), h: Number(box[4]) } : null;
      return vb && vb.w > 0 && vb.h > 0 ? vb : null;
    };
    /* an apparatus first, then the parts drawn on it ("on" = its field; their points are in its viewBox) */
    const frames = new Map<string, { vb: { x: number; y: number; w: number; h: number }; sx: number; sy: number }>();
    /* an instrument with field "" is a drawing of the set-up; "on" names the drawing a part belongs to, by its field
       or by its position in the list ("0", "1", …) */
    const listed = Array.isArray(spec.instruments) ? spec.instruments : [];
    const items = listed.filter(item => item && typeof item.field === "string");
    const hosts = new Map<string, PixiNS.Container>();
    for (const item of [...items.filter(item => !text_(item.on)), ...items.filter(item => text_(item.on))]) {
      const onto = text_(item.on) ? frames.get(item.on!) : undefined;
      if (text_(item.on) && !onto)
        throw Error('instrument "' + item.field + '": on = "' + item.on + '" names no drawing. Use the field of an instrument that has its own svg, or its position in the list (0 = first); field "" marks a drawing of the set-up.');
      if (!onto && !text_(item.svg)) continue;
      const sprite = onto ? null : new PIXI.Sprite(await host.svg!(item.svg));
      const vb = onto ? onto.vb : viewBox(item.svg) ?? { x: 0, y: 0, w: sprite!.texture.width, h: sprite!.texture.height };
      const sx = onto ? onto.sx : sprite!.texture.width / vb.w, sy = onto ? onto.sy : sprite!.texture.height / vb.h;
      const reference = String(listed.indexOf(item));
      const toLocal = (q: unknown): Pt | undefined => {
        const r = q as Pt;
        return r && Number.isFinite(Number(r.x)) && Number.isFinite(Number(r.y)) ? { x: (Number(r.x) - vb.x) * sx, y: (Number(r.y) - vb.y) * sy } : undefined;
      };
      let part: PixiNS.Sprite | null = null;
      if (text_(item.part)) {
        part = new PIXI.Sprite(await host.svg!(item.part!));
        const pvb = viewBox(item.part!) ?? vb;
        part.position.set((pvb.x - vb.x) * sx, (pvb.y - vb.y) * sy);
        part.scale.set(pvb.w * sx / Math.max(1e-9, part.texture.width), pvb.h * sy / Math.max(1e-9, part.texture.height));
      }
      const d = item.drive ?? {};
      const registered = base.instrument({ field: item.field, art: sprite, part, label: item.label, on: onto ? hosts.get(item.on!) : undefined,
        drive: part ? { property: d.property as DriveName, pivot: toLocal(d.pivot), from: d.from, to: d.to, copies: d.copies,
          useFieldAngle: d.useFieldAngle, min: d.min ?? null, max: d.max ?? null,
          path: (Array.isArray(d.path) ? d.path : []).map(toLocal).filter((q): q is Pt => !!q) } : undefined });
      if (!onto) for (const key of item.field ? [reference, item.field] : [reference]) { frames.set(key, { vb, sx, sy }); hosts.set(key, registered); }
    }
    /* fixtures: static art at a world position (number or plan parameter); anchor in the SVG viewBox */
    for (const item of Array.isArray(spec.fixtures) ? spec.fixtures : []) {
      if (!item || !text_(item.svg)) continue;
      const sprite = new PIXI.Sprite(await host.svg!(item.svg));
      const vb = viewBox(item.svg) ?? { x: 0, y: 0, w: sprite.texture.width, h: sprite.texture.height };
      const sx = sprite.texture.width / vb.w, sy = sprite.texture.height / vb.h, a = item.anchor;
      base.fixture(sprite, { x: text_(item.xParameter) ?? (Number.isFinite(Number(item.x)) && item.x !== null ? Number(item.x) : null),
        y: text_(item.yParameter) ?? (Number.isFinite(Number(item.y)) && item.y !== null ? Number(item.y) : null),
        anchor: a && Number.isFinite(Number(a.x)) && Number.isFinite(Number(a.y)) ? { x: (Number(a.x) - vb.x) * sx, y: (Number(a.y) - vb.y) * sy } : undefined,
        sizeMeters: item.sizeMeters, solid: item.solid,
        angle: text_(item.angleParameter) ?? (Number.isFinite(Number(item.angle)) && item.angle !== null ? Number(item.angle) : null),
        angleScale: item.angleScale, flipBy: text_(item.flipBy), frame: text_(item.frame) && ids.has(item.frame!) ? item.frame : undefined });
    }
    /* links: "<participant id>" or "x,y" (fixed world point in metres) */
    const end = (value: string): string | [number, number] | [number, number, string] => {
      const text = String(value);
      if (ids.has(text)) return text;
      /* "<participant id>:x,y" = a point in that participant's own frame; "x,y" = a point of the scene frame */
      const colon = text.lastIndexOf(":"), frame = colon > 0 ? text.slice(0, colon).trim() : "";
      const numbers = (frame ? text.slice(colon + 1) : text).split(",").map(part => Number(part.trim()));
      if (numbers.length !== 2 || !numbers.every(Number.isFinite)) return text;
      return frame ? [numbers[0], numbers[1], frame] : [numbers[0], numbers[1]];
    };
    for (const item of Array.isArray(spec.links) ? spec.links : [])
      if (item && item.from && item.to) base.link(end(item.from), end(item.to), { color: text_(item.color), width: item.width, dashed: item.dashed });
    return base;
  }

  /** Point (and heading) at arc-length `distance` along a polyline; wraps around closed loops. */
  function pointAlong(points: Pt[], distance: number, closed = false) {
    const pts = closed && points.length > 1 ? [...points, points[0]] : points;
    let total = 0;
    const lengths = pts.slice(1).map((q, i) => { const d = Math.hypot(q.x - pts[i].x, q.y - pts[i].y); total += d; return d; });
    if (!(total > 0)) return { x: pts[0]?.x ?? 0, y: pts[0]?.y ?? 0, angle: 0, total: 0 };
    let s = closed ? ((distance % total) + total) % total : Math.max(0, Math.min(total, distance));
    for (let i = 0; i < lengths.length; i++) {
      if (s <= lengths[i] || i === lengths.length - 1) {
        const a = pts[i], b = pts[i + 1], u = lengths[i] > 0 ? s / lengths[i] : 0;
        return { x: a.x + (b.x - a.x) * u, y: a.y + (b.y - a.y) * u, angle: Math.atan2(b.y - a.y, b.x - a.x), total };
      }
      s -= lengths[i];
    }
    return { x: pts[pts.length - 1].x, y: pts[pts.length - 1].y, angle: 0, total };
  }

  return Object.freeze({
    FONT, palette, color, pointAlong, niceStep, format, text, camera, arrow, dashed, hatch, spring, body, fieldRanges,
    scene: () => host.data().scene,
    participants: () => host.data().scene.participants,
    standardScene,
    illustratedScene,
  });
}