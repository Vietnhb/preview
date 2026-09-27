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
};

export function createStageKit(PIXI: typeof PixiNS, app: PixiNS.Application, host: StageHost) {
  type Pt = { x: number; y: number };
  type Bounds = { minX: number; maxX: number; minY: number; maxY: number };
  type Rect = { x: number; y: number; w: number; h: number };
  type Kind = "velocity" | "acceleration" | "force";
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
  const palette = () => palettes[host.data().theme === "DARK" ? "DARK" : "LIGHT"];
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
    anchorX?: number; anchorY?: number; mono?: boolean } = {}) {
    const p = palette();
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
  function standardScene(options: { bodies?: boolean; labels?: boolean; hud?: boolean; strobe?: boolean;
    vectors?: boolean; axes?: boolean; trails?: boolean } = {}) {
    const show = { bodies: true, labels: true, hud: true, strobe: true, vectors: true, axes: true, trails: true, ...options };
    const root = new PIXI.Container();
    root.label = "physlive-standard-scene";
    app.stage.addChild(root);
    const staticLayer = new PIXI.Graphics(), dynamicLayer = new PIXI.Graphics(), bodyLayer = new PIXI.Graphics();
    const staticText = new PIXI.Container(), dynamicText = new PIXI.Container();
    root.addChild(staticLayer, dynamicLayer, bodyLayer, staticText, dynamicText);

    type Track = { p: SceneParticipant; color: string; lane: number;
      screen: (values: Record<string, number>) => Pt | null; path: Array<Pt & { t: number }>; strobe: Array<Pt & { t: number }>;
      monotonic: boolean; label?: PixiNS.Text; vectorLabels: Partial<Record<Kind, PixiNS.Text>>; angleLabel?: PixiNS.Text; wallX?: number };
    let tracks: Track[] = [];
    let cam: Camera | null = null;
    let mode: "plane" | "lanes" | "columns" | "board" = "board";
    let vectorScale: Record<Kind, number> = { velocity: 0, acceleration: 0, force: 0 };
    let laneY: (lane: number) => number = () => 0;
    let columnX: (lane: number) => number = () => 0;
    let hudTime: PixiNS.Text | null = null, hudBadge: PixiNS.Text | null = null;
    let legend: Array<{ dot: PixiNS.Graphics; name: PixiNS.Text; values: PixiNS.Text; track: Track }> = [];
    let lastWidth = 0, lastHeight = 0;

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

    function build() {
      const data = host.data(), scene = data.scene, p = palette();
      const W = app.screen.width, H = app.screen.height;
      lastWidth = W; lastHeight = H;
      staticLayer.clear(); clearText(staticText); clearText(dynamicText);
      legend = []; hudTime = hudBadge = null;
      const frames = data.timeline.frames;
      const spatial = scene.participants.filter(item => item.dims > 0);
      mode = spatial.some(item => item.dims === 2) ? "plane"
        : spatial.length && spatial.every(item => item.vertical) ? "columns" : spatial.length ? "lanes" : "board";
      const legendWidth = show.hud ? Math.min(250, W * 0.34) : 0;
      const top = show.hud ? 58 : 20, bottom = H - (show.axes ? 46 : 20);
      const left = show.axes ? 58 : 20, right = W - 24;
      const view: Rect = { x: left, y: top, w: Math.max(40, right - left), h: Math.max(40, bottom - top) };
      tracks = scene.participants.map(participant => ({ p: participant, color: color(participant.colorIndex), lane: 0,
        screen: () => null, path: [], strobe: [], vectorLabels: {}, monotonic: true }));
      const spatialTracks = tracks.filter(track => track.p.dims > 0);
      spatialTracks.forEach((track, index) => { track.lane = index; });

      // --- world bounds from the full solver timeline (not only initial inputs)
      const bounds: Bounds = { minX: Infinity, maxX: -Infinity, minY: Infinity, maxY: -Infinity };
      const grow = (x: number, y: number) => {
        if (Number.isFinite(x)) { bounds.minX = Math.min(bounds.minX, x); bounds.maxX = Math.max(bounds.maxX, x); }
        if (Number.isFinite(y)) { bounds.minY = Math.min(bounds.minY, y); bounds.maxY = Math.max(bounds.maxY, y); }
      };
      let groundAtZero = false;
      if (mode === "plane") {
        for (const frame of frames) for (const track of spatialTracks) {
          const f = track.p.fields;
          if (track.p.dims === 2) grow(frame.values[f.x!], frame.values[f.y!]);
          else if (track.p.vertical) grow(0, frame.values[f.position!]);
          else grow(frame.values[f.position!], 0);
        }
        if (spatialTracks.some(track => track.p.link)) grow(0, 0);
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
      const padBounds = (minSpan: number) => {
        let spanX = bounds.maxX - bounds.minX, spanY = bounds.maxY - bounds.minY;
        if (!Number.isFinite(spanX)) { bounds.minX = -1; bounds.maxX = 1; spanX = 2; }
        if (!Number.isFinite(spanY)) { bounds.minY = -1; bounds.maxY = 1; spanY = 2; }
        const reference = Math.max(spanX, spanY, minSpan);
        if (spanX < reference * 0.25) { const c = (bounds.minX + bounds.maxX) / 2; bounds.minX = c - reference * 0.125; bounds.maxX = c + reference * 0.125; spanX = reference * 0.25; }
        if (spanY < reference * 0.25) { const c = (bounds.minY + bounds.maxY) / 2; bounds.minY = c - reference * 0.125; bounds.maxY = c + reference * 0.125; spanY = reference * 0.25; }
        bounds.minX -= spanX * 0.08; bounds.maxX += spanX * 0.08;
        bounds.minY -= groundAtZero ? spanY * 0.04 : spanY * 0.1; bounds.maxY += spanY * 0.12;
      };
      if (mode === "plane") {
        padBounds(1e-6);
        cam = camera(view, bounds, true);
      } else if (mode === "lanes") {
        padBounds(1e-6);
        const laneHeight = Math.min(104, view.h / Math.max(1, spatialTracks.length));
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

      for (const track of tracks) {
        const f = track.p.fields;
        if (!c || track.p.dims === 0) continue;
        if (mode === "plane") track.screen = values => {
          const x = track.p.dims === 2 ? values[f.x!] : track.p.vertical ? 0 : values[f.position!];
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
          const step = niceStep(Math.max(vis.maxX - vis.minX, vis.maxY - vis.minY) / (Math.max(view.w, view.h) / 90), 1);
          for (let x = Math.ceil(vis.minX / step) * step; x <= vis.maxX; x += step) {
            const sx = c.toScreen(x, 0).x;
            staticLayer.moveTo(sx, view.y).lineTo(sx, view.y + view.h);
          }
          for (let y = Math.ceil(vis.minY / step) * step; y <= vis.maxY; y += step) {
            const sy = c.toScreen(0, y).y;
            staticLayer.moveTo(view.x, sy).lineTo(view.x + view.w, sy);
          }
          staticLayer.stroke({ color: p.grid, width: 1 });
          const axisY = Math.min(Math.max(0, vis.minY), vis.maxY), axisX = Math.min(Math.max(0, vis.minX), vis.maxX);
          const origin = c.toScreen(axisX, axisY);
          arrow(staticLayer, view.x, origin.y, view.x + view.w + 10, origin.y, { color: p.axis, width: 1.4, head: 8 });
          arrow(staticLayer, origin.x, view.y + view.h, origin.x, view.y - 10, { color: p.axis, width: 1.4, head: 8 });
          for (let x = Math.ceil(vis.minX / step) * step; x <= vis.maxX - step * 0.3; x += step) {
            const sx = c.toScreen(x, 0).x;
            staticLayer.moveTo(sx, origin.y - 4).lineTo(sx, origin.y + 4);
            if (Math.abs(x) > step * 1e-6) staticText.addChild(Object.assign(text(tickLabel(x, step), { size: 10.5, color: p.muted, anchorX: 0.5 }), { x: sx, y: Math.min(origin.y + 6, view.y + view.h + 6) }));
          }
          for (let y = Math.ceil(vis.minY / step) * step; y <= vis.maxY - step * 0.3; y += step) {
            const sy = c.toScreen(0, y).y;
            staticLayer.moveTo(origin.x - 4, sy).lineTo(origin.x + 4, sy);
            if (Math.abs(y) > step * 1e-6) staticText.addChild(Object.assign(text(tickLabel(y, step), { size: 10.5, color: p.muted, anchorX: 1, anchorY: 0.5 }), { x: Math.max(origin.x - 7, view.x - 8), y: sy }));
          }
          staticLayer.stroke({ color: p.axis, width: 1.2 });
          staticText.addChild(Object.assign(text("O", { size: 11, color: p.muted, anchorX: 1, weight: "600" }), { x: origin.x - 5, y: origin.y + 4 }));
          staticText.addChild(Object.assign(text("x (m)", { size: 11.5, color: p.axis, anchorX: 1, anchorY: 1, weight: "600" }), { x: view.x + view.w + 8, y: origin.y - 6 }));
          staticText.addChild(Object.assign(text("y (m)", { size: 11.5, color: p.axis, anchorX: 0, anchorY: 0.5, weight: "600" }), { x: origin.x + 10, y: view.y - 8 }));
          if (groundAtZero) {
            const g0 = c.toScreen(0, 0).y;
            staticLayer.rect(view.x, g0, view.w, Math.max(0, view.y + view.h - g0)).fill({ color: p.groundFill, alpha: 0.55 });
            hatch(staticLayer, view.x, view.x + view.w, g0, 1, p.ground);
          }
          for (const track of spatialTracks) if (track.p.link) {
            const pivot = c.toScreen(0, 0);
            staticLayer.rect(pivot.x - 34, pivot.y - 8, 68, 8).fill({ color: p.groundFill });
            hatch(staticLayer, pivot.x - 34, pivot.x + 34, pivot.y - 8, -1, p.ground);
            dashed(staticLayer, pivot.x, pivot.y, pivot.x, pivot.y + track.p.link.radius * c.sy * 1.08, 5, 5).stroke({ color: p.faint, width: 1.2 });
          }
        } else if (mode === "lanes") {
          const step = niceStep(vis.maxX - vis.minX, Math.max(3, Math.round(view.w / 90)));
          const rulerY = view.y + view.h;
          for (let x = Math.ceil(vis.minX / step) * step; x <= vis.maxX; x += step) {
            const sx = c.toScreen(x, 0).x;
            staticLayer.moveTo(sx, view.y + 8).lineTo(sx, rulerY);
          }
          staticLayer.stroke({ color: p.grid, width: 1 });
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
            staticLayer.roundRect(view.x - 4, roadY, view.w + 8, 5, 2.5).fill({ color: p.groundFill, alpha: 0.9 });
            if (track.wallX !== undefined) {
              const wx = c.toScreen(track.wallX, 0).x;
              staticLayer.rect(wx - 10, y - 24, 10, 42).fill({ color: p.groundFill });
              staticLayer.moveTo(wx, y - 24).lineTo(wx, y + 18).stroke({ color: p.ground, width: 2 });
              for (let hy = y - 20; hy < y + 18; hy += 8) staticLayer.moveTo(wx, hy).lineTo(wx - 7, hy + 7);
              staticLayer.stroke({ color: p.ground, width: 1, alpha: 0.7 });
              const eq = c.toScreen(0, 0).x;
              dashed(staticLayer, eq, y - 26, eq, y + 20, 4, 4).stroke({ color: p.faint, width: 1.2 });
              staticText.addChild(Object.assign(text("VTCB", { size: 10, color: p.muted, anchorX: 0.5, anchorY: 1 }), { x: eq, y: y - 27 }));
            }
          }
        } else if (mode === "columns") {
          const step = niceStep(vis.maxY - vis.minY, Math.max(3, Math.round(view.h / 70)));
          for (let y = Math.ceil(vis.minY / step) * step; y <= vis.maxY; y += step) {
            const sy = c.toScreen(0, y).y;
            staticLayer.moveTo(view.x, sy).lineTo(view.x + view.w, sy);
          }
          staticLayer.stroke({ color: p.grid, width: 1 });
          arrow(staticLayer, view.x, view.y + view.h + 8, view.x, view.y - 12, { color: p.axis, width: 1.4, head: 8 });
          for (let y = Math.ceil(vis.minY / step) * step; y <= vis.maxY - step * 0.2; y += step) {
            const sy = c.toScreen(0, y).y;
            staticLayer.moveTo(view.x - 5, sy).lineTo(view.x + 5, sy);
            staticText.addChild(Object.assign(text(tickLabel(y, step), { size: 10.5, color: p.muted, anchorX: 1, anchorY: 0.5 }), { x: view.x - 8, y: sy }));
          }
          staticLayer.stroke({ color: p.axis, width: 1.2 });
          const axisName = spatialTracks.some(track => scene.fields[track.p.fields.position!]?.quantity === "height") ? "h (m)" : "y (m)";
          staticText.addChild(Object.assign(text(axisName, { size: 11.5, color: p.axis, anchorX: 0, anchorY: 0.5, weight: "600" }), { x: view.x + 10, y: view.y - 8 }));
          if (groundAtZero) {
            const g0 = c.toScreen(0, 0).y;
            staticLayer.rect(view.x, g0, view.w, Math.max(0, H - g0)).fill({ color: p.groundFill, alpha: 0.55 });
            hatch(staticLayer, view.x, view.x + view.w, g0, 1, p.ground);
          }
          for (const track of spatialTracks) {
            const x = columnX(track.lane);
            dashed(staticLayer, x, view.y, x, view.y + view.h, 3, 6).stroke({ color: p.gridMajor, width: 1 });
          }
        }
      }
      // --- full trajectories (faint), labels and vector labels
      for (const track of tracks) {
        if (track.p.dims === 0) continue;
        if (show.trails && mode === "plane" && track.path.length > 1) {
          for (let i = 1; i < track.path.length; i += 2) {
            staticLayer.moveTo(track.path[i - 1].x, track.path[i - 1].y).lineTo(track.path[i].x, track.path[i].y);
          }
          staticLayer.stroke({ color: track.color, width: 1.5, alpha: 0.35 });
        }
        if (show.labels) {
          track.label = text(track.p.label, { size: 12, weight: "600", color: p.ink, anchorX: 0.5, anchorY: 1 });
          dynamicText.addChild(track.label);
        }
        if (show.vectors) for (const kind of ["velocity", "acceleration", "force"] as Kind[]) if (vectorScale[kind] > 0) {
          const label = text("", { size: 11, weight: "600", color: p[kind], anchorX: 0.5, anchorY: 1 });
          track.vectorLabels[kind] = label; dynamicText.addChild(label);
        }
        if (track.p.link && track.p.fields.angle) {
          track.angleLabel = text("", { size: 11, weight: "600", color: p.muted, anchorX: 0.5, anchorY: 0 });
          dynamicText.addChild(track.angleLabel);
        }
      }
      // --- HUD: time, legend with live readouts, verification badge
      if (show.hud) {
        hudTime = text("t = 0.00 s", { size: 15, weight: "700", mono: true, color: p.ink });
        hudTime.position.set(16, 14);
        dynamicText.addChild(hudTime);
        let y = 12;
        for (const track of tracks) {
          const dot = new PIXI.Graphics().circle(0, 0, 5).fill({ color: track.color });
          const name = text(track.p.label, { size: 12, weight: "600", anchorX: 1 });
          const values = text("", { size: 11, color: p.muted, anchorX: 1, mono: true });
          dot.position.set(W - 16, y + 8);
          name.position.set(W - 27, y);
          values.position.set(W - 16, y + 16);
          dynamicText.addChild(dot, name, values);
          legend.push({ dot, name, values, track });
          y += 36;
          if (y > H * 0.45) break;
        }
        const status = data.verificationStatus;
        const verified = status === "VERIFIED_ANALYTICAL" || status === "VERIFIED_NUMERICAL";
        hudBadge = text(verified ? "✓ Dữ liệu vật lý đã được backend xác minh" : status === "PENDING" ? "Đang tính lại…" : "⚠ Minh họa — chưa xác minh vật lý",
          { size: 10.5, color: verified ? p.velocity : p.acceleration, anchorX: 1, anchorY: 1, weight: "600" });
        hudBadge.anchor.set(0, 0);
        hudBadge.position.set(16, 36);
        dynamicText.addChild(hudBadge);
      }
    }

    function screenOf(id: string, values = host.sample(0)) {
      const track = tracks.find(item => item.p.id === id);
      return track ? track.screen(values) : null;
    }

    function update(frame: { t: number; fields: Record<string, number> }) {
      if (app.screen.width !== lastWidth || app.screen.height !== lastHeight) build();
      const p = palette(), values = frame.fields;
      dynamicLayer.clear(); bodyLayer.clear();
      for (const track of tracks) {
        if (track.p.dims === 0 || !cam) continue;
        const point = track.screen(values);
        if (!point) continue;
        // travelled path + strobe marks (textbook multiple-exposure picture)
        if (show.trails && track.path.length > 1 && (mode === "plane" || track.monotonic)) {
          let started = false;
          for (const q of track.path) {
            if (q.t > frame.t) break;
            if (!started) { dynamicLayer.moveTo(q.x, q.y); started = true; } else dynamicLayer.lineTo(q.x, q.y);
          }
          if (started) dynamicLayer.lineTo(point.x, point.y).stroke({ color: track.color, width: mode === "plane" ? 2.6 : 3, alpha: 0.85, cap: "round", join: "round" });
        }
        if (show.strobe) for (const q of track.strobe) {
          if (q.t > frame.t + 1e-9) break;
          dynamicLayer.circle(q.x, q.y, 3.6).fill({ color: track.color, alpha: 0.35 }).stroke({ color: track.color, width: 1.2, alpha: 0.9 });
        }
        if (track.p.link) {
          const pivot = cam.toScreen(0, 0);
          dynamicLayer.moveTo(pivot.x, pivot.y).lineTo(point.x, point.y).stroke({ color: p.axis, width: 2 });
          dynamicLayer.circle(pivot.x, pivot.y, 4).fill({ color: p.axis });
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
          spring(dynamicLayer, wx, point.x - 12, point.y, 7, p.spring);
        }
        // vectors (shared scale per kind so learners can compare magnitudes)
        const radius = mode === "plane" ? 10 : 12;
        let offset = 0;
        for (const vector of vectorsOf(track, values)) {
          const scale = vectorScale[vector.kind], label = track.vectorLabels[vector.kind];
          if (!show.vectors || !(scale > 0)) continue;
          const dx = vector.dx * scale, dy = -vector.dy * scale;
          const along = mode === "lanes" ? { x: 0, y: offset } : mode === "columns" ? { x: offset, y: 0 } : { x: 0, y: 0 };
          const x0 = point.x + along.x, y0 = point.y + along.y;
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
        if (show.bodies) body(bodyLayer, point.x, point.y, radius, track.color);
        if (track.label) track.label.position.set(point.x, point.y - radius - (mode === "lanes" ? 18 : 6));
      }
      // keep lane labels from colliding with velocity labels in 1-D lanes
      if (hudTime) hudTime.text = "t = " + frame.t.toFixed(2) + " s";
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
    }
    build();
    return {
      container: root,
      update,
      resize() { build(); },
      setData() { build(); },
      screenOf,
      camera: () => cam,
      mode: () => mode,
      dispose() { root.destroy({ children: true }); },
    };
  }

  return Object.freeze({
    FONT, palette, color, niceStep, format, text, camera, arrow, dashed, hatch, spring, body, fieldRanges,
    scene: () => host.data().scene,
    participants: () => host.data().scene.participants,
    standardScene,
  });
}
