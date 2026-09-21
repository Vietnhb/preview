#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

const root = path.resolve(".");
const output = path.join(root, "backend/src/main/resources/db/migration/V17__restore_schema_definition_model_after_utf8_repair.sql");
const sources = [
  "backend/src/main/resources/schemas/catalog.json",
  "backend/src/main/resources/schemas/history/published-versions.json"
];

function read(relative) { return JSON.parse(fs.readFileSync(path.join(root, relative), "utf8")); }
function readHead(relative) { return JSON.parse(execFileSync("git", ["show", `HEAD:${relative}`], { encoding: "utf8" })); }
function mapByIdentity(entries) { return new Map(entries.map(entry => [`${entry.schemaId}@${entry.version}`, entry])); }
function sqlText(value) { return value.replaceAll("'", "''"); }

const repairs = [];
for (const relative of sources) {
  const current = mapByIdentity(read(relative));
  const previous = mapByIdentity(readHead(relative));
  for (const [identity, entry] of current) {
    const old = previous.get(identity);
    if (!old || JSON.stringify(old.definition) === JSON.stringify(entry.definition)) continue;
    const definition = structuredClone(entry.definition);
    definition.model = entry.model;
    repairs.push({ schemaId: entry.schemaId, version: entry.version, definition: JSON.stringify(definition) });
  }
}

repairs.sort((left, right) => `${left.schemaId}@${left.version}`.localeCompare(`${right.schemaId}@${right.version}`));
const statements = repairs.map(repair => `UPDATE schema_versions
SET definition = '${sqlText(repair.definition)}'::jsonb,
    definition_checksum = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE schema_id = '${sqlText(repair.schemaId)}' AND version = '${sqlText(repair.version)}';`);
fs.writeFileSync(output, `-- Restores the catalog-injected model field after the V16 UTF-8 definition repair.\n-- No schema identity or lifecycle state is changed.\n${statements.join("\n\n")}\n`, "utf8");
console.log(`Generated ${path.relative(root, output)} with ${repairs.length} identity repairs.`);
console.log(repairs.map(repair => `${repair.schemaId}@${repair.version}`).join("\n"));
