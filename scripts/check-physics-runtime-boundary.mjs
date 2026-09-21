#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const root = process.cwd();
const backend = path.join(root, 'backend', 'src', 'main', 'java', 'com', 'example', 'backend', 'physics');
const currentRoots = [
  path.join(backend, 'solver'),
  path.join(backend, 'reference'),
  path.join(backend, 'model')
];
const compatibilityRoots = [
  path.join(backend, 'compatibility', 'legacy', 'solver'),
  path.join(backend, 'compatibility', 'legacy', 'reference')
];
const javaRoots = [
  path.join(root, 'backend', 'src', 'main', 'java'),
  path.join(root, 'backend', 'src', 'test', 'java')
];

function filesUnder(directory) {
  if (!fs.existsSync(directory)) return [];
  const entries = fs.readdirSync(directory, { withFileTypes: true });
  return entries.flatMap((entry) => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? filesUnder(file) : [file];
  });
}

function javaFilesUnder(directory) {
  return filesUnder(directory).filter((file) => file.endsWith('.java'));
}

function contains(file, expression) {
  return expression.test(fs.readFileSync(file, 'utf8'));
}

const errors = [];
const currentFiles = currentRoots.flatMap(javaFilesUnder);
const compatibilityFiles = compatibilityRoots.flatMap(javaFilesUnder);
const allJavaFiles = javaRoots.flatMap(javaFilesUnder);

const currentRawJson = currentFiles.filter((file) => contains(file, /\bJsonNode\b/));
const currentDispatch = currentFiles.filter((file) => contains(file, /\bswitch\s*\(/));
const oldImports = allJavaFiles.filter((file) => contains(file, /com\.example\.backend\.physics\.(?:solver|reference)/));

if (currentRawJson.length > 0) {
  errors.push(`current solver/reference runtime contains raw JsonNode: ${currentRawJson.join(', ')}`);
}
if (currentDispatch.length > 0) {
  errors.push(`current solver/reference runtime contains dispatch switch: ${currentDispatch.join(', ')}`);
}
if (oldImports.length > 0) {
  errors.push(`source still imports the pre-isolation solver/reference packages: ${oldImports.join(', ')}`);
}
if (!fs.existsSync(path.join(backend, 'compatibility', 'LegacyPhysicsExecutionAdapterV1.java'))) {
  errors.push('LegacyPhysicsExecutionAdapterV1 is missing; compatibility entry point cannot be audited');
}
if (compatibilityFiles.length === 0) {
  errors.push('explicit legacy solver/reference compatibility package is empty');
}

console.log(`Physics runtime boundary: currentFiles=${currentFiles.length}, compatibilityFiles=${compatibilityFiles.length}, compatibilityRawJson=${compatibilityFiles.filter((file) => contains(file, /\bJsonNode\b/)).length}`);
if (errors.length > 0) {
  console.error(errors.join('\n'));
  process.exitCode = 1;
}
