import type * as PixiNS from "pixi.js";
import type { SceneDescriptor, SceneParticipant } from "./sceneModel";

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
    scene: SceneDescriptor; theme: "LIGHT" | "DARK"; verificationStatus: string };
  sample(t: number): Record<string, number>;
  /** Rasterise SVG markup into a texture (bridge-side sanitising); `screen` = viewport-sized art. */
  svg?(markup: string, options?: { screen?: boolean; width?: number; height?: number }): Promise<PixiNS.Texture>;
  /** Free a texture created by `svg` (returns its pixels to the memory budget). */
  release?(texture: PixiNS.Texture): void;
  /** Report an asynchronous rendering failure to the host. */
  fail?(message: string): void;
  /** Average luminance (0–1) of a viewport texture, when the bridge measured it. */
  luma?(texture: PixiNS.Texture): number | undefined;
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
  function format(value: number, unit = "", digits = 3) {
    if (!Number.isFinite(value)) return "—";
    let text: string;
    const magnitude = Math.abs(value);
    if (magnitude < 1e-9) text = "0";
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
    const show = { bodies: true, labels: true, hud: true, strobe: true, vectors: true, axes: true, trails: true,
      links: true, supports: true, angles: true, ...options };
    const root = new PIXI.Container();
    root.label = "physlive-standard-scene";
    app.stage.addChild(root);
    const backdropLayer = new PIXI.Container(), propLayer = new PIXI.Container(), hudPanel = new PIXI.Graphics();
    const staticLayer = new PIXI.Graphics(), dynamicLayer = new PIXI.Graphics(), bodyLayer = new PIXI.Graphics();
    const staticText = new PIXI.Container(), dynamicText = new PIXI.Container(), artLayer = new PIXI.Container();
    const decorLayer = new PIXI.Container(), connectorLayer = new PIXI.Container(), gaugeLayer = new PIXI.Container();
    root.addChild(backdropLayer, decorLayer, staticLayer, propLayer, connectorLayer, dynamicLayer, bodyLayer, artLayer, gaugeLayer, hudPanel, staticText, dynamicText);
    /** Generated SVG textures for the physical roles the kit infers from solver data. */
    const decor: { surface?: PixiNS.Texture; support?: PixiNS.Texture; connector?: PixiNS.Texture } = {};
    const connectors = new Map<string, PixiNS.Sprite>();
    type ArtOptions = { size?: number; sizeMeters?: number; autoScale?: boolean; facing?: "right" | "left" | "none";
      rotate?: "none" | "velocity" | "link"; anchor?: "bottom" | "center" };
    /** Participant artwork supplied by generated code, positioned by the kit. */
    const attached = new Map<string, { item: PixiNS.Container; options: ArtOptions; baseW: number; baseH: number; direction: number;
      bounds: { x: number; y: number; width: number; height: number } }>();
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
      monotonic: boolean; originX: number; quiet?: boolean; label?: PixiNS.Text; vectorLabels: Partial<Record<Kind, PixiNS.Text>>; angleLabel?: PixiNS.Text; wallX?: number };
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
      const top = show.hud ? Math.max(68, legendBottom + 16) : 20, bottom = H - (show.axes ? 46 : 20);
      const left = show.axes ? 58 : 20, right = W - 24;
      const view: Rect = { x: left, y: top, w: Math.max(40, right - left), h: Math.max(40, bottom - top) };
      tracks = scene.participants.map(participant => ({ p: participant, color: color(participant.colorIndex), lane: 0,
        screen: () => null, path: [], strobe: [], vectorLabels: {}, monotonic: true, originX: 0 }));
      const spatialTracks = tracks.filter(track => track.p.dims > 0);
      spatialTracks.forEach((track, index) => { track.lane = index; });

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
        laneHeight = Math.min(104, view.h / Math.max(1, spatialTracks.length));
        const blockTop = view.y + (view.h - laneHeight * spatialTracks.length) / 2 + 10;
        laneY = lane => blockTop + laneHeight * (lane + 0.5);
        cam = camera(view, { ...bounds, minY: 0, maxY: 1 }, false);
      } else if (mode === "columns") {
        padBounds(1e-6);
        const columnWidth = Math.min(170, (view.w - legendWidth * 0.3) / Math.max(1, spatialTracks.length));
        const blockLeft = view.x + (view.w - columnWidth * spatialTracks.length) / 2;
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
      if (mode === "board" && show.axes) {
        const keys = Object.values(scene.fields).filter(meta => meta.kind !== "angle" || meta.unit !== "rad").slice(0, 12);
        const columns = keys.length > 6 ? 3 : keys.length > 2 ? 2 : 1;
        const gap = 14, cardW = Math.min(360, (view.w - gap * (columns - 1)) / columns), cardH = 62;
        const rows = Math.ceil(keys.length / columns), blockH = rows * cardH + (rows - 1) * gap;
        const left = view.x + (view.w - (cardW * columns + gap * (columns - 1))) / 2;
        const top = Math.max(view.y, view.y + (view.h - blockH) / 2);
        keys.forEach((meta, index) => {
          const x = left + (index % columns) * (cardW + gap), y = top + Math.floor(index / columns) * (cardH + gap);
          const track = tracks.find(item => item.p.id === meta.participantId);
          const color = track ? track.color : p.axis;
          const card = new PIXI.Graphics().roundRect(x, y, cardW, cardH, 10).fill({ color: p.panel, alpha: 0.9 }).stroke({ color: p.grid, width: 1 });
          const who = scene.participants.length > 1 && track ? track.p.label + " · " : "";
          const name = text(who + meta.label + " (" + meta.symbol + ")", { size: 12, weight: "600", color: p.muted, halo: false });
          name.position.set(x + 12, y + 8);
          const value = text("", { size: 17, weight: "700", color: p.ink, halo: false, mono: true, anchorX: 1 });
          value.position.set(x + cardW - 12, y + 6);
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
          art.item.visible = true;
          if (track.label) track.label.position.set(point.x, (mode === "lanes" ? baseY - h : point.y - h / 2) - 6);
        } else {
          if (show.bodies) body(bodyLayer, point.x, point.y, radius, track.color);
          if (track.label) track.label.position.set(point.x, point.y - radius - (mode === "lanes" ? 18 : 6));
        }
      }
      // keep lane labels from colliding with velocity labels in 1-D lanes
      for (const gauge of gauges) {
        const v = values[gauge.key], span = gauge.max - gauge.min || 1;
        const zero = gauge.x + (0 - gauge.min) / span * gauge.w, at = gauge.x + (v - gauge.min) / span * gauge.w;
        gauge.bar.clear().roundRect(gauge.x, gauge.y, gauge.w, 8, 4).fill({ color: p.grid });
        if (Number.isFinite(v)) gauge.bar.rect(Math.min(zero, at), gauge.y, Math.max(1.5, Math.abs(at - zero)), 8).fill({ color: gauge.color });
        const meta = host.data().scene.fields[gauge.key];
        gauge.value.text = meta ? format(v, meta.unit) : format(v);
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
          else parts.push(m.symbol + "=" + format(v, m.unit));
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
      attached.clear(); root.destroy({ children: true });
    }
    return {
      container: root,
      update,
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
    anchor?: "auto" | "bottom" | "center"; sizeMeters?: number; size?: number };
  type IllustratedSpec = { environment?: string; environmentAnchorY?: number; surface?: string; support?: string; connector?: string; bodies?: BodySpec[];
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
      base.attach(body.id, sprite, { facing: body.facing, rotate: body.rotate,
        anchor: body.anchor === "bottom" || body.anchor === "center" ? body.anchor : undefined,
        sizeMeters: Number(body.sizeMeters) > 0 ? Number(body.sizeMeters) : undefined, size: body.size });
    }
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
