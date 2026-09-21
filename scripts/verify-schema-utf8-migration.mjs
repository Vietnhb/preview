#!/usr/bin/env node

import fs from "node:fs";

const catalog = JSON.parse(fs.readFileSync("backend/src/main/resources/schemas/catalog.json", "utf8"));
const history = JSON.parse(fs.readFileSync("backend/src/main/resources/schemas/history/published-versions.json", "utf8"));
const expected = new Map([...catalog, ...history].map(entry => [
  `${entry.schemaId}@${entry.version}`,
  JSON.stringify(entry.definition)
]));
const sql = fs.readFileSync("backend/src/main/resources/db/migration/V16__repair_schema_definition_utf8.sql", "utf8");
const statements = sql.split("\n\nUPDATE schema_versions\n").slice(1);
const mismatches = [];
for (const statement of statements) {
  const identity = statement.match(/WHERE schema_id = '([^']+)' AND version = '([^']+)'/);
  const definition = statement.match(/SET definition = '(.+)'::jsonb,/);
  if (!identity || !definition) continue;
  const key = `${identity[1]}@${identity[2]}`;
  const actual = JSON.stringify(JSON.parse(definition[1].replaceAll("''", "'")));
  if (actual !== expected.get(key)) mismatches.push(key);
}
console.log(`migrationStatements=${statements.length}`);
console.log(`migrationMismatches=${mismatches.length}`);
if (mismatches.length) console.log(mismatches.join("\n"));
