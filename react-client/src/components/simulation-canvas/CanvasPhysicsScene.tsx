import { useEffect, useMemo, useRef, useState } from "react";
import type { Simulation } from "../../types/physlive";
import { paletteFor, presentationFor, type OverlayState } from "./model";
import { renderScene, sceneRegistry } from "./renderers";

type CanvasPhysicsSceneProps = {
  simulation: Simulation;
  index: number;
  overlays: OverlayState;
  time?: number;
};

export default function CanvasPhysicsScene(props: CanvasPhysicsSceneProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ width: 900, height: 420 });
  const [darkMode, setDarkMode] = useState(() => document.documentElement.dataset.themeEffective === "dark");
  const presentation = useMemo(() => presentationFor(props.simulation), [props.simulation]);
  const scene = props.simulation.visualization?.scene;
  const supported = Boolean(sceneRegistry[scene]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const update = () => {
      const bounds = container.getBoundingClientRect();
      setSize({ width: Math.max(360, Math.round(bounds.width)), height: Math.max(280, Math.round(bounds.height)) });
    };
    update();
    const observer = new ResizeObserver(update);
    observer.observe(container);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const root = document.documentElement;
    setDarkMode(root.dataset.themeEffective === "dark");
    const observer = new MutationObserver(() => setDarkMode(root.dataset.themeEffective === "dark"));
    observer.observe(root, { attributes: true, attributeFilter: ["data-theme-effective"] });
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !supported) return;
    const ratio = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.round(size.width * ratio);
    canvas.height = Math.round(size.height * ratio);
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.clearRect(0, 0, size.width, size.height);
    renderScene({
      ctx, width: size.width, height: size.height,
      simulation: props.simulation, index: props.index, time: props.time,
      overlays: props.overlays, presentation,
      palette: paletteFor(presentation.theme, darkMode),
    });
  }, [darkMode, presentation, props.index, props.overlays, props.simulation, props.time, size, supported]);

  return (
    <div ref={containerRef} className="physics-scene canvas-physics-scene">
      {supported ? (
        <canvas
          ref={canvasRef}
          className="physics-scene-canvas"
          role="img"
          aria-label={`Mô phỏng ${scene} tại thời điểm ${(props.time ?? props.simulation.time[props.index] ?? 0).toFixed(2)} giây`}
        />
      ) : <div className="learn-blocked" role="alert">Schema chưa khai báo scene renderer hợp lệ.</div>}
    </div>
  );
}
