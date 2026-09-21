import type {
  Simulation,
  VisualizationActor,
  VisualizationPresentation,
  VisualizationSeries,
} from "../types/physlive";

export type SceneFamily = "mechanics" | "wave" | "circuit" | "optics" | "thermal" | "modern" | "measurement" | "astronomy" | "applied";

export type InferredScenePresentation = VisualizationPresentation & {
  layout?: "dataPlane" | "horizontalTrack" | "projectileRange" | "collisionTrack" | "springBench" | "circuitBoard" | "world";
  family: SceneFamily;
};

function corpus(simulation: Simulation): string {
  const visualization = simulation.visualization;
  return [
    visualization.scene,
    ...(visualization.series ?? []).flatMap(series => [series.key, series.source, series.label, series.symbol]),
  ].join(" ").toLowerCase();
}

function contains(text: string, ...terms: string[]): boolean {
  return terms.some(term => text.includes(term));
}

function sourceFor(series: VisualizationSeries[], matcher: RegExp): string | undefined {
  return series.find(item => matcher.test(`${item.key} ${item.source} ${item.label}`.toLowerCase()))?.source;
}

function scalarFieldId(simulation: Simulation): string | undefined {
  const fields = simulation.scalarFields;
  if (Array.isArray(fields)) return fields.find(field => field.id)?.id;
  if (fields && typeof fields === "object") return Object.keys(fields)[0];
  return undefined;
}

function actor(
  id: string,
  asset: string,
  x: string | undefined,
  y?: string,
  vx?: string,
  ax?: string,
  label?: string,
): VisualizationActor | undefined {
  if (!x) return undefined;
  return { id, asset, x, ...(y ? { y } : {}), ...(vx ? { vx } : {}), ...(ax ? { ax } : {}), ...(label ? { label } : {}) };
}

/**
 * Infer only renderer capabilities from declared scene/series metadata.
 * This is deliberately schema-id agnostic: a new schema can reuse the same
 * physical capability vocabulary without requiring a frontend code change.
 */
export function inferScenePresentation(simulation: Simulation): InferredScenePresentation {
  const text = corpus(simulation);
  const series = simulation.visualization.series ?? [];
  const scalarField = scalarFieldId(simulation);
  let family: SceneFamily = "measurement";
  let environment = "lab.measurement";
  let layout: InferredScenePresentation["layout"] = "dataPlane";
  let actors: VisualizationActor[] = [];
  let props: string[] = [];
  let effects: string[] = [];

  if (contains(text, "projectile", "ballistic", "parabola")) {
    family = "mechanics"; environment = "range.projectile"; layout = "projectileRange";
    const x = sourceFor(series, /position.?x|horizontal|range|\bx\b/);
    const y = sourceFor(series, /position.?y|vertical|height|\by\b/);
    const projectile = actor("projectile", "projectile.energy", x, y, sourceFor(series, /velocity.?x|horizontal.?speed/));
    if (projectile) actors = [projectile];
    props = ["structure.launch-tower", "launcher.cannon"];
    effects = ["motion.trail", "projectile.glow"];
  } else if (contains(text, "collision", "impact", "inelastic")) {
    family = "mechanics"; environment = "track.collision"; layout = "collisionTrack";
    const x1 = sourceFor(series, /position.?1|x.?1|body.?1/);
    const x2 = sourceFor(series, /position.?2|x.?2|body.?2/);
    const first = actor("body-1", "object.cart.blue", x1, undefined, sourceFor(series, /velocity.?1|v.?1/), undefined, "1");
    const second = actor("body-2", "object.cart.orange", x2, undefined, sourceFor(series, /velocity.?2|v.?2/), undefined, "2");
    actors = [first, second].filter((item): item is VisualizationActor => Boolean(item));
    effects = ["motion.trail", "collision.flash"];
  } else if (contains(text, "spring", "hooke", "oscillation", "damped")) {
    family = "mechanics"; environment = "bench.spring"; layout = "springBench";
    const x = sourceFor(series, /displacement|position|elongation|\bx\b/);
    const mass = actor("mass", "object.block.amber", x, undefined, sourceFor(series, /velocity/), sourceFor(series, /acceleration/), "m");
    if (mass) actors = [mass];
    props = ["spring.coil"];
    effects = ["motion.trail"];
  } else if (contains(text, "eclipse", "astronomy", "planet", "solar system")) {
    family = "astronomy"; environment = "optical.bench"; layout = "horizontalTrack"; props = ["astronomy.system"];
  } else if (contains(text, "renewable", "emission", "environment", "solar", "energy_environment")) {
    family = "applied"; environment = "lab.measurement"; props = ["energy.solar-panel"];
  } else if (contains(text, "thermal", "thermodynamic", "thermodynamics", "temperature", "gas", "calorimetry", "heat", "phase")) {
    family = "thermal"; environment = "room.thermodynamics";
    if (contains(text, "calorimetry", "heat")) props = ["thermal.calorimeter"];
    else if (contains(text, "gas", "isothermal", "isobaric", "isochoric", "adiabatic")) props = ["thermal.piston"];
    else props = ["thermal.thermometer"];
  } else if (contains(text, "radiation", "radioactive", "nuclear", "atomic", "photoelectric", "xray", "ct_", "mri", "de_broglie", "energy_band")) {
    family = "modern"; environment = "lab.radiation";
    if (contains(text, "xray", "ct_", "mri", "radiation", "radioactive")) props = ["modern.detector"];
    else if (contains(text, "atomic", "nuclear", "energy_band", "photoelectric", "de_broglie")) props = ["modern.atom", "modern.photon"];
  } else if (contains(text, "circuit", "resistor", "ohm", "diode", "capacitor", "transformer", "induction", "op_amp", "op amp", "current", "voltage", "electric", "magnetic")) {
    family = "circuit"; environment = "board.circuit"; layout = "circuitBoard";
    if (contains(text, "diode")) props = ["circuit.battery", "circuit.diode"];
    else if (contains(text, "capacitor", "rc_")) props = ["circuit.battery", "circuit.resistor", "circuit.capacitor"];
    else props = ["circuit.battery", "circuit.resistor"];
  } else if (contains(text, "light", "optic", "lens", "refraction", "diffraction", "polarization", "interference", "magnifier", "microscope", "telescope")) {
    family = "optics"; environment = "optical.bench"; layout = "horizontalTrack";
    props = contains(text, "diffraction", "interference", "polarization") ? ["optics.slit"] : contains(text, "reflection", "mirror") ? ["optics.mirror"] : ["optics.lens"];
  } else if (contains(text, "wave", "sound", "ultrasound", "acoustic", "radio", "signal", "communication", "modulation", "waveform") && !contains(text, "light", "lens", "optic", "refraction", "diffraction")) {
    family = "wave";
    environment = contains(text, "sound", "ultrasound", "acoustic") ? "room.acoustics" : contains(text, "water", "surface") ? "wave.tank" : "track.engineering";
    layout = "horizontalTrack";
    if (contains(text, "sound", "ultrasound", "acoustic")) props = ["wave.speaker"];
    else if (contains(text, "radio", "signal", "communication", "modulation")) props = ["wave.antenna"];
  } else if (contains(text, "measurement", "uncertainty", "experimental", "position_time", "velocity_time", "acceleration_time")) {
    family = "measurement"; environment = "lab.measurement"; props = ["measurement.probe"];
  } else if (contains(text, "motion", "kinematic", "drag", "force", "gravity", "orbit", "moment", "hydrostatic", "energy", "power")) {
    family = "mechanics"; environment = "road.highway";
    const x = sourceFor(series, /position|displacement|distance|\bx\b/);
    const body = actor("body", "object.block.cyan", x, undefined, sourceFor(series, /velocity|speed/), sourceFor(series, /acceleration/));
    if (body) actors = [body];
    if (contains(text, "moment", "equilibrium")) props = ["mechanics.lever"];
    else if (contains(text, "hydrostatic", "buoyant", "buoyancy")) props = ["mechanics.buoy"];
    else if (contains(text, "work", "energy", "force", "power")) props = ["mechanics.pulley"];
    else if (contains(text, "gravity", "orbit")) props = ["mechanics.planet-system"];
    effects = ["motion.trail"];
  }

  const wave = family === "wave" && scalarField
    ? { fieldId: scalarField, probeX: 0, displayExaggeration: 1 }
    : undefined;
  return {
    family,
    theme: "lab-night",
    environment,
    layout,
    actors,
    props,
    effects,
    ...(wave ? { wave } : {}),
  };
}
