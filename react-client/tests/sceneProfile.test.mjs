import test from "node:test";
import assert from "node:assert/strict";
import { declaredScenePresentation, inferScenePresentation } from "../src/simulation-scene/SceneProfile.ts";

function simulation(scene, series, extra = {}) {
  return {
    visualization: { scene, series },
    scalarFields: extra.scalarFields,
  };
}

const series = (key, label = key) => ({ source: `values.${key}`, key, label, symbol: key, unit: "1", color: "#38bdf8" });

test("scene profiles use only backend-declared visual intent", () => {
  const profile = declaredScenePresentation({
    visualization: {
      scene: "arbitrary_teacher_name",
      series: [],
      presentation: { environment: "board.circuit", layout: "circuitBoard", effects: ["circuit.current-flow"] },
    },
  });
  assert.equal(profile.environment, "board.circuit");
  assert.equal(profile.layout, "circuitBoard");
});

test("scene names and series labels do not trigger frontend classification", () => {
  const profile = inferScenePresentation(simulation("light_interference", [series("intensity", "Light intensity")]));
  assert.deepEqual(profile, {});
});

test("explicit empty intent stays empty instead of inventing apparatus", () => {
  const profile = declaredScenePresentation({ visualization: { scene: "unknown", series: [], presentation: {} } });
  assert.deepEqual(profile, {});
});
