import { readFile } from "node:fs/promises";
import Matter from "matter-js";
import { validateMatterCode } from "../src/matter-flow/codeSafety.ts";
import { validateEngineResolution } from "../src/matter-flow/validation-core.mjs";

const artifactPath = process.argv[2];
if (!artifactPath) {
  process.stderr.write("Usage: node --experimental-strip-types scripts/validate-generated-matter.mjs <cases.json>\n");
  process.exitCode = 2;
} else {
  const artifact = JSON.parse(await readFile(artifactPath, "utf8"));
  const cases = Array.isArray(artifact) ? artifact : artifact.cases ?? artifact.results ?? [];
  if (!Array.isArray(cases) || cases.length === 0) throw Error("Artifact must contain a nonempty cases array.");
  for (const item of cases) {
    const generated = item.simulation ?? item.generation ?? item;
    const code = generated.code;
    const description = item.description ?? item.input ?? "";
    const explanation = item.explanation ?? item.intent?.explanation ?? "";
    if (typeof code !== "string") {
      process.stdout.write(`${JSON.stringify({ description, explanation, stage: item.stage, validation: null })}\n`);
      continue;
    }
    const parameters = generated.parameters ?? item.parameters ?? [];
    const params = Array.isArray(parameters)
      ? Object.fromEntries(parameters.map((entry) => [entry.name, entry.value]))
      : parameters;
    const safetyError = validateMatterCode(code, Object.keys(params));
    if (safetyError) {
      process.stdout.write(`${JSON.stringify({ description, explanation, safetyError })}\n`);
      process.exitCode = 1;
      continue;
    }
    const setup = new Function("Matter", "params", "width", "height", code);
    try {
      const result = validateEngineResolution(Matter,
        (values) => setup(Matter, Object.freeze(values), 960, 540),
        params, generated.simulationSpec ?? item.simulationSpec, 960, 540);
      process.stdout.write(`${JSON.stringify({ description, explanation, codeExcerpt: code.slice(0, 260), validation: result })}\n`);
    } catch (error) {
      process.stdout.write(`${JSON.stringify({ description, explanation, codeExcerpt: code.slice(0, 260), error: String(error) })}\n`);
      process.exitCode = 1;
    }
  }
}
