import type { Simulation, Specification } from "../types/physlive";

const GRAVITY = 9.81;

export type SolverOutput = {
  times: number[];
  positions: Record<string, number[]>;
  velocities: Record<string, number[]>;
  accelerations: Record<string, number[]>;
  values: Record<string, number[]>;
};

function getParam(
  key: string,
  overrides: Record<string, number>,
  simulation: Simulation,
  specification?: Specification,
  fallback = 0
): number {
  const lowerKey = key.toLowerCase();
  for (const [k, v] of Object.entries(overrides)) {
    if (k.toLowerCase() === lowerKey && Number.isFinite(v)) return v;
  }
  if (Number.isFinite(simulation.parameters?.[key])) return simulation.parameters[key];
  if (specification?.quantities) {
    const q = specification.quantities.find(
      item => item.name.toLowerCase() === lowerKey || item.symbol?.toLowerCase() === lowerKey
    );
    if (q && Number.isFinite(q.normalizedValue)) return q.normalizedValue;
  }
  return fallback;
}

export function reSolveSimulation(
  simulation: Simulation,
  overrides: Record<string, number>,
  specification?: Specification
): Simulation {
  const schema = (simulation.schemaId || "").toLowerCase();
  const times = simulation.time;
  if (!times || times.length === 0) return simulation;

  const duration = times[times.length - 1] ?? 0;
  const points = times.length;
  const step = points > 1 ? times[1] - times[0] : 0.05;

  let positions: Record<string, number[]> = { ...simulation.positions };
  let velocities: Record<string, number[]> = { ...simulation.velocities };
  let accelerations: Record<string, number[]> = { ...simulation.accelerations };
  let values: Record<string, number[]> = { ...simulation.values };

  if (schema.includes("projectile") || schema.includes("2d")) {
    const v0 = getParam("initial_velocity", overrides, simulation, specification, 20);
    const angle = getParam("launch_angle", overrides, simulation, specification, Math.PI / 4);
    const x0 = getParam("initial_position", overrides, simulation, specification, 0);
    const y0 = getParam("initial_height", overrides, simulation, specification, 0);

    let vx = v0 * Math.cos(angle);
    let vy = v0 * Math.sin(angle);
    let x = x0;
    let y = y0;

    const xSeries: number[] = [];
    const ySeries: number[] = [];
    const vxSeries: number[] = [];
    const vySeries: number[] = [];
    const axSeries: number[] = [];
    const aySeries: number[] = [];

    for (let i = 0; i < points; i++) {
      const t = times[i];
      xSeries.push(x);
      ySeries.push(y);
      vxSeries.push(vx);
      vySeries.push(vy);
      axSeries.push(0);
      aySeries.push(-GRAVITY);

      if (i === points - 1) break;
      const dt = Math.min(step, duration - t);
      x += vx * dt;
      y += vy * dt - 0.5 * GRAVITY * dt * dt;
      vy -= GRAVITY * dt;
    }

    positions = { x: xSeries, y: ySeries };
    velocities = { x: vxSeries, y: vySeries };
    accelerations = { x: axSeries, y: aySeries };
    values = { ...values, x: xSeries, y: ySeries, vx: vxSeries, vy: vySeries, ax: axSeries, ay: aySeries };
  } else if (schema.includes("collision")) {
    const m1 = Math.max(0.001, getParam("mass_1", overrides, simulation, specification, 2));
    const m2 = Math.max(0.001, getParam("mass_2", overrides, simulation, specification, 3));
    const x1_0 = getParam("initial_position_1", overrides, simulation, specification, -5);
    const x2_0 = getParam("initial_position_2", overrides, simulation, specification, 5);
    const v1 = getParam("velocity_1", overrides, simulation, specification, 4);
    const v2 = getParam("velocity_2", overrides, simulation, specification, -2);

    let collisionAt = -1;
    if (x1_0 < x2_0 && v1 > v2) collisionAt = (x2_0 - x1_0) / (v1 - v2);
    else if (x1_0 > x2_0 && v2 > v1) collisionAt = (x1_0 - x2_0) / (v2 - v1);

    const willCollide = collisionAt > 0;
    const afterV1 = willCollide ? ((m1 - m2) * v1 + 2 * m2 * v2) / (m1 + m2) : v1;
    const afterV2 = willCollide ? ((m2 - m1) * v2 + 2 * m1 * v1) / (m1 + m2) : v2;
    const x1Coll = willCollide ? x1_0 + v1 * collisionAt : 0;
    const x2Coll = willCollide ? x2_0 + v2 * collisionAt : 0;

    const x1Series: number[] = [];
    const x2Series: number[] = [];
    const v1Series: number[] = [];
    const v2Series: number[] = [];

    for (let i = 0; i < points; i++) {
      const t = times[i];
      const curX1 = !willCollide || t <= collisionAt ? x1_0 + v1 * t : x1Coll + afterV1 * (t - collisionAt);
      const curX2 = !willCollide || t <= collisionAt ? x2_0 + v2 * t : x2Coll + afterV2 * (t - collisionAt);
      const curV1 = !willCollide || t < collisionAt ? v1 : afterV1;
      const curV2 = !willCollide || t < collisionAt ? v2 : afterV2;

      x1Series.push(curX1);
      x2Series.push(curX2);
      v1Series.push(curV1);
      v2Series.push(curV2);
    }

    positions = { x1: x1Series, x2: x2Series };
    velocities = { v1: v1Series, v2: v2Series };
    accelerations = {};
    values = {
      ...values,
      x1: x1Series,
      x2: x2Series,
      v1: v1Series,
      v2: v2Series,
      collisionTime: [willCollide ? collisionAt : -1],
      collisionX: [willCollide ? x1Coll : -1]
    };
  } else if (schema.includes("spring") || schema.includes("oscillation")) {
    const amplitude = Math.abs(getParam("amplitude", overrides, simulation, specification, 1));
    const mass = Math.max(0.001, getParam("mass", overrides, simulation, specification, 1));
    const spring = Math.max(0.001, getParam("spring_constant", overrides, simulation, specification, 10));
    const phase = getParam("phase", overrides, simulation, specification, 0);
    const omega = Math.sqrt(spring / mass);

    const xSeries: number[] = [];
    const vxSeries: number[] = [];
    const axSeries: number[] = [];

    for (let i = 0; i < points; i++) {
      const t = times[i];
      const angle = omega * t + phase;
      const x = amplitude * Math.cos(angle);
      const v = -amplitude * omega * Math.sin(angle);
      const a = -omega * omega * x;
      xSeries.push(x);
      vxSeries.push(v);
      axSeries.push(a);
    }

    positions = { x: xSeries };
    velocities = { x: vxSeries };
    accelerations = { x: axSeries };
    values = { ...values, x: xSeries, vx: vxSeries, ax: axSeries };
  } else if (schema.includes("circuit") || schema.includes("rc_")) {
    const discharging = schema.includes("discharging");
    const voltage = getParam("voltage", overrides, simulation, specification, 10);
    const resistance = Math.max(0.001, getParam("resistance", overrides, simulation, specification, 100));
    const capacitance = Math.max(1e-12, getParam("capacitance", overrides, simulation, specification, 1e-4));
    const tau = resistance * capacitance;

    const voltages: number[] = [];
    const currents: number[] = [];

    for (let i = 0; i < points; i++) {
      const t = times[i];
      const decay = Math.exp(-t / tau);
      const capV = discharging ? voltage * decay : voltage * (1 - decay);
      const cur = discharging ? -(voltage / resistance) * decay : (voltage / resistance) * decay;
      voltages.push(capV);
      currents.push(cur);
    }

    positions = {};
    velocities = {};
    accelerations = {};
    values = { ...values, voltage: voltages, current: currents };
  } else if (schema.includes("force") || schema.includes("dynamics")) {
    const mass = Math.max(0.001, getParam("mass", overrides, simulation, specification, 2));
    const force = getParam("net_force", overrides, simulation, specification, 10);
    const friction = Math.max(0, getParam("friction_coefficient", overrides, simulation, specification, 0));
    const acceleration = force / mass - friction * GRAVITY;
    let velocity = getParam("initial_velocity", overrides, simulation, specification, 0);
    let position = getParam("initial_position", overrides, simulation, specification, 0);

    const xSeries: number[] = [];
    const vxSeries: number[] = [];
    const axSeries: number[] = [];
    const netForceSeries: number[] = [];

    for (let i = 0; i < points; i++) {
      const t = times[i];
      xSeries.push(position);
      vxSeries.push(velocity);
      axSeries.push(acceleration);
      netForceSeries.push(force - friction * mass * GRAVITY);

      if (i === points - 1) break;
      const dt = Math.min(step, duration - t);
      position += velocity * dt + 0.5 * acceleration * dt * dt;
      velocity += acceleration * dt;
    }

    positions = { x: xSeries };
    velocities = { x: vxSeries };
    accelerations = { x: axSeries };
    values = { ...values, x: xSeries, vx: vxSeries, ax: axSeries, force: netForceSeries };
  } else {
    // Default 1D uniform acceleration kinematics
    let x = getParam("initial_position", overrides, simulation, specification, 0);
    let vx = getParam("initial_velocity", overrides, simulation, specification, 0);
    const a = getParam("acceleration", overrides, simulation, specification, 2);

    const xSeries: number[] = [];
    const vxSeries: number[] = [];
    const axSeries: number[] = [];

    for (let i = 0; i < points; i++) {
      const t = times[i];
      xSeries.push(x);
      vxSeries.push(vx);
      axSeries.push(a);

      if (i === points - 1) break;
      const dt = Math.min(step, duration - t);
      x += vx * dt + 0.5 * a * dt * dt;
      vx += a * dt;
    }

    positions = { x: xSeries };
    velocities = { x: vxSeries };
    accelerations = { x: axSeries };
    values = { ...values, x: xSeries, vx: vxSeries, ax: axSeries };
  }

  return {
    ...simulation,
    parameters: { ...simulation.parameters, ...overrides },
    positions,
    velocities,
    accelerations,
    values
  };
}
