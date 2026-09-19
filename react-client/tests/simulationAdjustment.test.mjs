import assert from "node:assert/strict";
import { test } from "node:test";
import { setImmediate } from "node:timers/promises";
import { createSimulationAdjustment } from "../src/utils/simulationAdjustment.ts";

function setup(t) {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  const adjustment = createSimulationAdjustment();
  const callbacks = { success: t.mock.fn(), error: t.mock.fn(), settled: t.mock.fn() };
  return { adjustment, callbacks };
}

test("rapid slider edits send only the last request", async t => {
  const { adjustment, callbacks } = setup(t);
  const first = t.mock.fn(async () => 1);
  const last = t.mock.fn(async () => 2);
  adjustment.schedule(first, callbacks);
  t.mock.timers.tick(100);
  adjustment.schedule(last, callbacks);
  t.mock.timers.tick(180);
  await setImmediate();
  assert.equal(first.mock.callCount(), 0);
  assert.equal(last.mock.callCount(), 1);
  assert.equal(callbacks.success.mock.calls[0].arguments[0], 2);
  assert.equal(callbacks.settled.mock.callCount(), 1);
});

test("a response with missing or normalized parameters settles without submitting again", async t => {
  const { adjustment, callbacks } = setup(t);
  const request = t.mock.fn(async () => ({ parameters: {}, time: [0, 1] }));
  adjustment.schedule(request, callbacks);
  t.mock.timers.tick(180);
  await setImmediate();
  t.mock.timers.tick(2000);
  await setImmediate();
  assert.equal(request.mock.callCount(), 1);
  assert.equal(callbacks.success.mock.callCount(), 1);
  assert.equal(callbacks.settled.mock.callCount(), 1);
});

test("an old response cannot replace the latest simulation or clear its pending state", async t => {
  const { adjustment, callbacks } = setup(t);
  let resolveOld;
  adjustment.schedule(() => new Promise(resolve => { resolveOld = resolve; }), callbacks);
  t.mock.timers.tick(180);
  const latest = { success: t.mock.fn(), error: t.mock.fn(), settled: t.mock.fn() };
  adjustment.schedule(async () => "latest", latest);
  resolveOld("old");
  await setImmediate();
  assert.equal(callbacks.success.mock.callCount(), 0);
  assert.equal(callbacks.settled.mock.callCount(), 0);
  t.mock.timers.tick(180);
  await setImmediate();
  assert.equal(latest.success.mock.calls[0].arguments[0], "latest");
  assert.equal(latest.settled.mock.callCount(), 1);
});

test("reset, invalid input or navigation cancels queued and in-flight results", async t => {
  const { adjustment, callbacks } = setup(t);
  const queued = t.mock.fn(async () => "queued");
  adjustment.schedule(queued, callbacks);
  adjustment.cancel();
  t.mock.timers.tick(180);
  assert.equal(queued.mock.callCount(), 0);
  let resolvePending;
  adjustment.schedule(() => new Promise(resolve => { resolvePending = resolve; }), callbacks);
  t.mock.timers.tick(180);
  adjustment.cancel();
  resolvePending("stale");
  await setImmediate();
  assert.equal(callbacks.success.mock.callCount(), 0);
  assert.equal(callbacks.settled.mock.callCount(), 0);
});

test("network errors finish the updating state and allow another edit", async t => {
  const { adjustment, callbacks } = setup(t);
  adjustment.schedule(async () => { throw new Error("timeout"); }, callbacks);
  t.mock.timers.tick(180);
  await setImmediate();
  assert.equal(callbacks.error.mock.callCount(), 1);
  assert.equal(callbacks.settled.mock.callCount(), 1);
  adjustment.schedule(async () => "recovered", callbacks);
  t.mock.timers.tick(180);
  await setImmediate();
  assert.equal(callbacks.success.mock.calls[0].arguments[0], "recovered");
  assert.equal(callbacks.settled.mock.callCount(), 2);
});
