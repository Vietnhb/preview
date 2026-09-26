import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const directory = path.join(root, "backend/src/main/resources/schemas");
const rows = [];
const identities = new Set();
const orders = new Set();
for (const topic of fs.readdirSync(path.join(directory, "source"))) {
  for (const name of fs.readdirSync(path.join(directory, "source", topic))) {
    if (!name.endsWith(".json")) continue;
    const match = /^(\d+)__(.+)__(\d+(?:\.\d+)*)\.json$/.exec(name);
    if (!match) throw new Error(`Invalid catalog source name: ${name}`);
    const row = JSON.parse(fs.readFileSync(path.join(directory, "source", topic, name), "utf8"));
    const identity = `${row.schemaId}@${row.version}`;
    const order = Number(match[1]);
    if (row.topic !== topic || row.schemaId !== match[2] || row.version !== match[3]
        || identities.has(identity) || orders.has(order)) throw new Error(`Conflicting catalog identity: ${name}`);
    identities.add(identity); orders.add(order); rows.push({ order, row });
  }
}
rows.sort((a, b) => a.order - b.order);
rows.forEach(({ order }, i) => { if (order !== i) throw new Error(`Missing source order ${i}`); });
const output = JSON.stringify(rows.map(({ row }) => row), null, 2) + "\n";
const target = path.join(directory, "catalog.json");
if (process.argv.includes("--write")) fs.writeFileSync(target, output);
else if (fs.readFileSync(target, "utf8").replaceAll("\r\n", "\n") !== output)
  throw new Error("Schema catalog drift: run node scripts/generate-schema-catalog.mjs --write");
console.log(`Schema catalog is current (${rows.length} entries).`);
