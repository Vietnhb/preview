import { useId, useState, type CSSProperties } from "react";
import { motion, useReducedMotion } from "motion/react";

/** Interactive teaching figure: one launch speed, the viewer changes the angle (no drag). */
const G = 9.81;
const SPEED = 20;
const SCALE = 7.4;
const LEFT = 36;
const GROUND = 232;
const MIN_ANGLE = 10;
const MAX_ANGLE = 80;

const rad = (angle: number) => (angle * Math.PI) / 180;
const range = (angle: number) => (SPEED * SPEED * Math.sin(2 * rad(angle))) / G;
const peak = (angle: number) => (SPEED * Math.sin(rad(angle))) ** 2 / (2 * G);
const flight = (angle: number) => (2 * SPEED * Math.sin(rad(angle))) / G;

function path(angle: number) {
  const t = rad(angle);
  const total = flight(angle);
  return Array.from({ length: 41 }, (_, i) => {
    const time = (total * i) / 40;
    const x = SPEED * Math.cos(t) * time;
    const y = SPEED * Math.sin(t) * time - (G * time * time) / 2;
    return `${i ? "L" : "M"}${(LEFT + x * SCALE).toFixed(1)},${(GROUND - Math.max(0, y) * SCALE).toFixed(1)}`;
  }).join(" ");
}

const fmt = (value: number) => value.toFixed(1).replace(".", ",");

export default function TrajectoryFigure() {
  const reduced = useReducedMotion();
  const [angle, setAngle] = useState(45);
  const sliderId = useId();
  const landing = LEFT + range(angle) * SCALE;
  const apexX = LEFT + (range(angle) / 2) * SCALE;
  const apexY = GROUND - peak(angle) * SCALE;
  const fill = ((angle - MIN_ANGLE) / (MAX_ANGLE - MIN_ANGLE)) * 100;
  return (
    <figure className="auth-figure">
      <svg viewBox="0 0 360 260" role="img" aria-label={`Quỹ đạo ném xiên góc ${angle}° với vận tốc đầu 20 m/s, tầm xa ${fmt(range(angle))} mét`}>
        {[0, 10, 20, 30, 40].map((x) => (
          <line key={`x${x}`} x1={LEFT + x * SCALE} x2={LEFT + x * SCALE} y1={28} y2={GROUND} className="auth-figure__grid" />
        ))}
        {[0, 5, 10, 15, 20, 25].map((y) => (
          <line key={`y${y}`} x1={LEFT} x2={LEFT + 42 * SCALE} y1={GROUND - y * SCALE} y2={GROUND - y * SCALE} className="auth-figure__grid" />
        ))}
        <line x1={LEFT} x2={LEFT + 42 * SCALE} y1={GROUND} y2={GROUND} className="auth-figure__axis" />
        {/* 45° gives the longest range; kept as a faint reference while the angle changes. */}
        {angle !== 45 && <g className="auth-figure__other"><path d={path(45)} /><circle cx={LEFT + range(45) * SCALE} cy={GROUND} r={3} /></g>}
        <g className="auth-figure__best">
          <motion.path d={path(angle)} initial={reduced ? false : { pathLength: 0 }} animate={{ pathLength: 1 }} transition={{ duration: 1.2, ease: "easeInOut", delay: 0.3 }} />
          <line x1={apexX} x2={apexX} y1={apexY} y2={GROUND} className="auth-figure__drop" />
          <circle cx={landing} cy={GROUND} r={4} />
        </g>
        <text x={apexX} y={apexY - 10} textAnchor="middle" className="auth-figure__label">{angle}°</text>
        <text x={LEFT} y={GROUND + 18} className="auth-figure__tick">0</text>
        <text x={LEFT + 40 * SCALE} y={GROUND + 18} textAnchor="middle" className="auth-figure__tick">40 m</text>
      </svg>
      <div className="auth-figure__control">
        <label htmlFor={sliderId}>Góc ném</label>
        <input id={sliderId} type="range" min={MIN_ANGLE} max={MAX_ANGLE} step={1} value={angle}
          style={{ "--fill": `${fill}%` } as CSSProperties} onChange={(event) => setAngle(Number(event.target.value))} />
        <output htmlFor={sliderId}>{angle}°</output>
      </div>
      <dl className="auth-figure__stats">
        <div><dt>Tầm xa</dt><dd>{fmt(range(angle))} m</dd></div>
        <div><dt>Độ cao cực đại</dt><dd>{fmt(peak(angle))} m</dd></div>
        <div><dt>Thời gian bay</dt><dd>{fmt(flight(angle))} s</dd></div>
      </dl>
      <figcaption>Vận tốc đầu 20 m/s, bỏ qua sức cản. Kéo thử góc ném: 45° bay xa nhất.</figcaption>
    </figure>
  );
}
