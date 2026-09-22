import test from "node:test";
import assert from "node:assert/strict";
import {
  ambiguityQuestionPrefix,
  retainAmbiguityAnswer,
} from "../src/components/workspace/create-simulation/ambiguityFlow.ts";

test("ambiguity answers accumulate instead of clearing earlier responses", () => {
  const first = retainAmbiguityAnswer({}, "mass_1", " 1 ");
  const second = retainAmbiguityAnswer(first, "mass_2", "7");
  const third = retainAmbiguityAnswer(second, "velocity_1", "100 km/h");

  assert.deepEqual(third, {
    mass_1: "1",
    mass_2: "7",
    velocity_1: "100 km/h",
  });
});

test("recorded ambiguity questions use a code-safe stable prefix", () => {
  assert.equal(
    ambiguityQuestionPrefix("schema.required:v_2"),
    "ambiguity-question:schema.required%3Av_2:",
  );
});
