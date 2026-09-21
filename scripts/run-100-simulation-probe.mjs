import fs from "node:fs";
import path from "node:path";
import { compileSimulationScene } from "../react-client/src/simulation-scene/SceneCompiler.ts";
import { hasRenderableNodes } from "../react-client/src/simulation-scene/SceneGraph.ts";
import { validateSceneGraph } from "../react-client/src/simulation-scene/SceneCompiler.ts";
import { prepareSimulationData } from "../react-client/src/simulation-runtime/SimulationData.ts";

const root = path.resolve(import.meta.dirname, "..");
const catalogPath = path.join(root, "backend", "src", "main", "resources", "schemas", "catalog.json");
const outputDir = path.join(root, "tmp", "simulation-probe");
fs.mkdirSync(outputDir, { recursive: true });
const requestedCount = Math.max(1, Number(process.argv[2] ?? 100));
const variantCount = Math.max(1, Number(process.argv[3] ?? 1));

const catalog = JSON.parse(fs.readFileSync(catalogPath, "utf8"));
const schemas = Array.isArray(catalog) ? catalog : catalog.schemas ?? [];

function byTopicRoundRobin(items, count) {
  const groups = new Map();
  for (const schema of items) {
    const topic = schema.topic ?? "UNSPECIFIED";
    if (!groups.has(topic)) groups.set(topic, []);
    groups.get(topic).push(schema);
  }
  const topics = [...groups.keys()].sort();
  const selected = [];
  let cursor = 0;
  while (selected.length < count && topics.some(topic => groups.get(topic).length)) {
    const topic = topics[cursor % topics.length];
    const group = groups.get(topic);
    if (group.length) selected.push(group.shift());
    cursor += 1;
  }
  return selected;
}

function seriesValues(seriesIndex) {
  const a = seriesIndex + 1;
  return [0, 0.25 * a, 0.5 * a, 0.75 * a, a];
}

function makeSimulation(schema, index, variant) {
  const definition = schema.definition ?? {};
  const visualization = definition.visualization ?? { scene: schema.schemaId, series: [] };
  const values = {};
  const positions = {};
  const velocities = {};
  for (const [seriesIndex, series] of (visualization.series ?? []).entries()) {
    const source = typeof series.source === "string" ? series.source : `values.series${seriesIndex}`;
    const key = source.includes(".") ? source.split(".").slice(1).join(".") : source;
    if (source.startsWith("positions.")) positions[key] = seriesValues(seriesIndex);
    else if (source.startsWith("velocities.")) velocities[key] = seriesValues(seriesIndex);
    else values[key] = seriesValues(seriesIndex);
  }
  const parameters = {};
  for (const quantity of definition.requiredQuantities ?? []) parameters[quantity.key] = 1;
  for (const quantity of definition.optionalQuantities ?? []) parameters[quantity.key] = quantity.defaultValue ?? 1;
  for (const control of definition.adjustableParameters ?? []) {
    const min = Number.isFinite(control.min) ? control.min : 1;
    const max = Number.isFinite(control.max) ? control.max : min;
    const ratio = variant === 0 ? 0 : variant === 1 ? 0.5 : 1;
    parameters[control.key] = min + (max - min) * ratio;
  }
  return {
    simulationId: `probe-${String(index + 1).padStart(3, "0")}-${schema.schemaId}`,
    runId: `probe-run-${index + 1}`,
    specificationId: `probe-spec-${index + 1}`,
    schemaId: schema.schemaId,
    valid: true,
    ready: true,
    time: [0, 0.25, 0.5, 0.75, 1],
    positions,
    velocities,
    accelerations: {},
    values,
    parameters,
    visualization: { ...visualization, controls: visualization.controls ?? [] },
    validation: { passed: true, tolerance: 1e-6, checkpoints: [] },
    elapsedMilliseconds: 0,
  };
}

function addPlaceholderFields(simulation, graph) {
  const fields = {};
  for (const node of graph.nodes.flatMap(function flatten(item) {
    return [item, ...(item.children ?? []).flatMap(flatten)];
  })) {
    if (node.type !== "waveField" || typeof node.properties.field !== "string") continue;
    const id = node.properties.field;
    fields[id] = {
      id,
      version: 1,
      type: "scalarField",
      physicalDimension: 1,
      axes: [{ key: "x", coordinates: [0, 1], unit: "m" }],
      shape: [2, 2],
      time: [0, 1],
      values: [0, 1, 1, 0],
      valueUnit: "1",
      timeUnit: "s",
      interpolation: "linear",
    };
  }
  if (Object.keys(fields).length) simulation.scalarFields = fields;
}

const selected = byTopicRoundRobin(schemas, Math.min(requestedCount, schemas.length));
const results = [];
for (const schema of selected) {
  for (let variant = 0; variant < variantCount; variant += 1) {
  const index = results.length;
  const simulation = makeSimulation(schema, index, variant);
  const graph = compileSimulationScene(simulation);
  addPlaceholderFields(simulation, graph);
  const runtime = prepareSimulationData(simulation);
  const validation = validateSceneGraph(graph, runtime);
  const renderable = hasRenderableNodes(graph);
  const props = graph.nodes.flatMap(function flatten(item) {
    return [item, ...(item.children ?? []).flatMap(flatten)];
  }).filter(node => node.type === "prop").map(node => node.properties.asset).filter(Boolean);
  results.push({
    index: index + 1,
    variant: variant + 1,
    schemaId: schema.schemaId,
    name: schema.name,
    topic: schema.topic,
    scene: simulation.visualization.scene,
    nodeCount: graph.nodes.length,
    propAssets: [...new Set(props)],
    renderable,
    valid: validation.valid,
    errors: validation.errors,
  });
  }
}

const report = {
  generatedAt: new Date().toISOString(),
  requestedCases: selected.length * variantCount,
  variantsPerSchema: variantCount,
  executedCases: results.length,
  passedCases: results.filter(result => result.renderable && result.valid).length,
  failedCases: results.filter(result => !result.renderable || !result.valid).length,
  topics: [...new Set(results.map(result => result.topic))].sort(),
  results,
};
fs.writeFileSync(path.join(outputDir, "results.json"), JSON.stringify(report, null, 2));
console.log(JSON.stringify({
  requestedCases: report.requestedCases,
  executedCases: report.executedCases,
  passedCases: report.passedCases,
  failedCases: report.failedCases,
  topics: report.topics,
  output: path.join(outputDir, "results.json"),
}, null, 2));
if (report.failedCases) process.exitCode = 1;
