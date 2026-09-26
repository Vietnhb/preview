import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import Matter from "matter-js";
import { validateMatterCode } from "../src/matter-flow/codeSafety.ts";
import { validateEngineResolution } from "../src/matter-flow/validation-core.mjs";

const simpleCode = `
  const engine = Matter.Engine.create();
  engine.gravity.y = 0;
  const body = Matter.Bodies.circle(120, 120, 15, { friction: 0, frictionAir: 0, restitution: 1 });
  Matter.Composite.add(engine.world, body);
  Matter.Body.setVelocity(body, { x: params.speed, y: 0 });
  return engine;
`;

test("generated code gate accepts only the scoped Matter setup API", () => {
  assert.equal(validateMatterCode(simpleCode, ["speed"]), null);
  for (const code of [
    "fetch('https://example.com'); return Matter.Engine.create();",
    "const engine = Matter.Engine.create(); document.body.innerHTML = ''; return engine;",
    "const engine = Matter.Engine.create(); while (true) {} return engine;",
    "const engine = Matter.Engine.create(); Matter.Engine.create.constructor('return 1')(); return engine;",
    "const engine = Matter.Engine.create(); params['speed']; return engine;",
    "const engine = Matter.Engine.create(); Matter.Bodies.polygon(0, 0, 999999); return engine;",
    "const engine = Matter.Engine.create(); const y = engine.world.bounds.max.y; return engine;",
  ]) assert.notEqual(validateMatterCode(code, ["speed"]), null);
});

test("generic background checks flag declared force-free systems with momentum drift", () => {
  const unstableCode = `
    const engine = Matter.Engine.create();
    engine.gravity.y = 0;
    const body = Matter.Bodies.circle(120, 120, 15, { friction: 0, frictionAir: 0, restitution: 1 });
    Matter.Composite.add(engine.world, body);
    Matter.Body.applyForce(body, body.position, { x: 0.1, y: 0 });
    return engine;
  `;
  assert.equal(validateMatterCode(unstableCode, []), null);
  const setup = new Function("Matter", "params", "width", "height", unstableCode);
  const result = validateEngineResolution(Matter, () => setup(Matter, {}, 960, 540), {},
    { externalForces: false, friction: false, conservativeInteractions: true }, 960, 540);
  assert.equal(result.status, "FLAGGED");
  assert.ok(result.metrics.maxRelativeMomentumDrift > 0.05);
  assert.ok(result.flags.some((flag) => flag.includes("Momentum")));
});

test("relative momentum and energy drift are preserved across coordinate scales", () => {
  const results = [0.0001, 1000].map((coordinateScale) => {
    const setup = () => {
      const engine = Matter.Engine.create();
      engine.gravity.x = 0; engine.gravity.y = 0;
      const body = Matter.Bodies.circle(0, 0, 15 * coordinateScale,
        { friction: 0, frictionAir: 0, restitution: 1 });
      Matter.Body.setMass(body, 1);
      Matter.Body.setVelocity(body, { x: coordinateScale, y: 0 });
      Matter.Composite.add(engine.world, body);
      // Deliberately omit this accelerating force from the declared closed system.
      Matter.Events.on(engine, "beforeUpdate", () => {
        Matter.Body.applyForce(body, body.position, { x: 0.001 * coordinateScale, y: 0 });
      });
      return engine;
    };
    return validateEngineResolution(Matter, setup, {},
      { externalForces: false, friction: false, conservativeInteractions: true,
        durationSeconds: 0.1 }, 960, 540);
  });
  for (const result of results) {
    assert.equal(result.status, "FLAGGED");
    assert.ok(result.flags.some((flag) => flag.includes("Momentum drift")));
    assert.ok(result.flags.some((flag) => flag.includes("Energy drift")));
  }
  for (const metric of ["maxRelativeMomentumDrift", "maxRelativeEnergyDrift"])
    assert.ok(Math.abs(results[0].metrics[metric] / results[1].metrics[metric] - 1) < 1e-8,
      `${metric} should be independent of coordinate scale`);
});

test("swept collision risk flags a fast body crossing a thin obstacle even when both timesteps tunnel", () => {
  const setup = () => {
    const engine = Matter.Engine.create();
    engine.gravity.y = 0;
    const puck = Matter.Bodies.circle(100, 200, 10, { friction: 0, frictionAir: 0 });
    const wall = Matter.Bodies.rectangle(300, 200, 5, 80, { isStatic: true });
    Matter.Composite.add(engine.world, [puck, wall]);
    Matter.Body.setVelocity(puck, { x: 250, y: 0 });
    return engine;
  };
  const result = validateEngineResolution(Matter, setup, {},
    { externalForces: false, friction: false, conservativeInteractions: false, durationSeconds: 0.1 }, 960, 540);
  assert.equal(result.status, "FLAGGED");
  assert.ok(result.metrics.maxSweptTravelToFeatureRatio > 1);
  assert.ok(result.flags.some((flag) => flag.includes("collision feature")));
});

test("swept collision risk uses relative motion when two fast bodies meet between frames", () => {
  const setup = () => {
    const engine = Matter.Engine.create();
    engine.gravity.y = 0;
    const a = Matter.Bodies.circle(100, 200, 5, { friction: 0, frictionAir: 0 });
    const b = Matter.Bodies.circle(250, 200, 5, { friction: 0, frictionAir: 0 });
    Matter.Composite.add(engine.world, [a, b]);
    Matter.Body.setVelocity(a, { x: 100, y: 0 });
    Matter.Body.setVelocity(b, { x: -100, y: 0 });
    return engine;
  };
  const result = validateEngineResolution(Matter, setup, {},
    { externalForces: false, friction: false, conservativeInteractions: false,
      durationSeconds: 1 / 60 }, 960, 540);
  assert.equal(result.status, "FLAGGED");
  assert.ok(result.metrics.maxSweptTravelToFeatureRatio > 1);
  assert.ok(result.flags.some((flag) => flag.includes("collision feature")));
});

test("ordinary bouncing near a thin floor does not trigger the swept collision warning", () => {
  const setup = () => {
    const engine = Matter.Engine.create();
    engine.gravity.y = 0;
    const ball = Matter.Bodies.circle(100, 100, 20, { friction: 0, frictionAir: 0, restitution: 1,
      plugin: { physliveId: "ball" } });
    const floor = Matter.Bodies.rectangle(100, 200, 300, 10, { isStatic: true,
      plugin: { physliveId: "floor" } });
    Matter.Composite.add(engine.world, [ball, floor]);
    Matter.Body.setVelocity(ball, { x: 0, y: 9.8 });
    return engine;
  };
  const result = validateEngineResolution(Matter, setup, {},
    { externalForces: false, friction: false, conservativeInteractions: true, durationSeconds: 2,
      expectedContacts: [["ball", "floor"]] }, 960, 540);
  assert.ok(result.metrics.maxSweptTravelToFeatureRatio > 0);
  assert.ok(result.metrics.maxSweptTravelToFeatureRatio <= 1);
  assert.equal(result.metrics.observedExpectedContactCount, 1);
  assert.ok(result.flags.every((flag) => !flag.includes("collision feature")));
  assert.ok(result.flags.every((flag) => !flag.includes("Expected contact")));
});

test("declared contact is flagged when the generated scene moves objects apart", () => {
  const setup = () => {
    const engine = Matter.Engine.create();
    engine.gravity.y = 1;
    const ball = Matter.Bodies.circle(100, 200, 20, { plugin: { physliveId: "ball" } });
    const floor = Matter.Bodies.rectangle(100, 100, 300, 10, { isStatic: true,
      plugin: { physliveId: "floor" } });
    Matter.Composite.add(engine.world, [ball, floor]);
    return engine;
  };
  const result = validateEngineResolution(Matter, setup, {},
    { durationSeconds: 1, expectedContacts: [["ball", "floor"]] }, 960, 540);
  assert.equal(result.status, "FLAGGED");
  assert.equal(result.metrics.observedExpectedContactCount, 0);
  assert.ok(result.flags.some((flag) => flag.includes("Expected contact between ball and floor")));
});

test("parameter updates are local to the sandbox setup", async () => {
  const workspace = await readFile(new URL("../src/pages/teacher/MatterPipelineWorkspace.tsx", import.meta.url), "utf8");
  const updateFunction = workspace.slice(workspace.indexOf("const updateParameter ="), workspace.indexOf("const recognitionLowConfidence"));
  assert.match(updateFunction, /setValues\(/);
  assert.match(workspace, /setRunValues\(values\);\s*setSandboxKey\(/);
  assert.doesNotMatch(updateFunction, /normalizeMatter|reviseMatter|confirmMatter|axiosClient|fetch\(/);
  assert.match(workspace, /void reportMatterValidation\(simulation\.sessionId, result, runValues\)/);
  assert.doesNotMatch(workspace, /if \(locallyAdjusted\) return;/);
  assert.doesNotMatch(workspace, /getMatterValidation\(/);
});
