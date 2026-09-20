import test from "node:test";
import assert from "node:assert/strict";
import { normalizeScalarField, prepareSimulationData, probeScalarField, sampleScalarField } from "../src/simulation-runtime/SimulationData.ts";

function field(overrides = {}) {
  return {
    id: "f",
    version: 1,
    type: "scalarField",
    physicalDimension: 1,
    axes: [{ key: "x", coordinates: [0, 1], unit: "m" }],
    shape: [2, 2],
    time: [0, 1],
    values: [[0, 1], [1, 2]],
    valueUnit: "m",
    timeUnit: "s",
    interpolation: "linear",
    ...overrides,
  };
}

test("normalizes and bilinearly samples a scalar field", () => {
  const result = normalizeScalarField(field());
  assert.deepEqual(result.errors, []);
  assert.ok(result.field);
  assert.equal(sampleScalarField(result.field, 0.5, 0.5), 1);
});

test("accepts a non-displacement physical value unit such as pascals", () => {
  const result = normalizeScalarField(field({ valueUnit: "Pa" }));
  assert.deepEqual(result.errors, []);
  assert.equal(result.field?.valueUnit, "Pa");
});

test("probe reports clamping at the physical boundary", () => {
  const result = normalizeScalarField(field());
  assert.ok(result.field);
  const probe = probeScalarField(result.field, 2, -1);
  assert.deepEqual(probe, {
    fieldId: "f",
    requestedX: 2,
    requestedTime: -1,
    x: 1,
    time: 0,
    value: 1,
    clamped: true,
  });
});

test("rejects a shape that does not match the sample matrix", () => {
  const result = normalizeScalarField(field({ shape: [2, 3] }));
  assert.ok(result.errors.some((error) => error.includes("shape must equal")));
  assert.equal(result.field, undefined);
});

test("accepts the backend object-map representation during replay", () => {
  const simulation = {
    simulationId: "s",
    runId: "r",
    specificationId: "p",
    schemaId: "string_wave",
    valid: true,
    ready: true,
    time: [0, 1],
    positions: {},
    velocities: {},
    accelerations: {},
    values: {},
    scalarFields: { transverseDisplacement: field({ id: "transverseDisplacement" }) },
    parameters: {},
    visualization: { scene: "string_wave", controls: [], series: [] },
    validation: { passed: true, tolerance: 0, checkpoints: [] },
    elapsedMilliseconds: 0,
  };
  const runtime = prepareSimulationData(simulation);
  assert.equal(runtime.scalarFields.has("transverseDisplacement"), true);
  assert.equal(sampleScalarField(runtime.scalarFields.get("transverseDisplacement"), 0.5, 0.5), 1);
});

test("normalizes and interpolates a plane scalar field", () => {
  const result = normalizeScalarField(field({
    id: "waterSurface",
    physicalDimension: 2,
    axes: [
      { key: "x", coordinates: [0, 1], unit: "m" },
      { key: "y", coordinates: [0, 1], unit: "m" },
    ],
    shape: [2, 2, 2],
    values: [[0, 1, 2, 3], [1, 2, 3, 4]],
  }));
  assert.deepEqual(result.errors, []);
  assert.ok(result.field);
  assert.equal(sampleScalarField(result.field, 0.5, 0.5, 0.5), 2);
  assert.deepEqual(probeScalarField(result.field, 0.5, 0.5, 0.5), {
    fieldId: "waterSurface", requestedX: 0.5, requestedTime: 0.5, requestedY: 0.5,
    x: 0.5, y: 0.5, time: 0.5, value: 2, clamped: false,
  });
});
