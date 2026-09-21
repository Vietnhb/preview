import type { CanvasPalette, OverlayState, Point } from "../components/simulation-canvas/model";
import { canvasAssetRegistry } from "../simulation-assets/AssetRegistry";
import { probeScalarField, sampleScalarField, scalarFieldFor, seriesFor, type RuntimeData } from "../simulation-runtime/SimulationData";
import type { RuntimeFrame } from "../simulation-runtime/SimulationRuntime";
import { BindingResolver } from "../simulation-scene/BindingResolver";
import { flattenSceneGraph, type SceneGraph, type SceneNode } from "../simulation-scene/SceneGraph";
import { primitiveRenderers } from "./PrimitiveRendererRegistry";
import { drawVectorScene } from './VectorSceneRenderer';

type LayoutMode = "dataPlane" | "lanes" | "horizontalTrack" | "projectileRange" | "collisionTrack" | "springBench" | "circuitBoard" | "world";

type LayoutContext = {
  mode: LayoutMode;
  width: number;
  height: number;
  left: number;
  right: number;
  top: number;
  baseline: number;
  xMin: number;
  xMax: number;
  yMax: number;
  mapX: (value: number) => number;
  mapPoint: (value: number, y: number, lane: number) => Point;
};

type TrajectoryLayer = {
  canvas: HTMLCanvasElement;
  ctx: CanvasRenderingContext2D;
  builtIndex: number;
  lastX: number;
  lastY: number;
};

export type CanvasRendererCache = {
  staticKey: string;
  staticLayer: HTMLCanvasElement | null;
  trajectories: Map<string, TrajectoryLayer>;
  layoutKey: string;
  layout: LayoutContext | null;
  resolverData: RuntimeData | null;
  resolver: BindingResolver | null;
};

export type CanvasRenderFrame = {
  ctx: CanvasRenderingContext2D;
  width: number;
  height: number;
  graph: SceneGraph;
  data: RuntimeData;
  runtime: RuntimeFrame;
  overlays: OverlayState;
  palette: CanvasPalette;
  cache: CanvasRendererCache;
};

export function createCanvasRendererCache(): CanvasRendererCache {
  return { staticKey: "", staticLayer: null, trajectories: new Map(), layoutKey: "", layout: null, resolverData: null, resolver: null };
}

const flattenedGraphCache = new WeakMap<SceneGraph, SceneNode[]>();

function nodesForGraph(graph: SceneGraph): SceneNode[] {
  const cached = flattenedGraphCache.get(graph);
  if (cached) return cached;
  const nodes = flattenSceneGraph(graph.nodes);
  flattenedGraphCache.set(graph, nodes);
  return nodes;
}

function propertyString(node: SceneNode, key: string, fallback = ""): string {
  const value = node.properties[key];
  return typeof value === "string" ? value : fallback;
}

function propertyNumber(node: SceneNode, key: string, fallback = 0): number {
  const value = node.properties[key];
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function nodeColor(node: SceneNode, palette: CanvasPalette, fallback = "#38bdf8"): string {
  const explicit = propertyString(node, "color");
  if (explicit) return palette[explicit as keyof CanvasPalette] ?? explicit;
  const styled = node.style.color;
  if (typeof styled === "string") return palette[styled as keyof CanvasPalette] ?? styled;
  return fallback;
}

function styleNumber(node: SceneNode, key: string, fallback = 0): number {
  const value = node.style[key];
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function sourceName(value: unknown): string {
  if (typeof value === "string") return value;
  if (typeof value === "object" && value !== null && "key" in value) return String(value.key ?? "");
  return "";
}

function layoutMode(value: unknown): LayoutMode {
  return value === "dataPlane" || value === "lanes" || value === "projectileRange" || value === "collisionTrack" || value === "springBench" || value === "circuitBoard" || value === "world"
    ? value
    : "dataPlane";
}

function seriesRange(data: RuntimeData, source: unknown, fallback: [number, number]): [number, number] {
  const range = data.ranges.get(sourceName(source));
  const min = range?.[0] ?? Number.POSITIVE_INFINITY;
  const max = range?.[1] ?? Number.NEGATIVE_INFINITY;
  if (!Number.isFinite(min) || !Number.isFinite(max)) return fallback;
  if (Math.abs(max - min) < Number.EPSILON) return [min - 1, max + 1];
  const padding = (max - min) * .12;
  return [min - padding, max + padding];
}

function mergeRange(current: [number, number], next: [number, number]): [number, number] {
  return [Math.min(current[0], next[0]), Math.max(current[1], next[1])];
}

function createLayout(nodes: SceneNode[], data: RuntimeData, width: number, height: number): LayoutContext {
  const environment = nodes.find(node => node.type === "environment");
  const layoutNode = nodes.find(node => node.properties.layout);
  const mode = layoutMode(environment?.properties.layout ?? layoutNode?.properties.layout ?? "dataPlane");
  let xRange: [number, number] = mode === "collisionTrack" ? [0, 8] : [-1, 1];
  let yRange: [number, number] = mode === "projectileRange" ? [0, 10] : [-1, 1];
  for (const node of nodes) {
    if (node.type !== "body" && node.type !== "trajectory") continue;
    xRange = mergeRange(xRange, seriesRange(data, node.transform.x, xRange));
    if (mode === "projectileRange" || mode === "dataPlane") yRange = mergeRange(yRange, seriesRange(data, node.transform.y, yRange));
  }
  for (const node of nodes) {
    if (node.type !== "waveField") continue;
    const fieldId = propertyString(node, "field");
    const field = scalarFieldFor(data, fieldId);
    if (!field) continue;
    xRange = mergeRange(xRange, [field.x[0] ?? xRange[0], field.x.at(-1) ?? xRange[1]]);
  }
  if (mode === "projectileRange") yRange = [0, Math.max(1, yRange[1])];
  const left = mode === "projectileRange" ? 90 : mode === "collisionTrack" || mode === "lanes" ? 92 : mode === "springBench" ? 190 : 88;
  const right = mode === "projectileRange" ? width - 68 : mode === "collisionTrack" ? width - 92 : mode === "springBench" ? width - 90 : width - 105;
  const top = mode === "projectileRange" || mode === "dataPlane" ? 55 : 0;
  const baseline = mode === "projectileRange" || mode === "dataPlane" ? height - 64 : mode === "collisionTrack" || mode === "lanes" ? height * .54 : mode === "springBench" ? height * .67 : height * .65;
  const mapX = (value: number) => mode === "world"
    ? width * .5 + value * 40
    : left + ((value - xRange[0]) / Math.max(Number.EPSILON, xRange[1] - xRange[0])) * (right - left);
  return {
    mode, width, height, left, right, top, baseline,
    xMin: xRange[0], xMax: xRange[1], yMax: yRange[1], mapX,
    mapPoint: (value, y, lane) => {
      if (mode === "projectileRange") return { x: mapX(value), y: baseline - (Math.max(0, y) / Math.max(1, yRange[1])) * (baseline - top) };
      if (mode === "dataPlane") return { x: mapX(value), y: baseline - ((y - yRange[0]) / Math.max(Number.EPSILON, yRange[1] - yRange[0])) * (baseline - top) };
      if (mode === "world") return { x: mapX(value), y: height * .5 - y * 40 };
      if (mode === "collisionTrack") return { x: mapX(value), y: baseline + lane * 56 - 17 };
      if (mode === "lanes") return { x: mapX(value), y: baseline + lane * 56 - 17 };
      if (mode === "springBench") return { x: left + 130 + ((value - xRange[0]) / Math.max(Number.EPSILON, xRange[1] - xRange[0])) * (right - left - 130), y: baseline - 29 };
      return { x: mapX(value), y: baseline - 17 - lane * 58 };
    },
  };
}

function drawBackground(ctx: CanvasRenderingContext2D, width: number, height: number, palette: CanvasPalette) {
  const gradient = ctx.createRadialGradient(width * .58, height * .4, 30, width * .5, height * .5, Math.max(width, height));
  gradient.addColorStop(0, palette.backgroundTop);
  gradient.addColorStop(1, palette.backgroundBottom);
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, width, height);
}

function drawGrid(ctx: CanvasRenderingContext2D, width: number, height: number, palette: CanvasPalette) {
  ctx.save();
  ctx.strokeStyle = palette.grid;
  ctx.lineWidth = 1;
  for (let x = 0; x <= width; x += 40) {
    ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, height); ctx.stroke();
  }
  for (let y = 0; y <= height; y += 40) {
    ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(width, y); ctx.stroke();
  }
  ctx.restore();
}

type EnvironmentPainter = (ctx: CanvasRenderingContext2D, width: number, height: number, baseline: number, palette: CanvasPalette) => void;

const environmentRegistry: Record<string, EnvironmentPainter> = {
  "track.engineering": (ctx, width, _height, y, palette) => {
    const rail = ctx.createLinearGradient(0, y, 0, y + 70);
    rail.addColorStop(0, "#1e293b"); rail.addColorStop(1, "#090d16");
    ctx.fillStyle = rail; ctx.fillRect(0, y, width, 70);
    ctx.strokeStyle = palette.cyan; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(width, y); ctx.stroke();
    ctx.strokeStyle = "rgba(148,163,184,.28)"; ctx.setLineDash([14, 14]);
    ctx.beginPath(); ctx.moveTo(0, y + 36); ctx.lineTo(width, y + 36); ctx.stroke(); ctx.setLineDash([]);
  },
  "road.highway": (ctx, width, _height, y) => {
    const road = ctx.createLinearGradient(0, y, 0, y + 86);
    road.addColorStop(0, "#182236"); road.addColorStop(1, "#070a11");
    ctx.fillStyle = road; ctx.fillRect(0, y, width, 86);
    ctx.fillStyle = "#334155"; ctx.fillRect(0, y, width, 3);
    ctx.strokeStyle = "#eab308"; ctx.lineWidth = 3; ctx.setLineDash([22, 18]);
    ctx.beginPath(); ctx.moveTo(0, y + 42); ctx.lineTo(width, y + 42); ctx.stroke(); ctx.setLineDash([]);
  },
  "range.projectile": (ctx, width, height, y) => {
    const turf = ctx.createLinearGradient(0, y, 0, height);
    turf.addColorStop(0, "#065f46"); turf.addColorStop(1, "#022c22");
    ctx.fillStyle = turf; ctx.fillRect(0, y, width, height - y);
    ctx.strokeStyle = "#10b981"; ctx.lineWidth = 2.5; ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(width, y); ctx.stroke();
  },
  "track.collision": (ctx, width, _height, y, palette) => {
    ctx.fillStyle = "rgba(15,23,42,.82)"; ctx.fillRect(42, y - 30, width - 84, 104);
    ctx.strokeStyle = palette.gridStrong; ctx.lineWidth = 1; ctx.strokeRect(42, y - 30, width - 84, 104);
    for (const offset of [0, 56]) {
      ctx.strokeStyle = offset ? "rgba(249,115,22,.4)" : "rgba(56,189,248,.4)";
      ctx.lineWidth = 3; ctx.beginPath(); ctx.moveTo(62, y + offset); ctx.lineTo(width - 62, y + offset); ctx.stroke();
    }
  },
  "bench.spring": (ctx, width, _height, y, palette) => {
    ctx.fillStyle = "rgba(30,41,59,.9)"; ctx.fillRect(54, y, width - 108, 34);
    ctx.strokeStyle = palette.gridStrong; ctx.strokeRect(54, y, width - 108, 34);
    ctx.fillStyle = "#64748b"; ctx.fillRect(72, y - 125, 20, 125);
    for (let row = y - 116; row < y - 8; row += 16) {
      ctx.strokeStyle = "rgba(203,213,225,.22)"; ctx.beginPath(); ctx.moveTo(72, row); ctx.lineTo(92, row + 12); ctx.stroke();
    }
  },
  "board.circuit": (ctx, width, height, _y, palette) => {
    const board = ctx.createLinearGradient(55, 45, width - 55, height - 45);
    board.addColorStop(0, "rgba(15,45,55,.92)"); board.addColorStop(1, "rgba(6,24,31,.96)");
    ctx.fillStyle = board; ctx.beginPath(); ctx.roundRect(55, 42, width - 110, height - 84, 22); ctx.fill();
    ctx.strokeStyle = "rgba(45,212,191,.22)"; ctx.lineWidth = 1.5; ctx.stroke();
    ctx.fillStyle = palette.muted; ctx.font = "700 10px ui-monospace, Consolas, monospace"; ctx.fillText("PHYSLIVE · CIRCUIT LAB", 78, 70);
  },
  "room.thermodynamics": (ctx, width, height, y, palette) => {
    ctx.fillStyle = "rgba(53,32,18,.72)"; ctx.fillRect(0, 0, width, height);
    ctx.fillStyle = "rgba(245,158,11,.12)"; ctx.fillRect(54, 48, width - 108, Math.max(100, y - 82));
    ctx.strokeStyle = "rgba(251,191,36,.55)"; ctx.lineWidth = 2; ctx.strokeRect(54, 48, width - 108, Math.max(100, y - 82));
    ctx.fillStyle = palette.muted; ctx.font = "700 10px ui-monospace, Consolas, monospace"; ctx.fillText("IDEAL GAS / THERMODYNAMICS", 72, 72);
  },
  "room.acoustics": (ctx, width, height, y, palette) => {
    ctx.fillStyle = "rgba(15,35,52,.78)"; ctx.fillRect(0, 0, width, height);
    ctx.strokeStyle = "rgba(56,189,248,.18)"; ctx.lineWidth = 1;
    for (let x = 70; x < width - 50; x += 70) { ctx.beginPath(); ctx.arc(x, y * .42, 18, 0, Math.PI * 2); ctx.stroke(); }
    ctx.fillStyle = palette.muted; ctx.font = "700 10px ui-monospace, Consolas, monospace"; ctx.fillText("ACOUSTIC PRESSURE FIELD", 72, 72);
  },
  "wave.tank": (ctx, width, height, y, palette) => {
    const waterTop = Math.min(height - 80, Math.max(110, y - 30));
    const water = ctx.createLinearGradient(0, waterTop, 0, height);
    water.addColorStop(0, "rgba(14,165,233,.26)"); water.addColorStop(1, "rgba(8,47,73,.82)");
    ctx.fillStyle = water; ctx.fillRect(0, waterTop, width, height - waterTop);
    ctx.strokeStyle = "rgba(125,211,252,.8)"; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(0, waterTop); ctx.lineTo(width, waterTop); ctx.stroke();
    ctx.strokeStyle = "rgba(56,189,248,.22)"; ctx.lineWidth = 1;
    for (let row = waterTop + 22; row < height; row += 26) { ctx.beginPath(); ctx.moveTo(54, row); ctx.lineTo(width - 54, row); ctx.stroke(); }
    ctx.fillStyle = palette.muted; ctx.font = "700 10px ui-monospace, Consolas, monospace"; ctx.fillText("WATER-SURFACE WAVE TANK", 72, 72);
  },
  "optical.bench": (ctx, width, _height, y, palette) => {
    ctx.fillStyle = "rgba(18,24,37,.82)"; ctx.fillRect(0, y - 26, width, 70);
    ctx.strokeStyle = "rgba(251,191,36,.7)"; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(42, y); ctx.lineTo(width - 42, y); ctx.stroke();
    ctx.fillStyle = palette.muted; ctx.font = "700 10px ui-monospace, Consolas, monospace"; ctx.fillText("PARAXIAL OPTICAL BENCH", 72, 72);
  },
  "lab.radiation": (ctx, width, height, _y, palette) => {
    ctx.fillStyle = "rgba(26,35,28,.82)"; ctx.fillRect(0, 0, width, height);
    ctx.strokeStyle = "rgba(74,222,128,.3)"; ctx.lineWidth = 2; ctx.strokeRect(54, 48, width - 108, height - 110);
    ctx.fillStyle = palette.muted; ctx.font = "700 10px ui-monospace, Consolas, monospace"; ctx.fillText("RADIATION COUNTING LAB", 72, 72);
  },
  "lab.measurement": (ctx, width, height, _y, palette) => {
    ctx.fillStyle = "rgba(30,41,59,.82)"; ctx.fillRect(0, 0, width, height);
    ctx.strokeStyle = "rgba(56,189,248,.35)"; ctx.lineWidth = 2; ctx.strokeRect(54, 48, width - 108, height - 110);
    ctx.fillStyle = palette.muted; ctx.font = "700 10px ui-monospace, Consolas, monospace"; ctx.fillText("MEASUREMENT / UNCERTAINTY LAB", 72, 72);
  },
};

function drawEnvironment(ctx: CanvasRenderingContext2D, node: SceneNode, layout: LayoutContext, palette: CanvasPalette) {
  const environment = propertyString(node, "environment", "track.engineering");
  (environmentRegistry[environment] ?? environmentRegistry["track.engineering"])(ctx, layout.width, layout.height, layout.baseline, palette);
}

function drawRuler(ctx: CanvasRenderingContext2D, layout: LayoutContext, palette: CanvasPalette) {
  if (layout.mode === "world" || layout.mode === "circuitBoard") return;
  const count = layout.width < 620 ? 4 : 7;
  const y = layout.mode === "projectileRange" || layout.mode === "horizontalTrack" ? layout.baseline : layout.baseline + 88;
  const left = layout.mode === "springBench" ? layout.left + 130 : layout.left;
  ctx.save(); ctx.font = "600 10px ui-monospace, Consolas, monospace"; ctx.textAlign = "center";
  for (let index = 0; index < count; index++) {
    const ratio = index / (count - 1);
    const x = left + ratio * (layout.right - left);
    const value = layout.xMin + ratio * (layout.xMax - layout.xMin);
    ctx.strokeStyle = index === 0 ? palette.cyan : palette.gridStrong; ctx.lineWidth = index === 0 ? 2 : 1;
    ctx.beginPath(); ctx.moveTo(x, y - 9); ctx.lineTo(x, y + 10); ctx.stroke();
    ctx.fillStyle = index === 0 ? palette.cyan : palette.muted; ctx.fillText(`${value.toFixed(1)} m`, x, y + 25);
  }
  ctx.restore();
}

function drawBadge(ctx: CanvasRenderingContext2D, text: string, x: number, y: number, color: string) {
  ctx.save(); ctx.font = "700 11px ui-monospace, SFMono-Regular, Consolas, monospace";
  const width = ctx.measureText(text).width + 14;
  ctx.fillStyle = "rgba(8,15,30,.9)"; ctx.strokeStyle = color; ctx.lineWidth = 1.1;
  ctx.beginPath(); ctx.roundRect(x - width / 2, y - 15, width, 22, 6); ctx.fill(); ctx.stroke();
  ctx.fillStyle = "#ffffff"; ctx.textAlign = "center"; ctx.textBaseline = "middle"; ctx.fillText(text, x, y - 4); ctx.restore();
}

function drawVector(ctx: CanvasRenderingContext2D, from: Point, delta: Point, color: string, label: string) {
  if (Math.hypot(delta.x, delta.y) < 2) return;
  const to = { x: from.x + delta.x, y: from.y + delta.y };
  const angle = Math.atan2(delta.y, delta.x);
  const head = 10;
  ctx.save(); ctx.strokeStyle = color; ctx.fillStyle = color; ctx.lineWidth = 2.8; ctx.lineCap = "round";
  ctx.beginPath(); ctx.moveTo(from.x, from.y); ctx.lineTo(to.x, to.y); ctx.stroke();
  ctx.beginPath(); ctx.moveTo(to.x, to.y);
  ctx.lineTo(to.x - head * Math.cos(angle - Math.PI / 6), to.y - head * Math.sin(angle - Math.PI / 6));
  ctx.lineTo(to.x - head * Math.cos(angle + Math.PI / 6), to.y - head * Math.sin(angle + Math.PI / 6));
  ctx.closePath(); ctx.fill(); ctx.restore();
  drawBadge(ctx, label, (from.x + to.x) / 2, Math.min(from.y, to.y) - 16, color);
}

function actorNode(nodes: SceneNode[], id: string): SceneNode | undefined {
  return nodes.find(node => node.type === "body" && node.id === id);
}

function vectorValue(resolver: BindingResolver, node: SceneNode, key: string, time: number, index: number): number {
  const value = node.properties[key];
  return typeof value === "undefined" ? 0 : resolver.resolve(value as never, time, index);
}

function drawCircuit(ctx: CanvasRenderingContext2D, frame: CanvasRenderFrame, node: SceneNode, resolver: BindingResolver) {
  const { width, height, palette } = frame;
  const voltage = resolver.resolve(node.properties.voltage as never, frame.runtime.time, frame.runtime.index);
  const current = resolver.resolve(node.properties.current as never, frame.runtime.time, frame.runtime.index);
  const maxVoltage = Math.max(.001, frame.data.maxAbs.get(sourceName(node.properties.voltage)) ?? 0);
  const fill = Math.min(1, Math.abs(voltage) / maxVoltage);
  const left = Math.max(105, width * .17); const right = Math.min(width - 105, width * .83);
  const top = Math.max(92, height * .28); const bottom = Math.min(height - 75, height * .72);
  const resistorLeft = width * .4; const resistorRight = width * .6;
  ctx.save(); ctx.strokeStyle = "#5eead4"; ctx.lineWidth = 4; ctx.lineCap = "round"; ctx.lineJoin = "round";
  ctx.beginPath(); ctx.moveTo(left, top); ctx.lineTo(resistorLeft, top);
  for (let i = 0; i <= 8; i++) ctx.lineTo(resistorLeft + (resistorRight - resistorLeft) * i / 8, top + (i % 2 ? -15 : 15));
  ctx.lineTo(right, top); ctx.lineTo(right, bottom); ctx.lineTo(left, bottom); ctx.lineTo(left, top); ctx.stroke(); ctx.restore();
  ctx.strokeStyle = "#f8fafc"; ctx.lineWidth = 3;
  ctx.beginPath(); ctx.moveTo(left - 28, height * .47); ctx.lineTo(left + 28, height * .47); ctx.moveTo(left - 16, height * .53); ctx.lineTo(left + 16, height * .53); ctx.stroke();
  ctx.fillStyle = palette.text; ctx.font = "700 12px ui-monospace, Consolas, monospace"; ctx.textAlign = "center";
  ctx.fillText("SOURCE", left, height * .59); ctx.fillText("R", width * .5, top - 28);
  ctx.strokeStyle = "#f8fafc"; ctx.lineWidth = 4;
  ctx.beginPath(); ctx.moveTo(right - 34, height * .47); ctx.lineTo(right + 34, height * .47); ctx.moveTo(right - 34, height * .53); ctx.lineTo(right + 34, height * .53); ctx.stroke();
  const effects = node.properties.effects as unknown[] | undefined;
  if (effects?.includes("circuit.capacitor-glow")) {
    const glow = ctx.createLinearGradient(right - 30, height * .53, right + 30, height * .47);
    glow.addColorStop(0, "rgba(56,189,248,.12)"); glow.addColorStop(1, `rgba(56,189,248,${.15 + fill * .7})`);
    ctx.fillStyle = glow; ctx.fillRect(right - 29, height * .53 - height * .06 * fill, 58, height * .06 * fill);
  }
  ctx.fillStyle = palette.cyan; ctx.fillText(`Uc = ${voltage.toFixed(2)} V`, right, height * .62);
  if (frame.overlays.velocity && effects?.includes("circuit.current-flow") && Math.abs(current) > .000001) {
    const phase = (frame.runtime.time * Math.max(.2, Math.abs(current) * 10)) % 1;
    for (let i = 0; i < 7; i++) {
      const x = left + ((phase + i / 7) % 1) * (right - left);
      ctx.fillStyle = palette.amber; ctx.beginPath(); ctx.arc(x, top, 4, 0, Math.PI * 2); ctx.fill();
    }
    drawBadge(ctx, `I = ${current.toFixed(4)} A`, width * .5, top + 54, palette.amber);
  }
}

function drawWaveField(ctx: CanvasRenderingContext2D, frame: CanvasRenderFrame, node: SceneNode, layout: LayoutContext, resolver: BindingResolver) {
  const field = scalarFieldFor(frame.data, propertyString(node, "field"));
  if (!field) return;
  const exaggeration = Math.max(0.01, propertyNumber(node, "displayExaggeration", 1));
  if (field.physicalDimension === 2 && field.y) {
    const amplitude = Math.max(1e-9, maxAbsoluteValue(field.values));
    const x0 = field.x[0] ?? 0; const x1 = field.x.at(-1) ?? x0 + 1;
    const y0 = field.y[0] ?? 0; const y1 = field.y.at(-1) ?? y0 + 1;
    const panelTop = layout.top + 22; const panelHeight = Math.max(80, layout.height * .62);
    const cellWidth = (layout.right - layout.left) / Math.max(1, field.x.length - 1);
    const cellHeight = panelHeight / Math.max(1, field.y.length - 1);
    for (let xi = 0; xi < field.x.length; xi++) {
      const x = field.x[xi] ?? x0;
      for (let yi = 0; yi < field.y.length; yi++) {
        const y = field.y[yi] ?? y0;
        const value = sampleScalarField(field, x, frame.runtime.time, y) ?? 0;
        const normalized = Math.max(-1, Math.min(1, value / amplitude));
        const hue = normalized >= 0 ? 195 - normalized * 35 : 15 + normalized * 15;
        const lightness = 42 + Math.abs(normalized) * 18;
        const px = layout.left + ((x - x0) / Math.max(1e-12, x1 - x0)) * (layout.right - layout.left);
        const py = panelTop + (1 - (y - y0) / Math.max(1e-12, y1 - y0)) * panelHeight;
        ctx.fillStyle = `hsl(${hue} 82% ${lightness}%)`;
        ctx.fillRect(px, py, Math.max(1, cellWidth + 1), Math.max(1, cellHeight + 1));
      }
    }
    const probeX = (x0 + x1) / 2; const probeY = (y0 + y1) / 2;
    const probe = probeScalarField(field, probeX, frame.runtime.time, probeY);
    ctx.fillStyle = frame.palette.text;
    ctx.font = "700 12px ui-monospace, Consolas, monospace";
    ctx.fillText("u(x,y,t) · giao thoa mat nuoc", layout.left, Math.max(18, panelTop - 8));
    ctx.fillStyle = frame.palette.muted;
    ctx.font = "600 10px ui-monospace, Consolas, monospace";
    ctx.fillText(`bien do hien thi ×${exaggeration.toFixed(2)} · don vi ${field.valueUnit}`, layout.left, panelTop + panelHeight + 18);
    if (probe) drawBadge(ctx, `u = ${probe.value.toFixed(4)} ${field.valueUnit}`, layout.left + (layout.right - layout.left) / 2, panelTop + panelHeight + 40, frame.palette.amber);
    return;
  }
  const amplitude = Math.max(1e-9, maxAbsoluteValue(field.values));
  // Physical amplitudes in school fixtures are often centimetres or smaller;
  // scale the drawing for legibility while keeping the SI value in the readout.
  const scale = Math.min(10000, Math.max(24, (layout.height * 0.22) / amplitude)) * exaggeration;
  const y0 = layout.baseline - 60;
  ctx.save();
  ctx.strokeStyle = "rgba(148,163,184,.6)";
  ctx.lineWidth = 1.5;
  ctx.setLineDash([6, 6]);
  ctx.beginPath(); ctx.moveTo(layout.left, y0); ctx.lineTo(layout.right, y0); ctx.stroke();
  ctx.setLineDash([]);
  ctx.strokeStyle = frame.palette.cyan;
  ctx.lineWidth = 3;
  ctx.lineJoin = "round";
  ctx.beginPath();
  for (let index = 0; index < field.x.length; index++) {
    const x = field.x[index] ?? 0;
    const value = probeScalarField(field, x, frame.runtime.time)?.value ?? 0;
    const point = { x: layout.mapX(x), y: y0 - value * scale };
    if (index === 0) ctx.moveTo(point.x, point.y); else ctx.lineTo(point.x, point.y);
  }
  ctx.stroke();
  const probeBinding = node.properties.probeX;
  const probeX = typeof probeBinding === "undefined"
    ? field.x[Math.floor(field.x.length / 2)] ?? 0
    : resolver.resolve(probeBinding as never, frame.runtime.time, frame.runtime.index);
  const probe = probeScalarField(field, probeX, frame.runtime.time);
  if (probe) {
    const point = { x: layout.mapX(probe.x), y: y0 - probe.value * scale };
    ctx.fillStyle = frame.palette.amber; ctx.beginPath(); ctx.arc(point.x, point.y, 6, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = frame.palette.amber; ctx.lineWidth = 1; ctx.beginPath(); ctx.moveTo(point.x, y0); ctx.lineTo(point.x, point.y); ctx.stroke();
    drawBadge(ctx, `u = ${probe.value.toFixed(4)} m`, point.x, point.y - 18, frame.palette.amber);
  }
  ctx.fillStyle = frame.palette.text;
  ctx.font = "700 12px ui-monospace, Consolas, monospace";
  ctx.fillText("u(x,t) · sóng truyền +x", layout.left, Math.max(24, y0 - layout.height * 0.22 - 14));
  ctx.fillStyle = frame.palette.muted;
  ctx.font = "600 10px ui-monospace, Consolas, monospace";
  ctx.fillText(`biên độ hiển thị ×${exaggeration.toFixed(2)} · đơn vị m`, layout.left, y0 + 42);
  ctx.restore();
}

function maxAbsoluteValue(values: ArrayLike<number>): number {
  let maximum = 0;
  for (let index = 0; index < values.length; index++) {
    const value = values[index];
    if (typeof value === "number" && Number.isFinite(value)) maximum = Math.max(maximum, Math.abs(value));
  }
  return maximum;
}

function drawEffect(ctx: CanvasRenderingContext2D, frame: CanvasRenderFrame, node: SceneNode, layout: LayoutContext, resolver: BindingResolver, nodes: SceneNode[]) {
  const effect = propertyString(node, "effect");
  const target = actorNode(nodes, propertyString(node, "actorId"));
  if (!target) return;
  const targetState = resolver.resolveNode(target, frame.runtime.time, frame.runtime.index);
  const position = layout.mapPoint(targetState.x, targetState.y, propertyNumber(target, "lane"));
  if (effect === "projectile.glow") {
    const radius = 18 + Math.sin(frame.runtime.time * 8) * 3;
    const glow = ctx.createRadialGradient(position.x, position.y, 3, position.x, position.y, radius);
    glow.addColorStop(0, "rgba(165,243,252,.5)"); glow.addColorStop(1, "rgba(14,165,233,0)");
    ctx.fillStyle = glow; ctx.beginPath(); ctx.arc(position.x, position.y, radius, 0, Math.PI * 2); ctx.fill();
    return;
  }
  if (effect === "vehicle.headlight") {
    const glow = ctx.createRadialGradient(position.x + 42, position.y - 8, 4, position.x + 145, position.y - 8, 130);
    glow.addColorStop(0, "rgba(254,240,138,.34)"); glow.addColorStop(1, "rgba(254,240,138,0)");
    ctx.fillStyle = glow; ctx.beginPath(); ctx.moveTo(position.x + 40, position.y - 15); ctx.lineTo(position.x + 175, position.y - 42); ctx.lineTo(position.x + 175, position.y + 26); ctx.closePath(); ctx.fill();
    return;
  }
  if (effect === "vehicle.brake-smoke") {
    const acceleration = vectorValue(resolver, target, "ax", frame.runtime.time, frame.runtime.index);
    const velocity = vectorValue(resolver, target, "vx", frame.runtime.time, frame.runtime.index);
    if (acceleration >= -.01 || Math.abs(velocity) < .25) return;
    for (let i = 0; i < 6; i++) {
      const phase = (frame.runtime.time * 24 + i * 13) % 28;
      const radius = 5 + phase * .34;
      ctx.fillStyle = `rgba(203,213,225,${Math.max(.04, .32 - phase * .009)})`;
      ctx.beginPath(); ctx.arc(position.x - 40 - phase * 1.5, position.y + 12 - Math.sin(i * 2.1) * 5, radius, 0, Math.PI * 2); ctx.fill();
    }
    return;
  }
  if (effect === "collision.flash") {
    if (nodes.findIndex(item => item.id === node.id) !== nodes.findIndex(item => item.type === "effect" && propertyString(item, "effect") === effect)) return;
    const collisionSeries = seriesFor(frame.data, "values.collisionTime");
    if (collisionSeries.length === 0) return;
    const collision = resolver.resolve("values.collisionTime", frame.runtime.time, frame.runtime.index);
    if (!Number.isFinite(collision) || Math.abs(frame.runtime.time - collision) >= .12) return;
    const other = nodes.find(item => item.type === "body" && item.id !== target.id);
    if (!other) return;
    const otherState = resolver.resolveNode(other, frame.runtime.time, frame.runtime.index);
    const otherPosition = layout.mapPoint(otherState.x, otherState.y, propertyNumber(other, "lane"));
    const x = (position.x + otherPosition.x) / 2; const y = (position.y + otherPosition.y) / 2;
    ctx.save(); ctx.strokeStyle = frame.palette.amber; ctx.lineWidth = 3;
    for (let ray = 0; ray < 10; ray++) { const angle = ray * Math.PI / 5; ctx.beginPath(); ctx.moveTo(x + Math.cos(angle) * 13, y + Math.sin(angle) * 13); ctx.lineTo(x + Math.cos(angle) * 28, y + Math.sin(angle) * 28); ctx.stroke(); }
    ctx.restore();
  }
}

function drawNode(ctx: CanvasRenderingContext2D, frame: CanvasRenderFrame, node: SceneNode, layout: LayoutContext, resolver: BindingResolver, nodes: SceneNode[]) {
  const { runtime, palette } = frame;
  if (node.type === "body") {
    const state = resolver.resolveNode(node, runtime.time, runtime.index);
    const lane = propertyNumber(node, "lane");
    const position = layout.mapPoint(state.x, state.y, lane);
    const xSource = sourceName(node.transform.x);
    const initialX = resolver.resolve(node.transform.x, frame.data.time[0] ?? 0, 0);
    const scale = styleNumber(node, "scale", layout.width < 620 ? .78 : 1);
    ctx.save(); ctx.fillStyle = "rgba(2,6,23,.22)"; ctx.beginPath(); ctx.ellipse(position.x, position.y + 25 * scale, 44 * scale, 6 * scale, 0, 0, Math.PI * 2); ctx.fill(); ctx.restore();
    canvasAssetRegistry.drawAsset(propertyString(node, "asset", "object.block.amber"), ctx, {
      config: { id: node.id, asset: propertyString(node, "asset", "object.block.amber"), x: xSource, label: propertyString(node, "label") || undefined },
      position, velocity: { x: vectorValue(resolver, node, "vx", runtime.time, runtime.index), y: vectorValue(resolver, node, "vy", runtime.time, runtime.index) },
      acceleration: { x: vectorValue(resolver, node, "ax", runtime.time, runtime.index), y: vectorValue(resolver, node, "ay", runtime.time, runtime.index) },
      distance: Math.abs(state.x - initialX), scale, rotation: state.rotation,
    }, palette);
    return;
  }
  if (node.type === "effect") { drawEffect(ctx, frame, node, layout, resolver, nodes); return; }
  if (node.type === "vector") {
    const target = actorNode(nodes, propertyString(node, "actorId"));
    if (!target) return;
    const targetState = resolver.resolveNode(target, runtime.time, runtime.index);
    const position = layout.mapPoint(targetState.x, targetState.y, propertyNumber(target, "lane"));
    const kind = propertyString(node, "kind", "velocity");
    const vx = vectorValue(resolver, node, "vectorX", runtime.time, runtime.index);
    const vy = vectorValue(resolver, node, "vectorY", runtime.time, runtime.index);
    if (kind === "velocity" && !frame.overlays.velocity) return;
    if (kind === "acceleration" && !frame.overlays.acceleration) return;
    const magnitude = Math.hypot(vx, vy);
    const scale = kind === "velocity" ? Math.min(7, 110 / Math.max(1, magnitude)) : 10;
    const color = propertyString(node, "color") || (node.style.color === "red" ? palette.red : palette.green);
    drawVector(ctx, { x: position.x, y: position.y - (kind === "velocity" ? 48 : 82) }, { x: vx * scale, y: -vy * scale }, color, `${kind === "velocity" ? "v" : "a"}=${magnitude.toFixed(2)}`);
    return;
  }
  if (node.type === "prop") {
    const anchorNode = actorNode(nodes, propertyString(node, "anchorId"));
    const anchorState = anchorNode ? resolver.resolveNode(anchorNode, frame.data.time[0] ?? 0, 0) : { x: 0, y: 0 };
    const hasScreenAnchor = !anchorNode && typeof node.properties.anchorXRatio === "number" && typeof node.properties.anchorYRatio === "number";
    const anchor = hasScreenAnchor
      ? { x: frame.width * propertyNumber(node, "anchorXRatio"), y: frame.height * propertyNumber(node, "anchorYRatio") }
      : layout.mapPoint(anchorState.x, anchorState.y, propertyNumber(anchorNode ?? node, "lane"));
    canvasAssetRegistry.drawProp(propertyString(node, "asset"), ctx, anchor, propertyNumber(node, "angle"), palette);
    return;
  }
  if (node.type === "circuitComponent") { drawCircuit(ctx, frame, node, resolver); return; }
  if (node.type === "waveField") { drawWaveField(ctx, frame, node, layout, resolver); return; }
  if (node.type === "graph" || node.type === "chart") {
    const source = sourceName(node.properties.source);
    const values = seriesFor(frame.data, source);
    const total = Math.max(1, Math.floor(propertyNumber(node, "total", 1)));
    const slot = Math.max(0, Math.floor(propertyNumber(node, "slot", 0)));
    const hasBodies = nodes.some(item => item.type === "body");
    const columns = hasBodies ? 1 : Math.min(2, total);
    const rows = Math.ceil(total / columns);
    const margin = 24; const gap = 12;
    const availableWidth = hasBodies ? frame.width * .32 : frame.width - margin * 2;
    const panelWidth = (availableWidth - gap * (columns - 1)) / columns;
    const panelHeight = Math.max(64, (frame.height - margin * 2 - gap * (rows - 1)) / rows);
    const column = slot % columns; const row = Math.floor(slot / columns);
    const graphLeft = (hasBodies ? frame.width - margin - availableWidth : margin) + column * (panelWidth + gap);
    const graphTop = margin + row * (panelHeight + gap); const graphBottom = graphTop + panelHeight;
    const range = frame.data.ranges.get(source) ?? [0, 1];
    const minimum = range[0] === range[1] ? range[0] - 1 : range[0];
    const maximum = range[0] === range[1] ? range[1] + 1 : range[1];
    const plotTop = graphTop + 22; const plotBottom = graphBottom - 14;
    ctx.save(); ctx.fillStyle = "rgba(8,15,30,.78)"; ctx.strokeStyle = "rgba(148,163,184,.28)"; ctx.lineWidth = 1;
    ctx.beginPath(); ctx.roundRect(graphLeft, graphTop, panelWidth, panelHeight, 8); ctx.fill(); ctx.stroke();
    ctx.fillStyle = palette.text; ctx.font = "600 10px ui-monospace, Consolas, monospace";
    ctx.fillText(`${propertyString(node, "label", source)}${propertyString(node, "unit") ? ` (${propertyString(node, "unit")})` : ""}`, graphLeft + 8, graphTop + 14);
    ctx.strokeStyle = nodeColor(node, palette, propertyString(node, "color", palette.blue)); ctx.lineWidth = 2; ctx.beginPath();
    for (let i = 0; i < values.length; i++) {
      const x = graphLeft + 6 + i / Math.max(1, values.length - 1) * (panelWidth - 12);
      const y = plotBottom - ((values[i] ?? 0) - minimum) / Math.max(Number.EPSILON, maximum - minimum) * (plotBottom - plotTop);
      if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
    }
    ctx.stroke(); ctx.restore();
    return;
  }
  if (node.type === "text") {
    const value = resolver.resolve(node.properties.source as never, runtime.time, runtime.index);
    const format = propertyString(node, "format", "{value}");
    ctx.fillStyle = palette[propertyString(node, "color", "text") as keyof CanvasPalette] ?? palette.text;
    ctx.font = "700 12px ui-monospace, Consolas, monospace";
    ctx.fillText(format.replace("{value}", value.toFixed(2)), propertyNumber(node, "x"), propertyNumber(node, "y"));
    return;
  }
  if (node.type === "line" || node.type === "arrow") {
    const start = layout.mapPoint(resolver.resolve(node.transform.x, runtime.time, runtime.index), resolver.resolve(node.transform.y, runtime.time, runtime.index), 0);
    const end = { x: start.x + propertyNumber(node, "dx", 0), y: start.y + propertyNumber(node, "dy", 0) };
    const color = nodeColor(node, palette);
    ctx.save(); ctx.strokeStyle = color; ctx.lineWidth = styleNumber(node, "lineWidth", 2); ctx.beginPath(); ctx.moveTo(start.x, start.y); ctx.lineTo(end.x, end.y); ctx.stroke(); ctx.restore();
    if (node.type === "arrow") drawVector(ctx, start, { x: end.x - start.x, y: end.y - start.y }, color, propertyString(node, "label"));
    return;
  }
  // Read-only compatibility for historical optical schema versions. New
  // schemas use vectorScene and backend approval rejects these legacy types.
  if (node.type === "lens") {
    const lenses = nodes.filter(item => item.type === "lens");
    const ordinal = Math.max(0, lenses.findIndex(item => item.id === node.id));
    const x = propertyNumber(node, "x", frame.width * (ordinal + 1) / (lenses.length + 1));
    const y = propertyNumber(node, "y", frame.height * .5);
    ctx.save(); ctx.fillStyle = "rgba(56,189,248,.38)"; ctx.strokeStyle = "#e0f2fe"; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.ellipse(x, y, propertyNumber(node, "radiusX", 8), propertyNumber(node, "radiusY", frame.height * .28), 0, 0, Math.PI * 2); ctx.fill(); ctx.stroke(); ctx.restore();
    return;
  }
  if (node.type === "ray") {
    const lenses = nodes.filter(item => item.type === "lens");
    const lensPoints = lenses.map((lens, index) => ({
      x: propertyNumber(lens, "x", frame.width * (index + 1) / (lenses.length + 1)),
      y: propertyNumber(lens, "y", frame.height * .5),
    }));
    const startY = propertyNumber(node, "startY", frame.height * .28);
    ctx.save(); ctx.strokeStyle = nodeColor(node, palette, "#fb7185"); ctx.lineWidth = 2; ctx.beginPath();
    ctx.moveTo(24, startY);
    for (const lens of lensPoints) ctx.lineTo(lens.x, lens.y);
    ctx.lineTo(frame.width - 24, propertyNumber(node, "endY", frame.height * .72)); ctx.stroke(); ctx.restore();
    return;
  }
  if (node.type === "circle" || node.type === "rectangle") {
    const point = layout.mapPoint(resolver.resolve(node.transform.x, runtime.time, runtime.index), resolver.resolve(node.transform.y, runtime.time, runtime.index), 0);
    ctx.save(); ctx.fillStyle = nodeColor(node, palette);
    if (node.type === "circle") {
      ctx.beginPath(); ctx.arc(point.x, point.y, propertyNumber(node, "radius", 12), 0, Math.PI * 2); ctx.fill();
    } else {
      ctx.fillRect(point.x, point.y, propertyNumber(node, "width", 40), propertyNumber(node, "height", 24));
    }
    ctx.restore();
    return;
  }
  if (node.type === "spring" || node.type === "rope") {
    const target = actorNode(nodes, propertyString(node, "targetId"));
    const targetState = target ? resolver.resolveNode(target, runtime.time, runtime.index) : null;
    const targetPoint = target && targetState ? layout.mapPoint(targetState.x, targetState.y, propertyNumber(target, "lane")) : null;
    const start = targetPoint
      ? { x: propertyNumber(node, "anchorX", 92), y: targetPoint.y }
      : layout.mapPoint(resolver.resolve(node.transform.x, runtime.time, runtime.index), resolver.resolve(node.transform.y, runtime.time, runtime.index), 0);
    const endX = targetPoint ? targetPoint.x - 30 : resolver.resolve(node.properties.x2 as never, runtime.time, runtime.index) || start.x + propertyNumber(node, "length", 130);
    const end = { x: endX, y: start.y };
    if (node.type === "spring" && layout.mode === "springBench") {
      const equilibriumX = layout.left + 130 + ((0 - layout.xMin) / Math.max(Number.EPSILON, layout.xMax - layout.xMin)) * (layout.right - layout.left - 130);
      ctx.save(); ctx.strokeStyle = "rgba(148,163,184,.6)"; ctx.setLineDash([5, 6]); ctx.beginPath(); ctx.moveTo(equilibriumX, 68); ctx.lineTo(equilibriumX, layout.baseline + 33); ctx.stroke(); ctx.setLineDash([]); ctx.restore();
      drawBadge(ctx, "x = 0", equilibriumX, 53, palette.muted);
    }
    ctx.save(); ctx.strokeStyle = nodeColor(node, palette); ctx.lineWidth = styleNumber(node, "lineWidth", 2.5); ctx.beginPath(); ctx.moveTo(start.x, start.y);
    if (node.type === "spring") {
      const coils = Math.max(4, Math.floor(propertyNumber(node, "coils", 12)));
      for (let i = 1; i <= coils; i++) ctx.lineTo(start.x + (end.x - start.x) * i / coils, start.y + (i === coils ? 0 : (i % 2 ? -10 : 10)));
    } else ctx.lineTo(end.x, end.y);
    ctx.stroke(); ctx.restore();
  }
}

const REGISTERED_PRIMITIVES = ["body", "effect", "vector", "prop", "circuitComponent", "waveField", "graph", "chart", "text", "line", "arrow", "circle", "rectangle", "spring", "rope", "lens", "ray"];
primitiveRenderers.register('vectorScene', drawVectorScene);
for (const type of REGISTERED_PRIMITIVES) {
  primitiveRenderers.register(type, input => drawNode(input.frame.ctx, input.frame, input.node, input.layout as LayoutContext, input.resolver, input.nodes));
}

function drawTrajectory(frame: CanvasRenderFrame, node: SceneNode, layout: LayoutContext, resolver: BindingResolver): TrajectoryLayer | null {
  const { cache, data, runtime, palette } = frame;
  const key = `${node.id}|${frame.width}x${frame.height}|${layout.mode}|${layout.xMin}|${layout.xMax}|${layout.yMax}`;
  let layer = cache.trajectories.get(key);
  if (!layer) {
    const canvas = document.createElement("canvas"); canvas.width = frame.width; canvas.height = frame.height;
    const ctx = canvas.getContext("2d");
    if (!ctx) return null;
    layer = { canvas, ctx, builtIndex: -1, lastX: 0, lastY: 0 }; cache.trajectories.set(key, layer);
  }
  const previewAll = node.properties.previewAll === true;
  const target = previewAll ? data.length - 1 : Math.min(runtime.index, data.length - 1);
  if (layer.builtIndex > target) {
    layer.ctx.clearRect(0, 0, frame.width, frame.height); layer.builtIndex = -1;
  }
  layer.ctx.save(); layer.ctx.strokeStyle = palette.cyan; layer.ctx.lineWidth = 2.5; layer.ctx.lineCap = "round"; layer.ctx.setLineDash(layout.mode === "projectileRange" ? [3, 7] : []);
  while (layer.builtIndex < target) {
    const index = ++layer.builtIndex;
    const time = data.time[index] ?? 0;
    const state = resolver.resolveNode(node, time, index);
    const lane = propertyNumber(node, "lane");
    const point = layout.mode === "horizontalTrack"
      ? { x: layout.mapX(state.x), y: layout.baseline - lane * 58 }
      : layout.mapPoint(state.x, state.y, lane);
    layer.ctx.beginPath();
    if (index === 0) layer.ctx.moveTo(point.x, point.y); else { layer.ctx.moveTo(layer.lastX, layer.lastY); layer.ctx.lineTo(point.x, point.y); }
    layer.ctx.stroke(); layer.lastX = point.x; layer.lastY = point.y;
  }
  layer.ctx.restore();
  return layer;
}

primitiveRenderers.register("trajectory", input => {
  const layer = drawTrajectory(input.frame, input.node, input.layout as LayoutContext, input.resolver);
  if (layer) input.frame.ctx.drawImage(layer.canvas, 0, 0);
});

export class CanvasRenderer {
  public render(frame: CanvasRenderFrame): void {
    const nodes = nodesForGraph(frame.graph);
    const layoutKey = `${frame.graph.signature}|${frame.width}x${frame.height}`;
    if (frame.cache.layoutKey !== layoutKey || !frame.cache.layout) {
      frame.cache.layoutKey = layoutKey;
      frame.cache.layout = createLayout(nodes, frame.data, frame.width, frame.height);
    }
    const layout = frame.cache.layout;
    if (frame.cache.resolverData !== frame.data || !frame.cache.resolver) {
      frame.cache.resolverData = frame.data;
      frame.cache.resolver = new BindingResolver(frame.data, nodes);
    }
    const resolver = frame.cache.resolver;
    const staticKey = `${frame.graph.signature}|${frame.width}x${frame.height}|${frame.palette.backgroundTop}|${frame.palette.backgroundBottom}|${frame.palette.grid}|${frame.overlays.grid}|${layout.mode}`;
    if (!frame.cache.staticLayer || frame.cache.staticKey !== staticKey) {
      frame.cache.staticKey = staticKey;
      frame.cache.trajectories.clear();
      const layer = document.createElement("canvas"); layer.width = frame.width; layer.height = frame.height;
      const staticCtx = layer.getContext("2d");
      if (!staticCtx) return;
      drawBackground(staticCtx, frame.width, frame.height, frame.palette);
      for (const node of nodes) {
        if (node.layer !== "static") continue;
        if (node.type === "grid" && frame.overlays.grid) drawGrid(staticCtx, frame.width, frame.height, frame.palette);
        else if (node.type === "environment") drawEnvironment(staticCtx, node, layout, frame.palette);
        else if (node.type === "prop") primitiveRenderers.draw(node.type, { frame: { ...frame, ctx: staticCtx }, node, layout, resolver, nodes });
        else if (node.type === "ruler") drawRuler(staticCtx, layout, frame.palette);
        else primitiveRenderers.draw(node.type, { frame: { ...frame, ctx: staticCtx }, node, layout, resolver, nodes });
      }
      frame.cache.staticLayer = layer;
    }

    frame.ctx.clearRect(0, 0, frame.width, frame.height);
    if (frame.cache.staticLayer) frame.ctx.drawImage(frame.cache.staticLayer, 0, 0);
    if (frame.overlays.trajectory) {
      for (const node of nodes) {
        if (node.type !== "trajectory") continue;
        primitiveRenderers.draw(node.type, { frame, node, layout, resolver, nodes });
      }
    }
    for (const node of nodes) {
      if (node.layer !== "dynamic") continue;
      primitiveRenderers.draw(node.type, { frame, node, layout, resolver, nodes });
    }
  }
}
