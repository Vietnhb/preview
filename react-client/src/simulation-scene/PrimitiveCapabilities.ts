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

export function isSupportedPrimitive(value: string): value is SupportedPrimitive {
  return (SUPPORTED_PRIMITIVES as readonly string[]).includes(value);
}
