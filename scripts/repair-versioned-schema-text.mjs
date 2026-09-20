import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const sourceRoot = path.join(repositoryRoot, "backend", "src", "main", "resources", "schemas", "source");
const mode = process.argv[2] ?? "--check";
const corruptionMarker = /[ÃÄÂÎÐÑÆÇØÅ]|â(?:€|‚|„|[\u0080-\u009f])|(?:á»|áº|ï¿|ï�)|\uFFFD/g;

const cp1252Bytes = new Map([
  [0x20ac, 0x80], [0x201a, 0x82], [0x0192, 0x83], [0x201e, 0x84], [0x2026, 0x85],
  [0x2020, 0x86], [0x2021, 0x87], [0x02c6, 0x88], [0x2030, 0x89], [0x0160, 0x8a],
  [0x2039, 0x8b], [0x0152, 0x8c], [0x017d, 0x8e], [0x2018, 0x91], [0x2019, 0x92],
  [0x201c, 0x93], [0x201d, 0x94], [0x2022, 0x95], [0x2013, 0x96], [0x2014, 0x97],
  [0x02dc, 0x98], [0x2122, 0x99], [0x0161, 0x9a], [0x203a, 0x9b], [0x0153, 0x9c],
  [0x017e, 0x9e], [0x0178, 0x9f],
]);

function westernBytes(text) {
  const bytes = [];
  for (const character of text) {
    const codePoint = character.codePointAt(0);
    if (codePoint <= 0xff) bytes.push(codePoint);
    else if (cp1252Bytes.has(codePoint)) bytes.push(cp1252Bytes.get(codePoint));
    else bytes.push(...Buffer.from(character, "utf8"));
  }
  return Uint8Array.from(bytes);
}

function decodeOneLayer(text) {
  try {
    return new TextDecoder("utf-8", { fatal: true }).decode(westernBytes(text));
  } catch {
    return null;
  }
}

function markerCount(text) {
  return (text.match(corruptionMarker) ?? []).length;
}

function repairText(text) {
  let current = text;
  for (let attempt = 0; attempt < 5; attempt += 1) {
    const decoded = decodeOneLayer(current);
    if (decoded === null || decoded === current || markerCount(decoded) >= markerCount(current)) break;
    current = decoded;
  }
  return current;
}

function repairTree(value, pathParts = []) {
  if (typeof value === "string") {
    const repaired = repairText(value);
    return { value: repaired, changed: repaired !== value };
  }
  if (Array.isArray(value)) {
    let changed = false;
    const next = value.map((item, index) => {
      const result = repairTree(item, [...pathParts, String(index)]);
      changed ||= result.changed;
      return result.value;
    });
    return { value: next, changed };
  }
  if (value !== null && typeof value === "object") {
    let changed = false;
    const next = {};
    for (const [key, item] of Object.entries(value)) {
      const result = repairTree(item, [...pathParts, key]);
      changed ||= result.changed;
      next[key] = result.value;
    }
    return { value: next, changed };
  }
  return { value, changed: false };
}

function incrementVersion(version) {
  const match = version.match(/^(.*?)(\d+)$/);
  if (!match) throw new Error(`Cannot safely create a new schema version from ${version}`);
  return `${match[1]}${Number(match[2]) + 1}`;
}

function scan() {
  const files = [];
  for (const topic of fs.readdirSync(sourceRoot).sort()) {
    const topicPath = path.join(sourceRoot, topic);
    for (const filename of fs.readdirSync(topicPath).sort()) {
      const match = filename.match(/^(\d{3,})__([A-Za-z0-9_-]+)__([A-Za-z0-9._-]+)\.json$/);
      if (!match) throw new Error(`Invalid schema source filename: ${path.relative(repositoryRoot, path.join(topicPath, filename))}`);
      const source = path.join(topicPath, filename);
      const original = JSON.parse(fs.readFileSync(source, "utf8"));
      const repaired = repairTree(original);
      files.push({ source, topic, ordinal: match[1], schemaId: original.schemaId, version: original.version,
        value: repaired.value, changed: repaired.changed });
    }
  }
  return files;
}

function runCheck(files) {
  const affected = files.filter((file) => file.changed);
  if (affected.length > 0) {
    console.error(`Found mojibake in ${affected.length} schema source entries. Run node scripts/repair-versioned-schema-text.mjs --apply to create corrected versions.`);
    process.exitCode = 1;
    return;
  }
  console.log(`Schema source encoding check passed (${files.length} entries).`);
}

function applyRepairs(files) {
  const affected = files.filter((file) => file.changed);
  const identities = new Set(files.map((file) => `${file.schemaId}@${file.version}`));
  for (const file of affected) {
    const version = incrementVersion(file.version);
    const identity = `${file.schemaId}@${version}`;
    if (identities.has(identity)) throw new Error(`Refusing to overwrite existing schema version ${identity}`);
    identities.add(identity);
    file.value.version = version;
    const destination = path.join(path.dirname(file.source), `${file.ordinal}__${file.schemaId}__${version}.json`);
    const rendered = `${JSON.stringify(file.value, null, 2)}\n`;
    if (corruptionMarker.test(rendered)) throw new Error(`Repair left a mojibake marker in ${identity}`);
    fs.writeFileSync(destination, rendered, "utf8");
    fs.unlinkSync(file.source);
    console.log(`${file.schemaId}: ${file.version} -> ${version}`);
  }
  console.log(`Created ${affected.length} corrected schema versions; no existing published version or stored run was changed.`);
}

const files = scan();
if (mode === "--check") runCheck(files);
else if (mode === "--apply") applyRepairs(files);
else throw new Error("Usage: node scripts/repair-versioned-schema-text.mjs [--check|--apply]");
