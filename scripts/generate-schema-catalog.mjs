#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const sourceRoot = path.join(root, "backend", "src", "main", "resources", "schemas", "source");
const catalogPath = path.join(root, "backend", "src", "main", "resources", "schemas", "catalog.json");
const mode = process.argv.includes("--write") ? "write" : process.argv.includes("--check") ? "check" : "help";

function fail(message) {
  console.error(`Schema catalog error: ${message}`);
  process.exitCode = 1;
}

function filesUnder(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const fullPath = path.join(directory, entry.name);
    return entry.isDirectory() ? filesUnder(fullPath) : entry.name.endsWith(".json") ? [fullPath] : [];
  });
}

function sourceEntries() {
  const files = filesUnder(sourceRoot).sort((left, right) => {
    const leftOrder = Number.parseInt(path.basename(left).split("__", 1)[0], 10);
    const rightOrder = Number.parseInt(path.basename(right).split("__", 1)[0], 10);
    return leftOrder - rightOrder || left.localeCompare(right);
  });
  const entries = [];
  const identities = new Set();
  files.forEach((file, index) => {
    const relative = path.relative(sourceRoot, file).split(path.sep);
    const topic = relative[0];
    const filename = relative.at(-1);
    const match = /^(\d+)__(.+)__([0-9]+(?:\.[0-9]+)*)\.json$/.exec(filename);
    if (!match) throw new Error(`invalid source filename: ${path.relative(root, file)}`);
    if (Number.parseInt(match[1], 10) !== index) throw new Error(`source order must be contiguous at ${filename}`);
    const schemaId = match[2];
    const version = match[3];
    const entry = JSON.parse(fs.readFileSync(file, "utf8"));
    if (entry.schemaId !== schemaId || entry.version !== version || entry.topic !== topic) {
      throw new Error(`source path and identity disagree: ${path.relative(root, file)}`);
    }
    const identity = `${entry.schemaId}@${entry.version}`;
    if (identities.has(identity)) throw new Error(`duplicate schema identity: ${identity}`);
    identities.add(identity);
    entries.push(entry);
  });
  return entries;
}

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]));
  }
  return value;
}

function run() {
  if (mode === "help") {
    console.log("Usage: node scripts/generate-schema-catalog.mjs --check|--write");
    return;
  }
  let entries;
  try {
    entries = sourceEntries();
  } catch (error) {
    fail(error instanceof Error ? error.message : String(error));
    return;
  }
  if (mode === "write") {
    fs.writeFileSync(catalogPath, `${JSON.stringify(entries, null, 2)}\n`, "utf8");
    console.log(`Wrote schema catalog (${entries.length} entries).`);
    return;
  }
  let current;
  try {
    current = JSON.parse(fs.readFileSync(catalogPath, "utf8"));
  } catch (error) {
    fail(`cannot read catalog: ${error instanceof Error ? error.message : String(error)}`);
    return;
  }
  const expected = JSON.stringify(canonical(entries));
  const actual = JSON.stringify(canonical(current));
  if (expected !== actual) {
    fail("catalog.json differs from the ordered source modules; run with --write after approving the source change");
    return;
  }
  console.log(`Schema catalog is current (${entries.length} entries).`);
}

run();
