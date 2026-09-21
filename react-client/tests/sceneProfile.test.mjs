import test from "node:test";
import assert from "node:assert/strict";
import { inferScenePresentation } from "../src/simulation-scene/SceneProfile.ts";

function simulation(scene, series, extra = {}) {
  return {
    visualization: { scene, series },
    scalarFields: extra.scalarFields,
  };
}

const series = (key, label = key) => ({ source: `values.${key}`, key, label, symbol: key, unit: "1", color: "#38bdf8" });

test("scene profiles choose physical environments from capabilities, not schema ids", () => {
  const profile = inferScenePresentation(simulation("diode_characteristic", [series("current", "Current"), series("power", "Power")]));
  assert.equal(profile.family, "circuit");
  assert.equal(profile.environment, "board.circuit");
  assert.equal(profile.layout, "circuitBoard");
});

test("optical interference is not mistaken for a mechanical wave", () => {
  const profile = inferScenePresentation(simulation("light_interference", [series("intensity", "Light intensity")]));
  assert.equal(profile.family, "optics");
  assert.equal(profile.environment, "optical.bench");
});

test("scalar wave profiles expose the field to the generic scene graph", () => {
  const profile = inferScenePresentation(simulation("wave_superposition", [series("displacement")], {
    scalarFields: { superpositionDisplacement: { id: "superpositionDisplacement" } },
  }));
  assert.equal(profile.family, "wave");
  assert.equal(profile.environment, "track.engineering");
  assert.equal(profile.wave?.fieldId, "superpositionDisplacement");
});

test("mechanics profiles bind an actor only when a compatible series exists", () => {
  const profile = inferScenePresentation(simulation("linear_drag_motion", [series("position"), series("velocity")]));
  assert.equal(profile.family, "mechanics");
  assert.equal(profile.actors?.[0]?.x, "values.position");
  assert.equal(profile.actors?.[0]?.vx, "values.velocity");
});

test("thermal and measurement profiles expose apparatus assets", () => {
  const thermal = inferScenePresentation(simulation("ideal_gas_isothermal", [series("finalVolume", "Final volume")]));
  const measurement = inferScenePresentation(simulation("measurement_uncertainty", [series("measuredValue", "Measured value")]));
  assert.equal(thermal.props?.[0], "thermal.piston");
  assert.equal(measurement.props?.[0], "measurement.probe");
});
