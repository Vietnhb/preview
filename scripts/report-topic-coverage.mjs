import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const catalogPath = path.join(root, "backend", "src", "main", "resources", "curriculum", "catalog.json");
const schemasPath = path.join(root, "backend", "src", "main", "resources", "schemas", "catalog.json");
const coveragePath = path.join(root, "docs", "curriculum", "coverage.csv");

function parseCsv(text) {
  const rows = [];
  let row = [];
  let cell = "";
  let quoted = false;
  for (let i = 0; i < text.length; i += 1) {
    const c = text[i];
    const next = text[i + 1];
    if (quoted) {
      if (c === '"' && next === '"') { cell += '"'; i += 1; }
      else if (c === '"') quoted = false;
      else cell += c;
    } else if (c === '"') quoted = true;
    else if (c === ',') { row.push(cell); cell = ''; }
    else if (c === '\n') { row.push(cell); if (row.some(Boolean)) rows.push(row); row = []; cell = ''; }
    else if (c !== '\r') cell += c;
  }
  if (cell || row.length) { row.push(cell); if (row.some(Boolean)) rows.push(row); }
  return rows;
}

const catalog = JSON.parse(fs.readFileSync(catalogPath, "utf8"));
const schemas = JSON.parse(fs.readFileSync(schemasPath, "utf8"));
const rows = parseCsv(fs.readFileSync(coveragePath, "utf8"));
const header = rows.shift();
const index = Object.fromEntries(header.map((name, position) => [name, position]));
const schemaIdsByModel = new Map();
const schemaCountByTopic = new Map();
for (const schema of schemas) {
  schemaCountByTopic.set(schema.topic, (schemaCountByTopic.get(schema.topic) ?? 0) + 1);
  const ids = schemaIdsByModel.get(schema.model) ?? new Set();
  ids.add(schema.schemaId);
  schemaIdsByModel.set(schema.model, ids);
  const own = schemaIdsByModel.get(schema.schemaId) ?? new Set();
  own.add(schema.schemaId);
  schemaIdsByModel.set(schema.schemaId, own);
}

const report = [];
for (const topic of catalog.topics ?? []) {
  const lessons = [];
  for (const module of topic.modules ?? []) {
    for (const level of module.levels ?? []) {
      for (const lesson of level.lessons ?? []) lessons.push(lesson.slug);
    }
  }
  const mappedRows = rows.filter((row) => lessons.includes(row[index.lesson]));
  const mappedLessons = new Set(mappedRows.map((row) => row[index.lesson]));
  const approvedLessons = new Set(mappedRows.filter((row) => row[index.status] === "approved").map((row) => row[index.lesson]));
  const models = new Set();
  const schemaIds = new Set();
  for (const row of mappedRows) {
    for (const model of (row[index.model_ids] ?? "").split("|").filter(Boolean)) {
      models.add(model);
      for (const schemaId of schemaIdsByModel.get(model) ?? []) schemaIds.add(schemaId);
    }
  }
  report.push({
    topic: topic.name,
    slug: topic.slug,
    lessons: lessons.length,
    mappedLessons: mappedLessons.size,
    approvedLessons: approvedLessons.size,
    modelFamilies: models.size,
    schemas: schemaCountByTopic.get(topic.name) ?? 0,
    referencedSchemas: schemaIds.size,
    coveragePercent: lessons.length === 0 ? 0 : Math.round((approvedLessons.size / lessons.length) * 10000) / 100,
  });
}

const totalLessons = report.reduce((sum, item) => sum + item.lessons, 0);
const totalMapped = report.reduce((sum, item) => sum + item.mappedLessons, 0);
const totalApproved = report.reduce((sum, item) => sum + item.approvedLessons, 0);
console.log(JSON.stringify({
  topics: report,
  total: {
    lessons: totalLessons,
    mappedLessons: totalMapped,
    approvedLessons: totalApproved,
    coveragePercent: totalLessons === 0 ? 0 : Math.round((totalApproved / totalLessons) * 10000) / 100,
  },
}, null, 2));
