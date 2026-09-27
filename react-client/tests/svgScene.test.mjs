import test from "node:test";
import assert from "node:assert/strict";
import { sampleTimeline } from "../src/simulation/svgScene.ts";

test("playback interpolates every participant from backend samples", () => {
  const timeline = { durationSeconds: 2, frames: [
    { t: 0, values: { t: 0, "first.x": 0, "second.x": 10 } },
    { t: 2, values: { t: 2, "first.x": 4, "second.x": 20 } },
  ] };
  assert.deepEqual(sampleTimeline(timeline, 1), { t: 1, "first.x": 2, "second.x": 15 });
  assert.deepEqual(sampleTimeline(timeline, 20), { t: 2, "first.x": 4, "second.x": 20 });
});
