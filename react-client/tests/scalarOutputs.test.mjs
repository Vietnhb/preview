import assert from "node:assert/strict";
import test from "node:test";
import { prepareSimulationData, seriesFor } from "../src/simulation-runtime/SimulationData.ts";
import { BindingResolver } from "../src/simulation-scene/BindingResolver.ts";

function simulation(overrides = {}) {
  return {
    simulationId: "scalar-run",
    runId: "scalar-run-version",
    specificationId: "spec-1",
    schemaId: "point_charge_field",
    valid: true,
    ready: true,
    time: [0, 1],
    positions: {},
    velocities: {},
    accelerations: {},
    values: {},
    parameters: {},
    visualization: {
      scene: "point_charge_field",
      controls: [],
      series: [{
        key: "electricField",
        source: "scalarOutputs.electricField",
        label: "Electric field magnitude",
        symbol: "E",
        unit: "N/C",
        color: "#38bdf8",
      }],
      presentation: {
        sceneGraph: {
          nodes: [
            { id: "background", type: "background", layer: "static" },
            { id: "field-graph", type: "graph", properties: { source: "scalarOutputs.electricField" } },
          ],
        },
      },
    },
    validation: { passed: true, tolerance: 0, checkpoints: [] },
    elapsedMilliseconds: 0,
    ...overrides,
  };
}

test("scalar outputs resolve as one-value constant sources without entering values", () => {
  const scalarOutputs = { electricField: 299_585.05974333335, electricPotential: 89_875.517923, large: 1e100 };
  const input = simulation({ scalarOutputs });
  const runtime = prepareSimulationData(input);

  const electricField = seriesFor(runtime, "scalarOutputs.electricField");
  assert.ok(electricField instanceof Float64Array);
  assert.deepEqual([...electricField], [scalarOutputs.electricField]);
  assert.equal(seriesFor(runtime, "scalarOutputs.large")[0], 1e100);
  assert.equal(new BindingResolver(runtime).resolve("scalarOutputs.electricField", 0.75, 1), scalarOutputs.electricField);
  assert.deepEqual(runtime.values, {});
  assert.deepEqual(input.values, {});
  assert.equal(runtime.simulation.scalarOutputs, scalarOutputs);
});

test("malformed scalar names and non-finite or non-numeric values are ignored", () => {
  const runtime = prepareSimulationData(simulation({
    scalarOutputs: {
      good: 4.5,
      nan: Number.NaN,
      positiveInfinity: Number.POSITIVE_INFINITY,
      negativeInfinity: Number.NEGATIVE_INFINITY,
      numericString: "5",
      "   ": 6,
    },
  }));

  assert.deepEqual([...seriesFor(runtime, "scalarOutputs.good")], [4.5]);
  for (const key of ["nan", "positiveInfinity", "negativeInfinity", "numericString", "   "]) {
    assert.equal(seriesFor(runtime, `scalarOutputs.${key}`).length, 0, key);
  }
});

test("declared scene graph scalarOutputs source resolves through the runtime series contract", () => {
  const input = simulation({ scalarOutputs: { electricField: 12 } });
  const runtime = prepareSimulationData(input);
  const source = input.visualization.presentation.sceneGraph.nodes[1].properties.source;
  const declaredSource = input.visualization.series[0].source;

  assert.equal(source, declaredSource);
  assert.equal(seriesFor(runtime, source)[0], 12);
  assert.equal(new BindingResolver(runtime).resolve(source, 0.5, 1), 12);
});
