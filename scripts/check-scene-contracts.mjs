#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const catalogPath = path.join(root, "backend", "src", "main", "resources", "schemas", "catalog.json");
const capabilityPath = path.join(root, "react-client", "src", "simulation-scene", "PrimitiveCapabilities.ts");
const catalog = JSON.parse(fs.readFileSync(catalogPath, "utf8"));
const capabilitySource = fs.readFileSync(capabilityPath, "utf8");
const errors = [];

function declaredValues(name) {
  const match = new RegExp(`export const ${name} = \\[([\\s\\S]*?)\\]`, "m").exec(capabilitySource);
  if (!match) return new Set();
  return new Set([...match[1].matchAll(/["']([^"']+)["']/g)].map(item => item[1]));
}

const environments = declaredValues("SUPPORTED_ENVIRONMENTS");
const effects = declaredValues("SUPPORTED_EFFECTS");
const layouts = declaredValues("SUPPORTED_LAYOUTS");
const identity = new Set();

for (const schema of catalog) {
  const key = `${schema.schemaId}@${schema.version}`;
  if (identity.has(key)) errors.push(`duplicate schema identity: ${key}`);
  identity.add(key);
  const visualization = schema.definition?.visualization;
  if (!visualization || !Array.isArray(visualization.series) || visualization.series.length === 0) {
    errors.push(`${key}: visualization.series is required`);
    continue;
  }
  visualization.series.forEach((series, index) => {
    if (!series?.key || !series?.source || !series?.unit) errors.push(`${key}: invalid visualization.series[${index}]`);
  });
  const presentation = visualization.presentation ?? {};
  if (presentation.environment && !environments.has(presentation.environment)) {
    errors.push(`${key}: unsupported declared environment ${presentation.environment}`);
  }
  for (const effect of presentation.effects ?? []) {
    if (!effects.has(effect)) errors.push(`${key}: unsupported declared effect ${effect}`);
  }
  if (presentation.layout && !layouts.has(presentation.layout)) {
    errors.push(`${key}: unsupported declared layout ${presentation.layout}`);
  }
}

if (errors.length) {
  console.error(errors.join("\n"));
  process.exitCode = 1;
} else {
  console.log(`Scene contracts passed (${catalog.length} schema versions).`);
}
