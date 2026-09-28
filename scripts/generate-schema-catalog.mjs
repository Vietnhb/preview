// Validates the curriculum-aligned schema library (no generated file is committed any more).
//
//   backend/src/main/resources/schemas/library/defaults.json
//   backend/src/main/resources/schemas/library/quantities.json
//   backend/src/main/resources/schemas/library/capabilities/<area>/<capabilityId>.json
//   backend/src/main/resources/schemas/topics/grade-NN/<nn>-<slug>.json
//
// The backend compiles these at startup (SchemaCatalogCompiler). Usage: node scripts/generate-schema-catalog.mjs --check
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const resources = path.join(root, "backend/src/main/resources");
const library = path.join(resources, "schemas/library");
const errors = [];
const read = file => JSON.parse(fs.readFileSync(file, "utf8"));
const walk = dir => fs.readdirSync(dir, { withFileTypes: true })
  .flatMap(entry => entry.isDirectory() ? walk(path.join(dir, entry.name)) : entry.name.endsWith(".json") ? [path.join(dir, entry.name)] : []);

const quantities = read(path.join(library, "quantities.json"));
const units = read(path.join(resources, "units/catalog.json"));
const unitNames = new Set(units.flatMap(u => [u.canonical, ...(u.aliases ?? [])]));
const capabilities = new Map();
for (const file of walk(path.join(library, "capabilities"))) {
  const capability = read(file);
  const id = capability.capabilityId;
  if (path.basename(file, ".json") !== id) errors.push(`${file}: file name must equal capabilityId`);
  if (capabilities.has(id)) errors.push(`duplicate capability ${id}`);
  if (!capability.title) errors.push(`${id}: title is required`);
  for (const item of [...(capability.canonicalInputs ?? []), ...(capability.outputs ?? [])]) {
    if (!quantities[item.key]?.label) errors.push(`${id}: quantity ${item.key} has no label in quantities.json`);
    if (!unitNames.has(item.unit)) errors.push(`${id}: unit ${item.unit} is missing from units/catalog.json`);
  }
  capabilities.set(id, capability);
}

const used = new Set();
const schemaIds = new Set();
const grades = new Set();
for (const file of walk(path.join(resources, "schemas/topics"))) {
  const topic = read(file);
  for (const field of ["schemaId", "version", "topic", "name", "grade", "description"])
    if (topic[field] === undefined || topic[field] === "") errors.push(`${file}: missing ${field}`);
  if (schemaIds.has(topic.schemaId)) errors.push(`duplicate topic schemaId ${topic.schemaId}`);
  schemaIds.add(topic.schemaId);
  grades.add(topic.grade);
  if (!topic.objectTypes?.length) errors.push(`${topic.schemaId}: objectTypes required`);
  if (!topic.capabilities?.length) errors.push(`${topic.schemaId}: capabilities required`);
  for (const id of topic.capabilities ?? []) {
    if (!capabilities.has(id)) errors.push(`${topic.schemaId}: unknown capability ${id}`);
    used.add(id);
  }
}
for (const id of capabilities.keys()) if (!used.has(id)) errors.push(`capability ${id} is not used by any topic`);
for (const grade of [10, 11, 12]) if (!grades.has(grade)) errors.push(`no topic for grade ${grade}`);

if (errors.length) {
  console.error(errors.join("\n"));
  process.exit(1);
}
console.log(`Schema library is consistent (${capabilities.size} capabilities, ${schemaIds.size} topics).`);
