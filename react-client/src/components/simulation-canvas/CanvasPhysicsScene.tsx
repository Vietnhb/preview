import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Simulation } from "../../types/physlive";
import { compileSimulationScene, validateSceneGraph } from "../../simulation-scene/SceneCompiler";
import { hasRenderableNodes } from "../../simulation-scene/SceneGraph";
import { prepareSimulationData } from "../../simulation-runtime/SimulationData";
import { indexAtTime } from "../../simulation-runtime/interpolate";
import { SimulationRuntime, type RuntimeFrame } from "../../simulation-runtime/SimulationRuntime";
import { CanvasRenderer, createCanvasRendererCache, type CanvasRendererCache } from "../../simulation-renderer/CanvasRenderer";
import { canvasDpr, type CanvasQuality } from "../../simulation-renderer/quality";
import { paletteFor, presentationFor, type OverlayState } from "./model";

type CanvasPhysicsSceneProps = Readonly<{
  simulation: Simulation;
  index: number;
  overlays: OverlayState;
  time?: number;
  playing?: boolean;
  speed?: number;
  quality?: CanvasQuality;
  seekRevision?: number;
  onTimeChange?: (time: number) => void;
  onPlaybackEnd?: () => void;
}>;

type RenderState = {
  simulation: Simulation;
  data: ReturnType<typeof prepareSimulationData>;
  graph: ReturnType<typeof compileSimulationScene>;
  overlays: OverlayState;
  palette: ReturnType<typeof paletteFor>;
  supported: boolean;
};

const READOUT_INTERVAL_MS = 80;

export default function CanvasPhysicsScene(props: CanvasPhysicsSceneProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const contextRef = useRef<CanvasRenderingContext2D | null>(null);
  const sizeRef = useRef({ width: 900, height: 420 });
  const pixelRatioRef = useRef(0);
  const rendererRef = useRef(new CanvasRenderer());
  const cacheRef = useRef<CanvasRendererCache>(createCanvasRendererCache());
  const drawFrameRef = useRef<number | null>(null);
  const resizeFrameRef = useRef<number | null>(null);
  const lastNotifiedTimeRef = useRef(props.time ?? props.simulation.time[props.index] ?? 0);
  const mountedSimulationRef = useRef(false);
  const simulationResetPendingRef = useRef(false);
  const lastPublishedWallRef = useRef(0);
  const appliedSeekRevisionRef = useRef(props.seekRevision);
  const onTimeChangeRef = useRef(props.onTimeChange);
  const onPlaybackEndRef = useRef(props.onPlaybackEnd);
  const [darkMode, setDarkMode] = useState(() => document.documentElement.dataset.themeEffective === "dark");
  const data = useMemo(() => prepareSimulationData(props.simulation), [props.simulation]);
  const presentation = useMemo(() => presentationFor(props.simulation), [props.simulation]);
  const graph = useMemo(() => compileSimulationScene(props.simulation), [props.simulation]);
  const palette = useMemo(() => paletteFor(presentation.theme, darkMode), [darkMode, presentation.theme]);
  const validation = useMemo(() => validateSceneGraph(graph, data), [data, graph]);
  const supported = validation.valid && hasRenderableNodes(graph);
  const renderStateRef = useRef<RenderState>({
    simulation: props.simulation, data, graph, overlays: props.overlays, palette, supported,
  });
  const [runtime] = useState(() => new SimulationRuntime());

  const renderFrame = useCallback((frame: RuntimeFrame) => {
    const ctx = contextRef.current;
    if (!ctx) return;
    const state = renderStateRef.current;
    const { width, height } = sizeRef.current;
    rendererRef.current.render({
      ctx, width, height, graph: state.graph, data: state.data, runtime: frame,
      overlays: state.overlays, palette: state.palette, cache: cacheRef.current,
    });
    const now = performance.now();
    if (!runtime.isPlaying() || now - lastPublishedWallRef.current >= READOUT_INTERVAL_MS || frame.time >= (state.data.time.at(-1) ?? frame.time)) {
      lastPublishedWallRef.current = now;
      lastNotifiedTimeRef.current = frame.time;
      onTimeChangeRef.current?.(frame.time);
    }
  }, [runtime]);

  const requestDraw = useCallback(() => {
    if (drawFrameRef.current !== null) return;
    drawFrameRef.current = requestAnimationFrame(() => {
      drawFrameRef.current = null;
      const current: RuntimeFrame = { time: runtime.getTime(), index: runtime.getIndex(), data };
      renderFrame(current);
    });
  }, [data, renderFrame, runtime]);

  const resizeCanvas = useCallback(() => {
    const container = containerRef.current;
    const canvas = canvasRef.current;
    if (!container || !canvas) return;
    const bounds = container.getBoundingClientRect();
    const width = Math.max(360, Math.round(bounds.width));
    const height = Math.max(280, Math.round(bounds.height));
    const ratio = canvasDpr(props.quality);
    const pixelWidth = Math.round(width * ratio);
    const pixelHeight = Math.round(height * ratio);
    const resized = canvas.width !== pixelWidth || canvas.height !== pixelHeight || pixelRatioRef.current !== ratio;
    sizeRef.current = { width, height };
    if (resized) {
      canvas.width = pixelWidth;
      canvas.height = pixelHeight;
      pixelRatioRef.current = ratio;
      contextRef.current = canvas.getContext("2d");
      contextRef.current?.setTransform(ratio, 0, 0, ratio, 0, 0);
      if (contextRef.current) contextRef.current.imageSmoothingEnabled = true;
      cacheRef.current = createCanvasRendererCache();
    } else if (!contextRef.current) {
      contextRef.current = canvas.getContext("2d");
      contextRef.current?.setTransform(ratio, 0, 0, ratio, 0, 0);
    }
    requestDraw();
  }, [props.quality, requestDraw]);

  useEffect(() => {
    renderStateRef.current = { simulation: props.simulation, data, graph, overlays: props.overlays, palette, supported };
    onTimeChangeRef.current = props.onTimeChange;
    onPlaybackEndRef.current = props.onPlaybackEnd;
  }, [data, graph, palette, props.onPlaybackEnd, props.onTimeChange, props.overlays, props.simulation, supported]);

  useEffect(() => {
    const unsubscribe = runtime.subscribe(renderFrame);
    const unsubscribeEnd = runtime.subscribePlaybackEnd(() => onPlaybackEndRef.current?.());
    return () => { unsubscribe(); unsubscribeEnd(); };
  }, [renderFrame, runtime]);

  useEffect(() => {
    const root = document.documentElement;
    const updateTheme = () => {
      const next = root.dataset.themeEffective === "dark";
      setDarkMode(current => current === next ? current : next);
    };
    const observer = new MutationObserver(updateTheme);
    observer.observe(root, { attributes: true, attributeFilter: ["data-theme-effective"] });
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const update = () => {
      if (resizeFrameRef.current !== null) return;
      resizeFrameRef.current = requestAnimationFrame(() => {
        resizeFrameRef.current = null;
        resizeCanvas();
      });
    };
    // Initialise the canvas before the runtime emits its first frame. If this
    // is deferred to RAF, the initial runtime frame can be dropped because
    // the 2D context does not exist yet; playback then appears to "wake up"
    // the scene by emitting another frame.
    resizeCanvas();
    const observer = new ResizeObserver(update);
    observer.observe(container);
    return () => {
      observer.disconnect();
      if (resizeFrameRef.current !== null) cancelAnimationFrame(resizeFrameRef.current);
      resizeFrameRef.current = null;
    };
  }, [resizeCanvas]);

  useEffect(() => {
    simulationResetPendingRef.current = mountedSimulationRef.current;
    mountedSimulationRef.current = true;
    runtime.setSimulation(props.simulation);
    lastNotifiedTimeRef.current = props.simulation.time[0] ?? 0;
    cacheRef.current = createCanvasRendererCache();
    requestDraw();
  }, [data, props.simulation, requestDraw, runtime]);

  useEffect(() => {
    runtime.setSpeed(props.speed ?? 1);
  }, [props.speed, runtime]);

  useEffect(() => {
    cacheRef.current.staticKey = "";
    requestDraw();
  }, [graph, palette, props.overlays, requestDraw]);

  useEffect(() => {
    if (!props.playing || !supported) runtime.pause();
    else runtime.play();
    if (!props.playing) requestDraw();
  }, [props.playing, props.simulation, requestDraw, runtime, supported]);

  // While playing, the runtime owns the clock and ignores throttled UI echoes.
  useEffect(() => {
    if (props.playing) return;
    if (simulationResetPendingRef.current) {
      simulationResetPendingRef.current = false;
      return;
    }
    const requested = props.time ?? data.time[props.index] ?? 0;
    if (props.seekRevision !== appliedSeekRevisionRef.current) {
      appliedSeekRevisionRef.current = props.seekRevision;
      runtime.seek(requested);
      return;
    }
    if (props.time !== undefined) {
      if (Math.abs(requested - lastNotifiedTimeRef.current) > Number.EPSILON) runtime.seek(requested);
      return;
    }
    if (runtime.getIndex() !== indexAtTime(data.time, requested)) runtime.seek(requested);
  }, [data, props.index, props.playing, props.time, props.seekRevision, runtime]);

  useEffect(() => () => {
    runtime.destroy();
    if (drawFrameRef.current !== null) cancelAnimationFrame(drawFrameRef.current);
    if (resizeFrameRef.current !== null) cancelAnimationFrame(resizeFrameRef.current);
    drawFrameRef.current = null;
    resizeFrameRef.current = null;
  }, [runtime]);

  const scene = props.simulation.visualization?.scene ?? "generic";
  const displayTime = props.time ?? props.simulation.time[props.index] ?? 0;
  return (
    <div ref={containerRef} className="physics-scene canvas-physics-scene">
      {supported ? (
        <canvas ref={canvasRef} className="physics-scene-canvas" aria-label={`Mô phỏng ${scene} tại thời điểm ${displayTime.toFixed(2)} giây`} />
      ) : <div className="learn-blocked" role="alert">Spec chưa khai báo primitive renderer hợp lệ.</div>}
    </div>
  );
}
