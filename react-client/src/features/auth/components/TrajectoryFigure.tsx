import { motion, useReducedMotion } from "motion/react";

/** Teaching figure: three launches at the same speed, different angles (no drag). */
const G = 9.81;
const SPEED = 20;
const ANGLES = [30, 45, 60];
const SCALE = 7.4;
const LEFT = 36;
const GROUND = 232;

function path(angle: number) {
  const t = (angle * Math.PI) / 180;
  const flight = (2 * SPEED * Math.sin(t)) / G;
  return Array.from({ length: 41 }, (_, i) => {
    const time = (flight * i) / 40;
    const x = SPEED * Math.cos(t) * time;
    const y = SPEED * Math.sin(t) * time - (G * time * time) / 2;
    return `${i ? "L" : "M"}${(LEFT + x * SCALE).toFixed(1)},${(GROUND - Math.max(0, y) * SCALE).toFixed(1)}`;
  }).join(" ");
}

export default function TrajectoryFigure() {
  const reduced = useReducedMotion();
  const range = (angle: number) => (SPEED * SPEED * Math.sin((2 * angle * Math.PI) / 180)) / G;
  return (
    <figure className="auth-figure">
      <svg viewBox="0 0 360 260" role="img" aria-label="Quỹ đạo ném xiên với góc 30°, 45° và 60° cùng vận tốc đầu 20 m/s">
        {[0, 10, 20, 30, 40].map((x) => (
          <line key={`x${x}`} x1={LEFT + x * SCALE} x2={LEFT + x * SCALE} y1={28} y2={GROUND} className="auth-figure__grid" />
        ))}
        {[0, 5, 10, 15, 20, 25].map((y) => (
          <line key={`y${y}`} x1={LEFT} x2={LEFT + 42 * SCALE} y1={GROUND - y * SCALE} y2={GROUND - y * SCALE} className="auth-figure__grid" />
        ))}
        <line x1={LEFT} x2={LEFT + 42 * SCALE} y1={GROUND} y2={GROUND} className="auth-figure__axis" />
        {ANGLES.map((angle, index) => (
          <g key={angle} className={angle === 45 ? "auth-figure__best" : "auth-figure__other"}>
            <motion.path
              d={path(angle)}
              initial={reduced ? false : { pathLength: 0 }}
              animate={{ pathLength: 1 }}
              transition={{ duration: 1.4, ease: "easeInOut", delay: 0.3 + index * 0.35 }}
            />
            <motion.circle
              cx={LEFT + range(angle) * SCALE}
              cy={GROUND}
              r={3.5}
              initial={reduced ? false : { scale: 0, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ duration: 0.3, delay: 1.6 + index * 0.35 }}
            />
          </g>
        ))}
        <text x={LEFT + 20.4 * SCALE} y={GROUND - 11.4 * SCALE} textAnchor="middle" className="auth-figure__label">45°</text>
        <text x={LEFT + 17.7 * SCALE} y={GROUND - 16.4 * SCALE} textAnchor="middle" className="auth-figure__label auth-figure__label--muted">60°</text>
        <text x={LEFT + 17.7 * SCALE} y={GROUND - 6.2 * SCALE} textAnchor="middle" className="auth-figure__label auth-figure__label--muted">30°</text>
        <text x={LEFT} y={GROUND + 18} className="auth-figure__tick">0</text>
        <text x={LEFT + 40 * SCALE} y={GROUND + 18} textAnchor="middle" className="auth-figure__tick">40 m</text>
      </svg>
      <figcaption>
        Cùng vận tốc đầu 20 m/s: góc 30° và 60° rơi cùng một điểm, góc 45° bay xa nhất.
      </figcaption>
    </figure>
  );
}
