import assert from "node:assert/strict";
import test from "node:test";
import {
  GRAVITY,
  LAUNCH,
  PLOT,
  projectile,
  trajectory,
} from "../src/components/simulation/projectileModel.ts";

test("projectile lands at analytical range and never crosses the ground", () => {
  const p = projectile(45, 24, 100);
  assert.equal(p.y, 0);
  assert.equal(p.time, p.duration);
  assert.ok(Math.abs(p.range - 24 ** 2 / GRAVITY) < 1e-10);
  assert.equal(p.x, p.range);
  assert.equal(projectile(45, 24, -1).time, 0);
});
test("projectile conserves specific mechanical energy before impact", () => {
  const { duration } = projectile(60, 28, 0);
  for (let i = 0; i <= 100; i++) {
    const p = projectile(60, 28, (duration * i) / 100);
    assert.ok(
      Math.abs((p.vx ** 2 + p.vy ** 2) / 2 + GRAVITY * p.y - 28 ** 2 / 2) <
        1e-8,
    );
  }
});
test("all supported trajectories fit the equal-scale plot", () => {
  for (
    let angle = LAUNCH.angle.min;
    angle <= LAUNCH.angle.max;
    angle += LAUNCH.angle.step
  ) {
    for (
      let speed = LAUNCH.speed.min;
      speed <= LAUNCH.speed.max;
      speed += LAUNCH.speed.step
    ) {
      const p = projectile(angle, speed, 0);
      assert.ok(PLOT.left + p.range * PLOT.scale < PLOT.width);
      assert.ok(PLOT.ground - p.peak * PLOT.scale > 0);
      assert.ok(!trajectory(angle, speed).includes("NaN"));
    }
  }
});
