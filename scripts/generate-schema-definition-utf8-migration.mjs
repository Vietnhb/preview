#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

const repoRoot = path.resolve(".");
const migrationPath = path.join(repoRoot, "backend/src/main/resources/db/migration/V16__repair_schema_definition_utf8.sql");
const sources = [
  "backend/src/main/resources/schemas/catalog.json",
  "backend/src/main/resources/schemas/history/published-versions.json"
];

function readCurrent(relative) {
  return JSON.parse(fs.readFileSync(path.join(repoRoot, relative), "utf8"));
}

function readHead(relative) {
  return JSON.parse(execFileSync("git", ["show", `HEAD:${relative}`], { encoding: "utf8" }));
}

function byIdentity(entries) {
  return new Map(entries.map(entry => [`${entry.schemaId}@${entry.version}`, entry]));
}

function sqlText(value) {
  return value.replaceAll("'", "''");
}

const repairs = [];
for (const relative of sources) {
  const current = byIdentity(readCurrent(relative));
  const head = byIdentity(readHead(relative));
  for (const [identity, entry] of current) {
    const previous = head.get(identity);
    if (!previous || JSON.stringify(previous.definition) === JSON.stringify(entry.definition)) continue;
    repairs.push({
      schemaId: entry.schemaId,
      version: entry.version,
      definition: JSON.stringify(entry.definition)
    });
  }
}

repairs.sort((left, right) => `${left.schemaId}@${left.version}`.localeCompare(`${right.schemaId}@${right.version}`));
const statements = repairs.map(repair => `UPDATE schema_versions
SET definition = '${sqlText(repair.definition)}'::jsonb,
    definition_checksum = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE schema_id = '${sqlText(repair.schemaId)}' AND version = '${sqlText(repair.version)}';`);
const sql = `-- Corrects source-approved schema definitions whose UTF-8 text was previously persisted as mojibake.
-- This is a data repair only: schema identity, lifecycle, solver bindings, and historical rows are preserved.
${statements.join("\n\n")}
`;

fs.writeFileSync(migrationPath, sql, "utf8");
console.log(`Generated ${path.relative(repoRoot, migrationPath)} with ${repairs.length} identity repairs.`);
console.log(repairs.map(repair => `${repair.schemaId}@${repair.version}`).join("\n"));
