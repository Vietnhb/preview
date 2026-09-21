import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const catalogPath = path.join(repositoryRoot, "backend", "src", "main", "resources", "schemas", "catalog.json");
const manifestPath = path.join(repositoryRoot, "docs", "physics", "model-evidence-manifest.json");
const mode = process.argv[2] ?? "--check";
const evidenceKinds = ["golden", "boundary", "invalidDomain", "invariant", "output", "faultDetection"];
const evidencePolicy = "PARTIAL entries may contain catalog-wide structural evidence while golden, boundary-domain, invariant, independent-reference, and fault evidence remain empty until reviewed per model; VERIFIED is never inferred from structural coverage.";

function readJson(file) {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch (error) {
    throw new Error(`${path.relative(repositoryRoot, file)} is not valid JSON: ${error.message}`);
  }
}

function compareVersions(left, right) {
  const leftParts = String(left).split(".").map(part => Number(part) || 0);
  const rightParts = String(right).split(".").map(part => Number(part) || 0);
  const length = Math.max(leftParts.length, rightParts.length);
  for (let index = 0; index < length; index += 1) {
    const difference = (leftParts[index] ?? 0) - (rightParts[index] ?? 0);
    if (difference !== 0) return difference;
  }
  return 0;
}

function latestCatalogEntries(catalog) {
  if (!Array.isArray(catalog)) throw new Error("schema catalog must be an array");
  const latest = new Map();
  for (const entry of catalog) {
    if (!entry || typeof entry !== "object" || !entry.schemaId || !entry.version || !entry.model) {
      throw new Error("every catalog entry must have schemaId, version, and model");
    }
    const previous = latest.get(entry.schemaId);
    if (!previous || compareVersions(entry.version, previous.version) > 0) latest.set(entry.schemaId, entry);
  }
  return [...latest.values()].sort((left, right) => left.schemaId.localeCompare(right.schemaId));
}

function expectedManifest(catalog) {
  return {
    manifestVersion: "1",
    sourceCatalog: "backend/src/main/resources/schemas/catalog.json",
    evidenceKinds,
    evidencePolicy,
    entries: latestCatalogEntries(catalog).map(entry => ({
      schemaId: entry.schemaId,
      schemaVersion: entry.version,
      modelId: entry.model,
      topic: entry.topic,
      status: "PENDING",
      evidence: Object.fromEntries(evidenceKinds.map(kind => [kind, []])),
    })),
  };
}

function normalizeRelativeReference(reference) {
  if (typeof reference !== "string" || reference.trim() === "") {
    throw new Error("evidence references must be non-empty repository-relative paths");
  }
  const normalized = reference.replaceAll("\\", "/");
  if (path.posix.isAbsolute(normalized) || /^[A-Za-z]:\//.test(normalized) || normalized.includes("..")) {
    throw new Error(`evidence reference escapes the repository: ${reference}`);
  }
  const absolute = path.join(repositoryRoot, normalized);
  if (!fs.existsSync(absolute)) throw new Error(`evidence reference does not exist: ${reference}`);
  return normalized;
}

function validateManifest(manifest, expected) {
  if (!manifest || manifest.manifestVersion !== "1") throw new Error("manifestVersion must be 1");
  if (manifest.sourceCatalog !== expected.sourceCatalog) throw new Error("sourceCatalog must point to the generated schema catalog");
  if (JSON.stringify(manifest.evidenceKinds) !== JSON.stringify(evidenceKinds)) {
    throw new Error(`evidenceKinds must be exactly ${evidenceKinds.join(", ")}`);
  }
  if (manifest.evidencePolicy !== evidencePolicy) {
    throw new Error("evidencePolicy must state that structural coverage cannot infer VERIFIED status");
  }
  if (!Array.isArray(manifest.entries)) throw new Error("entries must be an array");

  const expectedByIdentity = new Map(expected.entries.map(entry => [`${entry.schemaId}@${entry.schemaVersion}`, entry]));
  const seen = new Set();
  for (const entry of manifest.entries) {
    const identity = `${entry?.schemaId ?? ""}@${entry?.schemaVersion ?? ""}`;
    if (seen.has(identity)) throw new Error(`duplicate manifest identity: ${identity}`);
    seen.add(identity);
    const expectedEntry = expectedByIdentity.get(identity);
    if (!expectedEntry) throw new Error(`manifest identity is not a latest catalog identity: ${identity}`);
    for (const field of ["modelId", "topic"]) {
      if (entry[field] !== expectedEntry[field]) throw new Error(`${identity}: ${field} disagrees with catalog`);
    }
    if (!["PENDING", "PARTIAL", "VERIFIED"].includes(entry.status)) {
      throw new Error(`${identity}: status must be PENDING, PARTIAL, or VERIFIED`);
    }
    if (!entry.evidence || typeof entry.evidence !== "object") throw new Error(`${identity}: evidence is required`);
    for (const kind of evidenceKinds) {
      if (!Array.isArray(entry.evidence[kind])) throw new Error(`${identity}: evidence.${kind} must be an array`);
      entry.evidence[kind] = entry.evidence[kind].map(normalizeRelativeReference);
    }
    const evidenceCount = evidenceKinds.reduce((total, kind) => total + entry.evidence[kind].length, 0);
    if (entry.status === "VERIFIED" && evidenceCount !== evidenceKinds.length) {
      throw new Error(`${identity}: VERIFIED requires at least one reference for every evidence kind`);
    }
  }
  for (const identity of expectedByIdentity.keys()) {
    if (!seen.has(identity)) throw new Error(`manifest is missing latest catalog identity: ${identity}`);
  }
  if (manifest.entries.length !== expected.entries.length) throw new Error("manifest entry count does not match latest catalog identity count");
  return {
    entries: manifest.entries.length,
    pending: manifest.entries.filter(entry => entry.status === "PENDING").length,
    partial: manifest.entries.filter(entry => entry.status === "PARTIAL").length,
    verified: manifest.entries.filter(entry => entry.status === "VERIFIED").length,
  };
}

const expected = expectedManifest(readJson(catalogPath));
if (mode === "--write") {
  let existing = null;
  if (fs.existsSync(manifestPath)) existing = readJson(manifestPath);
  const existingByIdentity = new Map((existing?.entries ?? []).map(entry => [`${entry.schemaId}@${entry.schemaVersion}`, entry]));
  for (const entry of expected.entries) {
    const previous = existingByIdentity.get(`${entry.schemaId}@${entry.schemaVersion}`);
    if (previous) {
      entry.status = previous.status ?? entry.status;
      entry.evidence = previous.evidence ?? entry.evidence;
    }
  }
  fs.writeFileSync(manifestPath, `${JSON.stringify(expected, null, 2)}\n`, "utf8");
  console.log(`Generated ${path.relative(repositoryRoot, manifestPath)} for ${expected.entries.length} latest catalog identities.`);
} else if (mode !== "--check" && mode !== "--release") {
  throw new Error("Usage: node scripts/check-physics-evidence-manifest.mjs [--write|--check|--release]");
}

const manifest = readJson(manifestPath);
const summary = validateManifest(manifest, expected);
console.log(`Physics evidence manifest is structurally current: ${summary.entries} identities; `
  + `${summary.verified} verified, ${summary.partial} partial, ${summary.pending} pending.`);
if (mode === "--release" && (summary.pending > 0 || summary.partial > 0)) {
  throw new Error(`release evidence is incomplete: ${summary.verified}/${summary.entries} identities are VERIFIED`);
}
