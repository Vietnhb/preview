import { useEffect, useRef } from "react";

type DotFieldProps = {
  /** Distance between dots in CSS pixels. */
  gap?: number;
  /** Radius (px) of the cursor's repulsion field. */
  radius?: number;
  /** Base dot colour and the colour dots take near the cursor. */
  color?: string;
  activeColor?: string;
  className?: string;
};

type Dot = { x: number; y: number; ox: number; oy: number; vx: number; vy: number };

function readColor(element: HTMLElement, value: string) {
  if (!value.startsWith("var(")) return value;
  const name = value.slice(4, -1).trim();
  return getComputedStyle(element).getPropertyValue(name).trim() || "#60a5fa";
}

function hexToRgb(hex: string): [number, number, number] {
  const clean = hex.replace("#", "");
  const full = clean.length === 3 ? clean.split("").map(c => c + c).join("") : clean.slice(0, 6);
  const n = Number.parseInt(full, 16);
  return Number.isNaN(n) ? [96, 165, 250] : [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

/**
 * A grid of dots that behaves like charges in a field: the pointer pushes them
 * away, springs pull them home. Pauses off-screen and renders a still grid when
 * the user prefers reduced motion.
 */
export default function DotField({
  gap = 26,
  radius = 140,
  color = "rgb(148 163 184 / 26%)",
  activeColor = "var(--glow-1)",
  className = "",
}: Readonly<DotFieldProps>) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext("2d");
    if (!canvas || !context) return;
    const reduced = globalThis.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
    const [ar, ag, ab] = hexToRgb(readColor(canvas, activeColor));
    let dots: Dot[] = [];
    let width = 0;
    let height = 0;
    let frame = 0;
    let visible = true;
    const pointer = { x: -9999, y: -9999, active: false };

    const layout = () => {
      const rect = canvas.getBoundingClientRect();
      const ratio = Math.min(globalThis.devicePixelRatio || 1, 2);
      width = rect.width;
      height = rect.height;
      canvas.width = Math.round(width * ratio);
      canvas.height = Math.round(height * ratio);
      context.setTransform(ratio, 0, 0, ratio, 0, 0);
      dots = [];
      const offsetX = (width % gap) / 2;
      const offsetY = (height % gap) / 2;
      for (let y = offsetY; y <= height; y += gap) {
        for (let x = offsetX; x <= width; x += gap) dots.push({ x, y, ox: x, oy: y, vx: 0, vy: 0 });
      }
      draw();
    };

    const draw = () => {
      context.clearRect(0, 0, width, height);
      for (const dot of dots) {
        const dx = dot.x - pointer.x;
        const dy = dot.y - pointer.y;
        const near = pointer.active ? Math.max(0, 1 - Math.hypot(dx, dy) / radius) : 0;
        if (near > 0) {
          context.fillStyle = `rgb(${ar} ${ag} ${ab} / ${0.25 + near * 0.75})`;
          context.beginPath();
          context.arc(dot.x, dot.y, 1.2 + near * 1.8, 0, Math.PI * 2);
          context.fill();
        } else {
          context.fillStyle = color;
          context.fillRect(dot.x - 1, dot.y - 1, 2, 2);
        }
      }
    };

    const step = () => {
      let moving = false;
      for (const dot of dots) {
        if (pointer.active) {
          const dx = dot.x - pointer.x;
          const dy = dot.y - pointer.y;
          const distance = Math.hypot(dx, dy);
          if (distance < radius && distance > 0.01) {
            const force = (1 - distance / radius) * 2.4;
            dot.vx += (dx / distance) * force;
            dot.vy += (dy / distance) * force;
          }
        }
        dot.vx += (dot.ox - dot.x) * 0.06;
        dot.vy += (dot.oy - dot.y) * 0.06;
        dot.vx *= 0.82;
        dot.vy *= 0.82;
        dot.x += dot.vx;
        dot.y += dot.vy;
        if (Math.abs(dot.vx) + Math.abs(dot.vy) > 0.02) moving = true;
      }
      draw();
      frame = moving || pointer.active ? requestAnimationFrame(step) : 0;
    };

    const wake = () => {
      if (!frame && visible && !reduced) frame = requestAnimationFrame(step);
    };
    const onMove = (event: PointerEvent) => {
      const rect = canvas.getBoundingClientRect();
      pointer.x = event.clientX - rect.left;
      pointer.y = event.clientY - rect.top;
      pointer.active = pointer.x >= 0 && pointer.y >= 0 && pointer.x <= rect.width && pointer.y <= rect.height;
      wake();
    };
    const onLeave = () => {
      pointer.active = false;
      wake();
    };

    layout();
    const resize = new ResizeObserver(layout);
    resize.observe(canvas);
    const observer = new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting;
      if (!visible) {
        cancelAnimationFrame(frame);
        frame = 0;
      }
    });
    observer.observe(canvas);
    if (!reduced) {
      globalThis.addEventListener("pointermove", onMove, { passive: true });
      document.addEventListener("pointerleave", onLeave);
    }
    return () => {
      cancelAnimationFrame(frame);
      resize.disconnect();
      observer.disconnect();
      globalThis.removeEventListener("pointermove", onMove);
      document.removeEventListener("pointerleave", onLeave);
    };
  }, [gap, radius, color, activeColor]);

  return <canvas ref={canvasRef} className={`dot-field ${className}`} aria-hidden="true" />;
}
