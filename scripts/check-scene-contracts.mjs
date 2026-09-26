import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { AUTHORABLE_PRIMITIVES, SUPPORTED_EFFECTS } from "../react-client/src/simulation-scene/PrimitiveCapabilities.ts";
import { validateVectorScene } from "../react-client/src/simulation-scene/VectorScene.ts";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const catalogPath = path.join(root, "backend", "src", "main", "resources", "schemas", "catalog.json");
const catalog = JSON.parse(fs.readFileSync(catalogPath, "utf8"));
const schemas = Array.isArray(catalog) ? catalog : catalog.schemas ?? [];
const primitives = new Set(AUTHORABLE_PRIMITIVES);
const effects = new Set(SUPPORTED_EFFECTS);
const errors = [];
const identities = new Set();
let explicitSceneGraphs = 0;
let seriesDrivenScenes = 0;

function bindingError(value, depth = 0, declaredSources) {
  if (depth > 32) return "binding nesting exceeds 32";
  if (typeof value === "number") return Number.isFinite(value) ? undefined : "literal must be finite";
  if (typeof value === "string") return !value.trim() ? "series binding must not be blank"
    : declaredSources && !declaredSources.has(value) ? `undeclared series binding ${value}` : undefined;
  if (!value || typeof value !== "object") return "binding must be numeric, a series key, or an object";
  if (["constant", "series", "quantity", "entity"].includes(value.source)) {
    if (value.source === "constant") return Number.isFinite(value.value) ? undefined : "constant must be finite";
    if (value.source === "entity") return typeof value.entityId === "string" && typeof value.path === "string" ? undefined : "entity requires entityId/path";
    return typeof value.key === "string" && value.key.trim() ? undefined : `${value.source} requires key`;
  }
  if (value.source !== "expression" || !Array.isArray(value.args)) return "unsupported binding source";
  const arity = { abs: 1, negate: 1, sin: 1, cos: 1, clamp: 3 }[value.operator];
  const variadic = ["add", "subtract", "multiply", "divide", "min", "max"].includes(value.operator);
  if ((!variadic && arity === undefined) || (arity !== undefined && value.args.length !== arity) || (variadic && value.args.length < 2)) return "invalid expression operator/arity";
  for (const arg of value.args) { const error = bindingError(arg, depth + 1, declaredSources); if (error) return error; }
  return undefined;
}

function visitNodes(schemaId, nodes, ids, declaredSources) {
  if (!Array.isArray(nodes)) { errors.push(`${schemaId}: sceneGraph.nodes must be an array`); return; }
  for (const node of nodes) {
    if (!node || typeof node !== "object" || typeof node.id !== "string" || !node.id.trim()) { errors.push(`${schemaId}: node id is required`); continue; }
    if (ids.has(node.id)) errors.push(`${schemaId}: duplicate node id ${node.id}`);
    ids.add(node.id);
    if (!primitives.has(node.type)) errors.push(`${schemaId}: unsupported primitive ${node.type}`);
    for (const [key, value] of Object.entries(node.transform ?? {})) {
      const error = bindingError(value, 0, declaredSources);
      if (error) errors.push(`${schemaId}.${node.id}.transform.${key}: ${error}`);
    }
    if ((node.type === "graph" || node.type === "chart") && !declaredSources.has(node.properties?.source)) {
      errors.push(`${schemaId}.${node.id}: graph source is not declared by visualization.series`);
    }
    if (node.type === "vectorScene") {
      for (const error of validateVectorScene(node.properties?.vector, value => bindingError(value, 0, declaredSources))) errors.push(`${schemaId}.${node.id}: ${error}`);
    }
    if (node.children !== undefined) visitNodes(schemaId, node.children, ids, declaredSources);
  }
}

for (const schema of schemas) {
  const schemaId = schema.schemaId;
  const identity = `${schemaId}@${schema.version}`;
  if (!schemaId || !schema.version) errors.push("schemaId/version is required");
  if (identities.has(identity)) errors.push(`duplicate schema identity ${identity}`);
  identities.add(identity);
  const visualization = schema.definition?.visualization;
  if (!visualization || typeof visualization.scene !== "string" || !visualization.scene.trim()) {
    errors.push(`${schemaId}: visualization.scene is required`);
    continue;
  }
  const seenSeries = new Set();
  const declaredSources = new Set();
  for (const series of visualization.series ?? []) {
    if (!series.key || seenSeries.has(series.key)) errors.push(`${schemaId}: duplicate/missing visualization series key ${series.key ?? ""}`);
    seenSeries.add(series.key);
    if (typeof series.source !== "string" || !series.source.trim()) errors.push(`${schemaId}.${series.key}: series source is required`);
    else declaredSources.add(series.source);
  }
  const nodes = visualization.presentation?.sceneGraph?.nodes;
  if (Array.isArray(nodes) && nodes.length) {
    explicitSceneGraphs += 1;
    visitNodes(schemaId, nodes, new Set(), declaredSources);
  } else if ((visualization.series?.length ?? 0) > 0 || (visualization.presentation?.actors?.length ?? 0) > 0) {
    seriesDrivenScenes += 1;
  } else errors.push(`${schemaId}: no sceneGraph, series, or actors to render`);
  for (const effect of visualization.presentation?.effects ?? []) {
    if (!effects.has(effect)) errors.push(`${schemaId}: unsupported compatibility effect ${effect}`);
  }
}

console.log(JSON.stringify({ schemas: schemas.length, explicitSceneGraphs, seriesDrivenScenes, errors }, null, 2));
if (errors.length) process.exitCode = 1;
