/** Stable scene language understood by the frontend renderer. */
export const SUPPORTED_PRIMITIVES = [
  "background", "grid", "environment", "ruler", "body", "circle", "rectangle", "line", "arrow", "vector",
  "trajectory", "spring", "rope", "waveField", "prop", "effect", "text", "graph", "chart", "circuitComponent", "vectorScene",
  "lens", "ray",
] as const;

export type SupportedPrimitive = typeof SUPPORTED_PRIMITIVES[number];
export const AUTHORABLE_PRIMITIVES = SUPPORTED_PRIMITIVES.filter(type => type !== "lens" && type !== "ray");

/** Compatibility effects used by versioned historical presentations. */
export const SUPPORTED_EFFECTS = [
  "motion.trail", "vehicle.headlight", "vehicle.brake-smoke", "projectile.glow",
  "collision.flash", "circuit.current-flow", "circuit.capacitor-glow",
] as const;

/** Renderer capabilities are an allowlist, not a physics classifier. */
export const SUPPORTED_ENVIRONMENTS = [
  "track.engineering", "road.highway", "range.projectile", "track.collision", "bench.spring",
  "board.circuit", "room.thermodynamics", "room.acoustics", "wave.tank", "optical.bench",
  "lab.radiation", "lab.measurement",
] as const;

export const SUPPORTED_LAYOUTS = [
  "dataPlane", "lanes", "horizontalTrack", "projectileRange", "collisionTrack", "springBench",
  "circuitBoard", "world",
] as const;

export function isSupportedPrimitive(value: string): value is SupportedPrimitive {
  return (SUPPORTED_PRIMITIVES as readonly string[]).includes(value);
}

export function isSupportedEffect(value: string): boolean {
  return (SUPPORTED_EFFECTS as readonly string[]).includes(value);
}

export function isSupportedEnvironment(value: string): boolean {
  return (SUPPORTED_ENVIRONMENTS as readonly string[]).includes(value);
}

export function isSupportedLayout(value: string): boolean {
  return (SUPPORTED_LAYOUTS as readonly string[]).includes(value);
}
