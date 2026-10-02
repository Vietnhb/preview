import test from "node:test";
import assert from "node:assert/strict";
import { describeScene, niceStep, formatNumber, presentationRate, fastestOscillation } from "../src/features/simulation/model/sceneModel.ts";

const run = (duration, f, steps = 200) => ({ durationSeconds: duration, frames: Array.from({ length: steps + 1 }, (_, i) => {
  const t = duration * i / steps; return { t, values: { t, ...f(t) } };
}) });

test("infers participants and dimensions from quantity semantics, not lesson identity", () => {
  const timeline = run(2, t => ({ "a.position": 3 * t, "a.velocity": 3, "b.x": t, "b.y": 5 - 4.9 * t * t, "b.vx": 1, "b.vy": -9.8 * t }));
  const scene = describeScene(timeline, [{ id: "a", label: "Xe" }, { id: "b" }]);
  assert.deepEqual(scene.participants.map(p => [p.id, p.label, p.dims]), [["a", "Xe", 1], ["b", "b", 2]]);
  assert.equal(scene.fields["a.velocity"].unit, "m/s");
  assert.equal(scene.fields["b.y"].min < scene.fields["b.y"].max, true);
});
test("detects a fixed-length link and a restoring spring from solver data", () => {
  const pendulum = run(3, t => { const th = 0.4 * Math.cos(2.5 * t); return { "p.angle": th, "p.x": 2 * Math.sin(th), "p.y": -2 * Math.cos(th) }; });
  assert.ok(Math.abs(describeScene(pendulum).participants[0].link.radius - 2) < 1e-9);
  const spring = run(3, t => ({ "s.displacement": 0.1 * Math.cos(6 * t), "s.force": -20 * 0.1 * Math.cos(6 * t) }));
  assert.ok(Math.abs(describeScene(spring).participants[0].spring.stiffness - 20) < 1e-6);
  const drift = run(3, t => ({ "s.displacement": t, "s.force": 2 }));
  assert.equal(describeScene(drift).participants[0].spring, null);
});
test("one-dimensional motion under gravity is drawn vertically", () => {
  const fall = run(3, t => ({ "stone.position": 45 - 5 * t * t, "stone.velocity": -10 * t }));
  const scene = describeScene(fall, [{ id: "stone", inputs: { initial_position: "h", gravitational_acceleration: "g" } }]);
  assert.equal(scene.participants[0].vertical, true);
});
test("unknown quantities still get readable metadata for charts", () => {
  const scene = describeScene(run(1, t => ({ "c.magnetic_flux": t })));
  assert.equal(scene.fields["c.magnetic_flux"].kind, "scalar");
  assert.equal(scene.fields["c.magnetic_flux"].label, "Magnetic flux");
});
test("nice ticks and number formatting", () => {
  assert.equal(niceStep(300, 6), 50);
  assert.equal(niceStep(11.5, 10), 2);
  assert.equal(formatNumber(-2.5), "−2.5");
  assert.equal(formatNumber(0.0001012), "1.01×10⁻⁴");
});
test("elapsed-time states are hidden and connectors follow the capability's declared role", () => {
  const orbit = run(6, t => ({ "s.elapsed_time": t, "s.angle": t, "s.x": 3 * Math.cos(t), "s.y": 3 * Math.sin(t) }));
  const plain = describeScene(orbit, [], { "s.angle": { unit: "rad" }, "s.elapsed_time": { unit: "s" } });
  assert.equal(plain.fields["s.elapsed_time"], undefined);
  assert.equal(plain.participants[0].link, null);
  const tethered = describeScene(orbit, [], { "s.angle": { unit: "rad", rendererRole: "connector_angle" } });
  assert.ok(Math.abs(tethered.participants[0].link.radius - 3) < 1e-9);
});
test("the gravitational constant does not make motion vertical; uniform gravity does", () => {
  const line = run(2, t => ({ "p.position": 2 * t }));
  assert.equal(describeScene(line, [{ id: "p", inputs: { gravitational_constant: 6.67e-11 } }]).participants[0].vertical, false);
  assert.equal(describeScene(line, [{ id: "p", inputs: { gravitational_acceleration: "g" } }]).participants[0].vertical, true);
});
test("the capability's renderer roles decide what is a place in space", () => {
  const stretch = run(5, t => ({ "s.elongation": 0.01 * t, "s.force": t }));
  assert.equal(describeScene(stretch).participants[0].dims, 1);
  assert.equal(describeScene(stretch, [], { "s.elongation": { rendererRole: "state_value" }, "s.force": { rendererRole: "state_value" } }).participants[0].dims, 0);
  const probe = run(1, t => ({ "a.position": 1, "a.pressure": Math.sin(40 * t) }));
  const scene = describeScene(probe);
  assert.equal(scene.participants[0].dims, 1);
  assert.equal(scene.participants[0].stationary, true);
  assert.equal(describeScene(probe, [], { "a.pressure": { rendererRole: "state_value" } }).participants[0].dims, 0);
});
test("replay is slowed so an oscillation stays visible, never faster than the eye can follow", () => {
  const ac = run(10, t => ({ "c.current": Math.sin(2 * Math.PI * 5 * t) }), 4000);
  assert.ok(Math.abs(fastestOscillation(ac) - 5) < 0.2);
  const rate = presentationRate(10, ac);
  assert.ok(5 * rate <= 2.1, "on-screen frequency " + 5 * rate);
  assert.equal(presentationRate(10), 10 / 10);
});
test("tiny SI values are shown instead of rounded to zero", () => {
  assert.equal(formatNumber(5.04e-29, 4, 6e-29), "5.04×10⁻²⁹");
  assert.equal(formatNumber(1e-17, 4, 2), "0");
});
