import test from "node:test";
import assert from "node:assert/strict";
import { compileSceneGraph } from "../src/simulation-scene/SceneGraph.ts";

const simulation = (actor, effects = ["motion.trail"]) => ({
  simulationId: "scene-test",
  visualization: {
    scene: "teacher-scene",
    series: [{ key: "position", source: "positions.x", unit: "m" }],
    presentation: { actors: [actor], effects },
  },
});

test("one-dimensional position scenes use the ruler without an unexplained trail", () => {
  const nodes = compileSceneGraph(simulation({ id: "body", x: "positions.x" })).nodes;
  assert.equal(nodes.filter(node => node.type === "ruler").length, 1);
  assert.equal(nodes.filter(node => node.type === "trajectory").length, 0);
  assert.equal(nodes.find(node => node.type === "ruler")?.properties.unit, "m");
  assert.equal(nodes.find(node => node.type === "ruler")?.properties.originSource, "positions.x");
});

test("two-dimensional position scenes retain the declared trajectory", () => {
  const nodes = compileSceneGraph(simulation({ id: "body", x: "positions.x", y: "positions.y" })).nodes;
  assert.equal(nodes.filter(node => node.type === "ruler").length, 1);
  assert.equal(nodes.filter(node => node.type === "trajectory").length, 1);
});
