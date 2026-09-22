/** Local teaching experiment: SI units, launch and landing at y=0, no air drag. */
export const GRAVITY = 9.81;
export const LAUNCH = {
  angle: { min: 10, max: 80, step: 1, initial: 55 },
  speed: { min: 8, max: 30, step: 0.5, initial: 28 },
};
export function projectile(angle: number, speed: number, elapsed: number) {
  const theta = (angle * Math.PI) / 180;
  const vx = speed * Math.cos(theta);
  const initialVy = speed * Math.sin(theta);
  const duration = (2 * initialVy) / GRAVITY;
  const time = Math.max(0, Math.min(elapsed, duration));
  return {
    time,
    duration,
    x: vx * time,
    y:
      time === duration
        ? 0
        : Math.max(0, initialVy * time - (GRAVITY * time * time) / 2),
    vx,
    vy: initialVy - GRAVITY * time,
    range: vx * duration,
    peak: (initialVy * initialVy) / (2 * GRAVITY),
  };
}
// Equal x/y scale preserves angles. Extents cover all supported launch settings.
export const PLOT = {
  left: 64,
  ground: 350,
  scale: 7,
  width: 1000,
  height: 410,
};
export function trajectory(angle: number, speed: number, elapsed?: number) {
  const { duration } = projectile(angle, speed, 0);
  return Array.from({ length: 81 }, (_, i) => {
    const p = projectile(
      angle,
      speed,
      (Math.max(0, Math.min(elapsed ?? duration, duration)) * i) / 80,
    );
    return `${i ? "L" : "M"}${PLOT.left + p.x * PLOT.scale},${PLOT.ground - p.y * PLOT.scale}`;
  }).join(" ");
}
