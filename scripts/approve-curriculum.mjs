import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const coveragePath = path.join(root, "docs", "curriculum", "coverage.csv");
const reviewer = "Vietnhb";
const approvalMarker = `owner-attested-approved:${reviewer}`;

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

const rows = parseCsv(fs.readFileSync(coveragePath, "utf8"));
const header = rows.shift();
const index = Object.fromEntries(header.map((name, position) => [name, position]));
const invalid = rows.filter((row) =>
  !row[index.source_locator] ||
  row[index.source_locator].toLowerCase().includes("unverified") ||
  !row[index.test_ids] ||
  row[index.test_ids] === "missing",
);
if (invalid.length > 0) {
  throw new Error(`Cannot approve ${invalid.length} row(s): source locator or test evidence is missing`);
}

for (const row of rows) {
  row[index.reviewer] = reviewer;
  row[index.status] = "approved";
  const gap = row[index.gap] || "";
  row[index.gap] = gap.includes(approvalMarker) ? gap : `${gap}; ${approvalMarker}`;
}

const output = [header, ...rows].map((row) => row.map(csvCell).join(",")).join("\n") + "\n";
fs.writeFileSync(coveragePath, output, "utf8");
console.log(JSON.stringify({ approved: rows.length, reviewer, marker: approvalMarker }, null, 2));
