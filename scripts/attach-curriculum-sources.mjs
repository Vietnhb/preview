import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const coveragePath = path.join(root, "docs", "curriculum", "coverage.csv");
const sourceCatalogPath = path.join(root, "docs", "curriculum", "source-catalog.json");

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

function csvCell(value) {
  const text = String(value ?? "");
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

const catalog = JSON.parse(fs.readFileSync(sourceCatalogPath, "utf8"));
const sources = catalog.sources;
const rules = catalog.rules.map((rule) => ({ ...rule, regex: new RegExp(rule.pattern, "i") }));
const rows = parseCsv(fs.readFileSync(coveragePath, "utf8"));
const header = rows.shift();
const index = Object.fromEntries(header.map((name, position) => [name, position]));
const stats = { total: rows.length, exact: 0, fallback: 0, bySource: {} };

for (const row of rows) {
  const text = [row[index.chapter], row[index.lesson], row[index.requirement_summary]].join(" ");
  const rule = rules.find((candidate) => candidate.regex.test(text));
  const sourceId = rule?.source ?? "moet_tt32";
  const source = sources[sourceId];
  const exact = Boolean(rule && rule.source !== "moet_tt32");
  const chapter = rule?.chapter ?? `Grade ${row[index.grade]} curriculum outcome`;
  const locator = `source:${sourceId};chapter:${chapter};lesson:${row[index.lesson]}`;
  row[index.textbook] = source.title;
  row[index.edition] = source.edition;
  row[index.source_url] = source.url;
  row[index.source_locator] = locator;
  row[index.reviewer] = (!row[index.reviewer] || row[index.reviewer] === "unassigned") ? "Vietnhb" : row[index.reviewer];
  const oldGap = (row[index.gap] || "")
    .split(/;\s*auto-source:[^;]*/i)[0]
    .replace(/source\/?reviewer pending/gi, "academic review pending")
    .replace(/source and academic review are not closed/gi, "academic review pending")
    .trim();
  const marker = exact
    ? `auto-source:${sourceId}`
    : "auto-source:moet_tt32-fallback";
  const approval = (row[index.gap] || "").match(/owner-attested-approved:[^;]+/i)?.[0];
  const suffix = approval ? `; ${approval}` : "";
  if (!oldGap.includes(marker)) row[index.gap] = oldGap ? `${oldGap}; ${marker}${suffix}` : `${marker}${suffix}`;
  stats[exact ? "exact" : "fallback"] += 1;
  stats.bySource[sourceId] = (stats.bySource[sourceId] ?? 0) + 1;
}

const output = [header, ...rows].map((row) => row.map(csvCell).join(",")).join("\n") + "\n";
fs.writeFileSync(coveragePath, output, "utf8");
console.log(JSON.stringify(stats, null, 2));
