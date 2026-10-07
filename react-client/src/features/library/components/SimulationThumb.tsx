import { useEffect, useRef, useState } from "react";
import { getSharedSimulation } from "../../simulation/api/simulationApi";
import { openSharedGeneratedSimulation, type GeneratedSimulationResult } from "../../simulation/api/simulationUnderstandingApi";
import SvgPixiScene from "../../simulation/components/SvgPixiScene";
import "../../simulation/styles/simulation.css";
import "../styles/simulation-thumb.css";

/** A lightweight curve drawn from the simulation's own data, used when the live scene is not mounted. */
type Curve = { paths: string[]; ends: [number, number][]; label: string };
type Preview = { scene: GeneratedSimulationResult | null; curve: Curve | null };

const WIDTH = 240;
const HEIGHT = 112;
const PAD = 14;
const POINTS = 48;
const MAX_TRACKS = 3;
const MAX_PARALLEL = 2;
/** Each live scene is a sandboxed frame with its own renderer, so only a handful run at once. */
const MAX_LIVE = 6;

const cache = new Map<string, Promise<Preview>>();
let running = 0;
const waiting: (() => void)[] = [];
const slot = () => new Promise<void>(resolve => { if (running < MAX_PARALLEL) { running += 1; resolve(); } else waiting.push(resolve); });
const release = () => { const next = waiting.shift(); if (next) next(); else running -= 1; };
let live = 0;

function pick(series: number[], count: number) {
  if (series.length <= count) return series;
  return Array.from({ length: count }, (_, index) => series[Math.round(index * (series.length - 1) / (count - 1))]);
}
const usable = (series: number[] | undefined): series is number[] => Array.isArray(series) && series.length > 1 && series.every(Number.isFinite);
const varies = (series: number[]) => Math.max(...series) - Math.min(...series) > 1e-9;

type Series = Record<string, number[]>;
type Meta = Record<string, { label?: string; unit?: string } | undefined>;

/** Real x–y trajectories when the motion is planar, otherwise the main quantity over time. */
function buildCurve(time: number[], series: Series, meta: Meta = {}): Curve | null {
  const tracks: { xs: number[]; ys: number[] }[] = [];
  for (const key of Object.keys(series)) {
    if (!/x$/i.test(key)) continue;
    const partner = key.replace(/x$/i, match => (match === "X" ? "Y" : "y"));
    const xs = series[key], ys = series[partner];
    if (usable(xs) && usable(ys) && xs.length === ys.length && varies(ys) && varies(xs)) tracks.push({ xs, ys });
  }
  let label = "Quỹ đạo";
  if (tracks.length === 0) {
    const rank = (key: string) => /position|\.x$|\.y$|displacement|height/i.test(key) ? 0 : /velocity|speed/i.test(key) ? 1 : /acceleration/i.test(key) ? 3 : 2;
    const keys = Object.keys(series).filter(key => usable(series[key]) && usable(time) && series[key].length === time.length && varies(series[key])).sort((a, b) => rank(a) - rank(b));
    if (keys.length === 0) return null;
    const first = keys[0];
    for (const key of keys.filter(key => rank(key) === rank(first)).slice(0, MAX_TRACKS)) tracks.push({ xs: time, ys: series[key] });
    const name = meta[first]?.label?.trim();
    label = `${name ? name.charAt(0).toUpperCase() + name.slice(1) : "Đại lượng"} theo thời gian`;
  }
  const chosen = tracks.slice(0, MAX_TRACKS).map(track => ({ xs: pick(track.xs, POINTS), ys: pick(track.ys, POINTS) }));
  const allX = chosen.flatMap(track => track.xs), allY = chosen.flatMap(track => track.ys);
  const minX = Math.min(...allX), maxX = Math.max(...allX), minY = Math.min(...allY), maxY = Math.max(...allY);
  const sx = (WIDTH - 2 * PAD) / (maxX - minX || 1), sy = (HEIGHT - 2 * PAD) / (maxY - minY || 1);
  const point = (x: number, y: number): [number, number] => [PAD + (x - minX) * sx, HEIGHT - PAD - (y - minY) * sy];
  return {
    label,
    paths: chosen.map(track => track.xs.map((x, index) => { const [px, py] = point(x, track.ys[index]); return `${index ? "L" : "M"}${px.toFixed(1)},${py.toFixed(1)}`; }).join(" ")),
    ends: chosen.map(track => point(track.xs.at(-1)!, track.ys.at(-1)!)),
  };
}

async function fetchPreview(simulationId: string): Promise<Preview> {
  const scene = await openSharedGeneratedSimulation(simulationId).catch(() => null);
  const frames = scene?.simulationSpec?.solverTimeline?.frames as { t: number; values: Record<string, number> }[] | undefined;
  if (scene && frames && frames.length > 1) {
    const series: Series = {};
    for (const key of Object.keys(frames[0].values ?? {})) if (key !== "t") series[key] = frames.map(frame => frame.values?.[key]);
    return { scene, curve: buildCurve(frames.map(frame => frame.t), series, scene.simulationSpec.solverFieldMeta as Meta | undefined) };
  }
  // Saves that predate stored scenes only have the plain solver series.
  const plain = await getSharedSimulation(simulationId).catch(() => null);
  return { scene: null, curve: plain ? buildCurve(plain.time ?? [], { ...plain.values, ...plain.velocities, ...plain.positions }) : null };
}

function load(simulationId: string) {
  let entry = cache.get(simulationId);
  if (!entry) {
    entry = slot().then(() => fetchPreview(simulationId)).catch(() => ({ scene: null, curve: null })).finally(release);
    cache.set(simulationId, entry);
  }
  return entry;
}

/**
 * Card cover showing the shared simulation itself. While the card is on screen it mounts the author's
 * real scene (resting on the first frame, playing on hover); at most MAX_LIVE scenes exist at once and
 * a card that scrolls away gives its scene up. Cards beyond that limit show a curve drawn from the same
 * simulation data. Nothing here is a stock illustration.
 */
export default function SimulationThumb({ simulationId }: Readonly<{ simulationId: string }>) {
  const host = useRef<HTMLDivElement>(null);
  const [state, setState] = useState<{ id: string; preview: Preview } | null>(null);
  const [visible, setVisible] = useState(false);
  const [hover, setHover] = useState(false);
  const [hasSlot, setHasSlot] = useState(false);
  /** The live scene could not be drawn (for example the tab was in the background); show the data curve instead. */
  const [failedId, setFailedId] = useState("");

  useEffect(() => {
    const element = host.current;
    if (!element) return;
    if (typeof IntersectionObserver === "undefined") { setVisible(true); return; }
    const observer = new IntersectionObserver(entries => setVisible(entries.some(entry => entry.isIntersecting)), { rootMargin: "120px" });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!visible) return;
    let active = true;
    void load(simulationId).then(preview => { if (active) setState({ id: simulationId, preview }); });
    return () => { active = false; };
  }, [visible, simulationId]);

  const preview = state?.id === simulationId ? state.preview : null;
  const scene = preview?.scene ?? null;
  // Take a live-scene slot only while on screen, and hand it back on leaving.
  useEffect(() => {
    if (!visible || !scene || failedId === simulationId || live >= MAX_LIVE) return;
    live += 1;
    setHasSlot(true);
    return () => { live -= 1; setHasSlot(false); };
  }, [visible, scene, failedId, simulationId]);

  const curve = preview?.curve ?? null;
  const timeline = scene?.simulationSpec.solverTimeline;
  return <div ref={host} className="simulation-thumb" data-state={!preview ? "loading" : hasSlot ? "live" : curve ? "curve" : "none"}
    onPointerEnter={() => setHover(true)} onPointerLeave={() => setHover(false)}>
    {hasSlot && scene && timeline ? <SvgPixiScene cover coverPlaying={hover} onCoverFailed={() => setFailedId(simulationId)}
        program={scene.simulationSpec.visualProgram ?? { code: "" }} timeline={timeline}
        parameters={scene.savedParameters ?? Object.fromEntries(scene.parameters.map(parameter => [parameter.name, parameter.value]))}
        models={scene.simulationSpec.physicsModels}
        fieldMeta={scene.simulationSpec.solverFieldMeta as Record<string, { unit?: string; label?: string }> | undefined}
        verificationStatus={scene.validation?.status ?? "VISUAL_ONLY_UNVERIFIED"} />
      : curve ? <>
        <svg className="simulation-thumb__plot" viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="img" aria-label={`Bản xem trước: ${curve.label.toLocaleLowerCase("vi")}`} preserveAspectRatio="xMidYMid meet">
          <line className="simulation-thumb__axis" x1={PAD} x2={WIDTH - PAD} y1={HEIGHT - PAD} y2={HEIGHT - PAD} />
          <line className="simulation-thumb__axis" x1={PAD} x2={PAD} y1={PAD} y2={HEIGHT - PAD} />
          {curve.paths.map((d, index) => <path key={index} className="simulation-thumb__curve" data-track={index} d={d} />)}
          {curve.ends.map(([x, y], index) => <circle key={index} className="simulation-thumb__end" data-track={index} cx={x} cy={y} r={3} />)}
          {hover && <circle className="simulation-thumb__runner" r={4.5}><animateMotion dur="2.4s" repeatCount="indefinite" path={curve.paths[0]} /></circle>}
        </svg>
        <span className="simulation-thumb__label">{curve.label}</span>
      </>
      : preview ? <span className="simulation-thumb__none">Chưa có bản xem trước</span>
      : <span className="simulation-thumb__skeleton" aria-hidden="true" />}
  </div>;
}
