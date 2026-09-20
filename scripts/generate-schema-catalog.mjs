import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const schemasRoot = path.join(repositoryRoot, "backend", "src", "main", "resources", "schemas");
const sourceRoot = path.join(schemasRoot, "source");
const generatedPath = path.join(schemasRoot, "catalog.json");
const mode = process.argv[2] ?? "--check";

function validateEntry(entry, source) {
  if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
    throw new Error(`${source}: catalog entry must be an object`);
  }
  for (const field of ["schemaId", "version", "topic", "model", "name", "solverId", "referenceSolverId"]) {
    if (typeof entry[field] !== "string" || entry[field].trim() === "") {
      throw new Error(`${source}: ${field} must be a non-empty string`);
    }
  }
  if (!entry.definition || typeof entry.definition !== "object" || Array.isArray(entry.definition)) {
    throw new Error(`${source}: definition must be an object`);
  }
  return entry;
}

function sourceFiles() {
  if (!fs.existsSync(sourceRoot)) {
    throw new Error(`Schema source directory is missing: ${path.relative(repositoryRoot, sourceRoot)}`);
  }
  const result = [];
  for (const topic of fs.readdirSync(sourceRoot).sort()) {
    const topicPath = path.join(sourceRoot, topic);
    if (!fs.statSync(topicPath).isDirectory()) throw new Error(`Unexpected file in schema source root: ${topic}`);
    if (!/^[A-Z][A-Z0-9_]*$/.test(topic)) throw new Error(`Invalid schema topic directory: ${topic}`);
    for (const filename of fs.readdirSync(topicPath).sort()) {
      if (!filename.endsWith(".json")) throw new Error(`Unexpected schema source file: ${topic}/${filename}`);
      const match = filename.match(/^(\d{3,})__([A-Za-z0-9_-]+)__([A-Za-z0-9._-]+)\.json$/);
      if (!match) throw new Error(`Invalid schema source filename: ${topic}/${filename}`);
      const ordinal = Number(match[1]);
      const source = path.join(topicPath, filename);
      const raw = fs.readFileSync(source, "utf8").trim();
      if (/[ÃÄÂÎÐÑÆÇØÅ]|â(?:€|‚|„|[\u0080-\u009f])|(?:á»|áº|ï¿|ï�)|\uFFFD/u.test(raw)) {
        throw new Error(`${path.relative(repositoryRoot, source)}: repair encoding corruption in a new schema version`);
      }
      const entry = validateEntry(JSON.parse(raw), path.relative(repositoryRoot, source));
      if (entry.topic !== topic || entry.schemaId !== match[2] || entry.version !== match[3]) {
        throw new Error(`${path.relative(repositoryRoot, source)}: path identity does not match schema entry ${entry.schemaId}@${entry.version}`);
      }
      result.push({ ordinal, entry, source, raw });
    }
  }
  result.sort((left, right) => left.ordinal - right.ordinal);
  const identities = new Set();
  result.forEach(({ ordinal, entry, source }, index) => {
    if (ordinal !== index) throw new Error(`${source}: source order must be contiguous from 000`);
    const identity = `${entry.schemaId}@${entry.version}`;
    if (identities.has(identity)) throw new Error(`Duplicate schema identity in source catalog: ${identity}`);
    identities.add(identity);
  });
  return result;
}

function render(entries) {
  const lines = entries.map(({ raw }, index) => `${raw}${index < entries.length - 1 ? "," : ""}`);
  return `[\n${lines.join("\n")}\n]\n`;
}

function normalizeLineEndings(text) {
  return text.replace(/\r\n/g, "\n");
}

function splitExistingCatalog() {
  if (fs.existsSync(sourceRoot) && fs.readdirSync(sourceRoot).length > 0) {
    throw new Error(`Refusing to overwrite existing schema source modules in ${path.relative(repositoryRoot, sourceRoot)}`);
  }
  const currentText = fs.readFileSync(generatedPath, "utf8");
  const current = JSON.parse(currentText);
  if (!Array.isArray(current)) throw new Error("Current generated schema catalog must be a JSON array");
  const rawEntries = extractRawEntries(currentText);
  if (rawEntries.length !== current.length) throw new Error("Could not preserve every source entry while splitting catalog.json");
  const identities = new Set();
  current.forEach((entry, index) => {
    validateEntry(entry, `catalog.json[${index}]`);
    const identity = `${entry.schemaId}@${entry.version}`;
    if (identities.has(identity)) throw new Error(`Duplicate schema identity in current catalog: ${identity}`);
    identities.add(identity);
    if (!/^[A-Z][A-Z0-9_]*$/.test(entry.topic)) throw new Error(`${identity}: invalid topic ${entry.topic}`);
    if (!/^[A-Za-z0-9_-]+$/.test(entry.schemaId) || !/^[A-Za-z0-9._-]+$/.test(entry.version)) {
      throw new Error(`${identity}: cannot represent identity in a source filename`);
    }
    const topicPath = path.join(sourceRoot, entry.topic);
    fs.mkdirSync(topicPath, { recursive: true });
    const filename = `${String(index).padStart(3, "0")}__${entry.schemaId}__${entry.version}.json`;
    fs.writeFileSync(path.join(topicPath, filename), `${rawEntries[index]}\n`, "utf8");
  });
  console.log(`Split ${current.length} catalog entries into ${path.relative(repositoryRoot, sourceRoot)} without changing catalog.json.`);
}

function extractRawEntries(text) {
  const entries = [];
  let index = text.indexOf("[") + 1;
  if (index === 0) throw new Error("Catalog root must be a JSON array");
  while (index < text.length) {
    while (/\s|,/.test(text[index] ?? "")) index += 1;
    if (text[index] === "]") break;
    const start = index;
    let depth = 0;
    let inString = false;
    let escaped = false;
    for (; index < text.length; index += 1) {
      const character = text[index];
      if (inString) {
        if (escaped) escaped = false;
        else if (character === "\\") escaped = true;
        else if (character === '"') inString = false;
        continue;
      }
      if (character === '"') inString = true;
      else if (character === "{" || character === "[") depth += 1;
      else if (character === "}" || character === "]") {
        depth -= 1;
        if (depth === 0) {
          index += 1;
          break;
        }
      }
    }
    if (depth !== 0 || inString) throw new Error("Malformed catalog while preserving source entry text");
    entries.push(text.slice(start, index).trim());
  }
  return entries;
}

if (mode === "--split") {
  splitExistingCatalog();
} else if (mode === "--write" || mode === "--check") {
  const sources = sourceFiles();
  const expected = render(sources);
  const actual = fs.readFileSync(generatedPath, "utf8");
  if (mode === "--write") {
    fs.writeFileSync(generatedPath, expected, "utf8");
    console.log(`Generated ${path.relative(repositoryRoot, generatedPath)} from source modules.`);
  } else if (normalizeLineEndings(actual) !== normalizeLineEndings(expected)) {
    console.error("Generated schema catalog is out of date. Run: node scripts/generate-schema-catalog.mjs --write");
    process.exitCode = 1;
  } else {
    console.log(`Schema catalog is current (${sources.length} entries).`);
  }
} else {
  throw new Error("Usage: node scripts/generate-schema-catalog.mjs [--split|--write|--check]");
}
