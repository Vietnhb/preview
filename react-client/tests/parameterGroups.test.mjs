import test from "node:test";
import assert from "node:assert/strict";
import { groupParameters, peersAligned } from "../src/features/simulation/model/parameterGroups.ts";

const p = name => ({ name, value: 1 });
test("own, common and peer parameters come from the plan bindings", () => {
  const models = [1, 2, 3].map(i => ({ id: "c" + i, label: "Con lắc " + i, capabilityId: "pendulum", inputs: { length: "L" + i, initial_angle: "a" + i, gravity: "g", mass: 0.5 } }));
  const grouping = groupParameters([p("L1"), p("a1"), p("L2"), p("a2"), p("L3"), p("a3"), p("g"), p("T")], models);
  assert.deepEqual(grouping.shared.map(x => x.name), ["g", "T"]);
  assert.deepEqual(grouping.groups.map(g => [g.label, g.parameters.map(x => x.name)]), [["Con lắc 1", ["L1", "a1"]], ["Con lắc 2", ["L2", "a2"]], ["Con lắc 3", ["L3", "a3"]]]);
  assert.deepEqual(grouping.peers.L2, ["L1", "L2", "L3"]);
  assert.equal(grouping.peers.g, undefined);
});
test("one quantity seen from two sides stays common; different laws are not peers", () => {
  const models = [
    { id: "car", capabilityId: "collision", inputs: { mass: "m", other_mass: "M", initial_position: "x0" } },
    { id: "wall", capabilityId: "collision", inputs: { mass: "M", other_mass: "m", initial_position: "xw" } },
    { id: "ball", capabilityId: "free_fall", inputs: { initial_position: "h" } }];
  const grouping = groupParameters([p("m"), p("M"), p("x0"), p("xw"), p("h")], models);
  assert.deepEqual(grouping.shared.map(x => x.name), ["m", "M"]);
  assert.deepEqual(grouping.peers.x0, ["x0", "xw"]);
  assert.equal(grouping.peers.h, undefined);
});
test("a plan with one participant or shared sliders only has nothing to group", () => {
  const shared = groupParameters([p("L")], [1, 2].map(i => ({ id: "c" + i, capabilityId: "pendulum", inputs: { length: "L" } })));
  assert.deepEqual([shared.shared.length, shared.groups.length, Object.keys(shared.peers).length], [1, 0, 0]);
});
test("peers are aligned only while like objects hold the same value", () => {
  const peers = { a: ["a", "b"], b: ["a", "b"] };
  assert.equal(peersAligned(peers, { a: 2, b: 2 }, () => 0), true);
  assert.equal(peersAligned(peers, { a: 2 }, () => 2), true);
  assert.equal(peersAligned(peers, { a: 2, b: 3 }, () => 0), false);
});
