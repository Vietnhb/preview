import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

/**
 * Offline retrieval evaluation for the approved schema catalog.
 *
 * The fixture is deliberately catalog-derived and marked synthetic. It is
 * useful for deterministic algorithm regressions (BM25, cosine ordering and
 * RRF), but it is not evidence that a real embedding provider understands all
 * textbook wording. The provider-backed quality gate is reported separately
 * and remains blocked when no configured provider credentials are available.
 */

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const catalogPath = path.join(repositoryRoot, "backend", "src", "main", "resources", "schemas", "catalog.json");
const fixturePath = path.join(repositoryRoot, "docs", "evaluation", "schema-routing-retrieval-fixture.json");
const reportPath = path.join(repositoryRoot, "docs", "evaluation", "schema-routing-retrieval-report.json");
const mode = process.argv[2] ?? "--check";

const CONFIG = Object.freeze({
  lexicalTopK: 20,
  vectorTopK: 20,
  candidateTopK: 5,
  rrfK: 60,
  fakeEmbeddingDimension: 64,
  minimumMarginRatio: 0.08,
  requiredRecallAtK: 0.95,
  requiredPrecision: 0.99,
  requiredCoverage: 0.80,
});

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function compareVersions(left, right) {
  const a = String(left).split(".").map(part => Number(part) || 0);
  const b = String(right).split(".").map(part => Number(part) || 0);
  for (let i = 0; i < Math.max(a.length, b.length); i += 1) {
    const difference = (a[i] ?? 0) - (b[i] ?? 0);
    if (difference !== 0) return difference;
  }
  return 0;
}

function latestCatalogEntries(catalog) {
  const latest = new Map();
  for (const entry of catalog) {
    const previous = latest.get(entry.schemaId);
    if (!previous || compareVersions(entry.version, previous.version) > 0) latest.set(entry.schemaId, entry);
  }
  return [...latest.values()].sort((a, b) => identity(a).localeCompare(identity(b)));
}

function identity(entry) {
  return `${entry.schemaId}@${entry.version ?? entry.schemaVersion}`;
}

function text(value) {
  return typeof value === "string" ? value.trim() : "";
}

function list(value) {
  return Array.isArray(value) ? value.filter(item => typeof item === "string" && item.trim()).map(item => item.trim()) : [];
}

function quantityMetadata(entry) {
  const definition = entry.definition ?? {};
  const quantities = [
    ...(Array.isArray(definition.requiredQuantities) ? definition.requiredQuantities : []),
    ...(Array.isArray(definition.optionalQuantities) ? definition.optionalQuantities : []),
  ];
  return quantities.map(quantity => ({
    key: text(quantity.key),
    aliases: list(quantity.aliases),
    symbols: list(quantity.symbols),
    units: list(quantity.allowedUnits),
  })).filter(quantity => quantity.key);
}

function projection(entry) {
  const definition = entry.definition ?? {};
  const quantities = quantityMetadata(entry);
  const metadata = new Set([
    text(entry.schemaId), text(entry.version), text(entry.name), text(entry.topic), text(entry.model),
    text(definition.description), ...list(definition.learningOutcomes), ...list(definition.curriculumLabels),
    ...list(definition.curriculumLabel), ...list(definition.relationTypes),
    ...list(definition.endConditionCapabilities),
  ]);
  for (const quantity of quantities) {
    metadata.add(quantity.key);
    quantity.aliases.forEach(value => metadata.add(value));
    quantity.symbols.forEach(value => metadata.add(value));
    quantity.units.forEach(value => metadata.add(value));
  }
  const execution = definition.execution ?? {};
  list(execution.endConditionCapabilities).forEach(value => metadata.add(value));
  list(execution.relationTypes).forEach(value => metadata.add(value));
  return {
    schemaId: entry.schemaId,
    schemaVersion: entry.version,
    topic: entry.topic,
    name: text(entry.name),
    quantities,
    searchText: [...metadata].filter(Boolean).sort().join(" ").normalize("NFKC"),
  };
}

function tokenize(value) {
  const normalized = String(value ?? "").normalize("NFKC");
  const matches = normalized.match(/[\p{L}\p{M}\p{N}]+(?:[_][\p{L}\p{M}\p{N}]+)*|[\p{S}\p{P}]/gu) ?? [];
  return matches.map(token => /[\p{L}\p{M}]/u.test(token) ? token.toLocaleLowerCase("und") : token);
}

function buildBm25(corpus) {
  const documents = corpus.map(document => {
    const terms = tokenize(document.searchText);
    const frequencies = new Map();
    terms.forEach(term => frequencies.set(term, (frequencies.get(term) ?? 0) + 1));
    return { document, frequencies, length: terms.length };
  });
  const df = new Map();
  documents.forEach(document => document.frequencies.forEach((_, term) => df.set(term, (df.get(term) ?? 0) + 1)));
  const averageLength = documents.reduce((total, document) => total + document.length, 0) / Math.max(1, documents.length);
  return { documents, df, averageLength };
}

function rankBm25(query, index) {
  const terms = [...new Set(tokenize(query))];
  const scores = [];
  for (const document of index.documents) {
    let score = 0;
    for (const term of terms) {
      const tf = document.frequencies.get(term) ?? 0;
      if (!tf) continue;
      const df = index.df.get(term) ?? 0;
      const idf = Math.log1p((index.documents.length - df + 0.5) / (df + 0.5));
      const normalization = 1 - 0.75 + 0.75 * document.length / index.averageLength;
      score += idf * (tf * (1.2 + 1)) / (tf + 1.2 * normalization);
    }
    if (score > 0) scores.push({ id: identity(document.document), score });
  }
  return scores.sort((a, b) => b.score - a.score || a.id.localeCompare(b.id))
    .slice(0, CONFIG.lexicalTopK)
    .map((item, index) => ({ ...item, rank: index + 1 }));
}

function hashToken(token) {
  let hash = 2166136261;
  for (const codePoint of token) {
    hash ^= codePoint.codePointAt(0);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

function fakeEmbedding(textValue) {
  const vector = new Array(CONFIG.fakeEmbeddingDimension).fill(0);
  for (const token of tokenize(textValue)) {
    const hash = hashToken(token);
    const index = hash % vector.length;
    vector[index] += (hash & 1) === 0 ? 1 : -1;
  }
  const norm = Math.sqrt(vector.reduce((total, value) => total + value * value, 0));
  return norm === 0 ? vector : vector.map(value => value / norm);
}

function cosine(left, right) {
  return left.reduce((total, value, index) => total + value * right[index], 0);
}

function rankVector(query, corpus) {
  const queryVector = fakeEmbedding(query);
  return corpus.map(document => ({
    id: identity(document),
    score: cosine(queryVector, fakeEmbedding(document.searchText)),
  })).sort((a, b) => b.score - a.score || a.id.localeCompare(b.id))
    .slice(0, CONFIG.vectorTopK)
    .map((item, index) => ({ ...item, rank: index + 1 }));
}

function fuse(lexical, vector) {
  const scores = new Map();
  for (const ranking of [lexical, vector]) {
    const seen = new Set();
    for (const item of ranking) {
      if (seen.has(item.id)) continue;
      seen.add(item.id);
      scores.set(item.id, (scores.get(item.id) ?? 0) + 1 / (CONFIG.rrfK + item.rank));
    }
  }
  return [...scores.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .slice(0, CONFIG.candidateTopK)
    .map(([id, score], index) => ({ id, score, rank: index + 1 }));
}

function verificationScore(query, document) {
  const queryTerms = new Set(tokenize(query));
  if (queryTerms.size === 0) return 0;
  const metadataTerms = new Set(tokenize(document.searchText));
  const metadataOverlap = [...queryTerms].filter(term => metadataTerms.has(term)).length / queryTerms.size;
  const quantityTerms = new Set(document.quantities.flatMap(quantity => [
    quantity.key, ...quantity.aliases, ...quantity.symbols,
  ].flatMap(tokenize)));
  const quantityOverlap = [...queryTerms].filter(term => quantityTerms.has(term)).length
    / Math.max(1, quantityTerms.size);
  const unitTerms = new Set(document.quantities.flatMap(quantity => quantity.units.flatMap(tokenize)));
  const unitOverlap = [...queryTerms].filter(term => unitTerms.has(term)).length / queryTerms.size;
  return quantityOverlap * 0.55 + unitOverlap * 0.25 + metadataOverlap * 0.20;
}

function verifyCandidates(query, fused, corpus) {
  const byId = new Map(corpus.map(document => [identity(document), document]));
  return fused.map(candidate => ({
    ...candidate,
    confidence: verificationScore(query, byId.get(candidate.id)),
  })).sort((left, right) => right.confidence - left.confidence
    || right.score - left.score || left.id.localeCompare(right.id));
}

function queryFor(document, index) {
  const quantity = document.quantities[0];
  const identifier = quantity?.symbols[0] ?? quantity?.aliases[0] ?? quantity?.key ?? "đại lượng";
  const unit = quantity?.units[0] ?? "đơn vị phù hợp";
  const facts = document.quantities.map(item => {
    const key = item.symbols[0] ?? item.aliases[0] ?? item.key;
    return `${key} 2 ${item.units[0] ?? "đơn vị"}`;
  }).join(", ");
  const styles = [
    `Mô phỏng ${document.name}: ${facts}`,
    `Bài toán ${document.name.toLocaleLowerCase("vi")} với ${facts}`,
    `Tính ${document.name}; dữ kiện ${facts}`,
  ];
  let query = styles[index % styles.length];
  if (index % 7 === 0) query = query.replaceAll("đại lượng", "dai luong");
  if (index % 11 === 0) query = query.replaceAll(" = ", " → ");
  return query;
}

function buildFixture(entries) {
  const documents = entries.map(projection);
  const answerable = documents.map((document, index) => ({
    id: `answerable-${String(index + 1).padStart(3, "0")}`,
    split: index % 5 === 0 ? "heldOut" : "calibration",
    label: "ANSWERABLE",
    provenance: "synthetic catalog-derived query; no official textbook attribution",
    query: queryFor(document, index),
    acceptableIdentities: [identity(document)],
    topic: document.topic,
  }));
  const byTopic = new Map();
  documents.forEach(document => {
    const listForTopic = byTopic.get(document.topic) ?? [];
    listForTopic.push(document);
    byTopic.set(document.topic, listForTopic);
  });
  const ambiguous = [...byTopic.values()].filter(items => items.length >= 2).slice(0, 3).map((items, index) => ({
    id: `ambiguous-${String(index + 1).padStart(3, "0")}`,
    split: "challenge",
    label: "AMBIGUOUS",
    provenance: "synthetic confuser query combining two approved identities",
    query: `So sánh ${items[0].name} và ${items[1].name} trong cùng một bài toán`,
    acceptableIdentities: [identity(items[0]), identity(items[1])],
    topic: items[0].topic,
  }));
  const outOfScope = [
    "Dự báo thời tiết và lượng mưa ngày mai",
    "Viết chương trình quản lý học phí cho trường học",
  ].map((query, index) => ({
    id: `out-of-scope-${String(index + 1).padStart(3, "0")}`,
    split: "challenge",
    label: "OUT_OF_SCOPE",
    provenance: "synthetic out-of-scope challenge; no approved physics identity",
    query,
    acceptableIdentities: [],
    topic: null,
  }));
  return {
    fixtureVersion: "1",
    generatedBy: "scripts/evaluate-schema-routing-retrieval.mjs",
    sourceCatalog: "backend/src/main/resources/schemas/catalog.json",
    sourceIdentityRule: "latest approved version per schemaId, sorted by schemaId@version",
    provenancePolicy: "Synthetic catalog-derived examples are not textbook evidence.",
    splitPolicy: "Every active identity has one answerable case; every fifth sorted identity is held out.",
    thresholds: {
      candidateRecallAtK: CONFIG.requiredRecallAtK,
      confidentSelectionPrecision: CONFIG.requiredPrecision,
      unambiguousCoverage: CONFIG.requiredCoverage,
      ambiguousAndOutOfScopeConfidentGuesses: 0,
    },
    cases: [...answerable, ...ambiguous, ...outOfScope],
  };
}

function wilson(successes, total) {
  if (total === 0) return { lower95: null, upper95: null };
  const z = 1.959963984540054;
  const proportion = successes / total;
  const denominator = 1 + z * z / total;
  const center = (proportion + z * z / (2 * total)) / denominator;
  const radius = z * Math.sqrt((proportion * (1 - proportion) + z * z / (4 * total)) / total) / denominator;
  return { lower95: Math.max(0, center - radius), upper95: Math.min(1, center + radius) };
}

function evaluate(fixture, entries) {
  const corpus = entries.map(projection);
  const index = buildBm25(corpus);
  const results = fixture.cases.map(testCase => {
    const lexical = rankBm25(testCase.query, index);
    const vector = rankVector(testCase.query, corpus);
    const fused = fuse(lexical, vector);
    const candidates = verifyCandidates(testCase.query, fused, corpus);
    const first = candidates[0];
    const second = candidates[1];
    const marginRatio = first && second ? (first.confidence - second.confidence) / Math.max(first.confidence, Number.EPSILON) : first ? 1 : 0;
    const confident = Boolean(first && first.confidence >= 0.25 && marginRatio >= CONFIG.minimumMarginRatio);
    const recall = testCase.acceptableIdentities.some(id => fused.some(candidate => candidate.id === id));
    const top1Correct = Boolean(first && testCase.acceptableIdentities.includes(first.id));
    return {
      id: testCase.id,
      split: testCase.split,
      label: testCase.label,
      topic: testCase.topic,
      acceptableIdentities: testCase.acceptableIdentities,
      candidateRecallAtK: recall,
      top1Correct,
      confident,
      confidentCorrect: confident && top1Correct,
      marginRatio,
      selectedIdentity: confident ? first.id : null,
      lexicalTop: lexical[0]?.id ?? null,
      vectorTop: vector[0]?.id ?? null,
    };
  });
  const answerableHeldOut = results.filter(result => result.split === "heldOut" && result.label === "ANSWERABLE");
  const answerableAll = results.filter(result => result.label === "ANSWERABLE");
  const challenge = results.filter(result => result.split === "challenge");
  const confidentAnswerable = answerableHeldOut.filter(result => result.confident);
  const byTopic = {};
  for (const result of answerableHeldOut) {
    const topic = result.topic ?? "UNKNOWN";
    byTopic[topic] ??= { total: 0, recalled: 0, confident: 0, confidentCorrect: 0 };
    byTopic[topic].total += 1;
    byTopic[topic].recalled += Number(result.candidateRecallAtK);
    byTopic[topic].confident += Number(result.confident);
    byTopic[topic].confidentCorrect += Number(result.confidentCorrect);
  }
  const summary = {
    fixtureVersion: fixture.fixtureVersion,
    evaluationMode: "offline_deterministic_embedding_fake",
    providerQualityGate: { status: "BLOCKED", reason: "No real embedding-provider credentials/evidence supplied to this offline command." },
    corpusSize: corpus.length,
    caseCounts: {
      calibration: results.filter(result => result.split === "calibration").length,
      heldOutAnswerable: answerableHeldOut.length,
      challenge: challenge.length,
      answerableTotal: answerableAll.length,
    },
    heldOut: {
      candidateRecallAtK: rate(answerableHeldOut.filter(result => result.candidateRecallAtK).length, answerableHeldOut.length),
      candidateRecallConfidence95: wilson(answerableHeldOut.filter(result => result.candidateRecallAtK).length, answerableHeldOut.length),
      confidentSelectionPrecision: rate(confidentAnswerable.filter(result => result.confidentCorrect).length, confidentAnswerable.length),
      confidentSelectionPrecisionConfidence95: wilson(confidentAnswerable.filter(result => result.confidentCorrect).length, confidentAnswerable.length),
      unambiguousCoverage: rate(confidentAnswerable.length, answerableHeldOut.length),
      confidentSelections: confidentAnswerable.length,
    },
    challenge: {
      confidentGuesses: challenge.filter(result => result.confident).length,
      total: challenge.length,
    },
    byTopic,
    releaseGate: {
      offlineAlgorithmGate: true,
      providerQualityGate: false,
      heldOutThresholdsMet: summaryGate(answerableHeldOut, confidentAnswerable, challenge),
      status: "BLOCKED_EXTERNAL_PROVIDER",
    },
    cases: results,
  };
  return summary;
}

function rate(numerator, denominator) {
  return denominator === 0 ? null : numerator / denominator;
}

function summaryGate(heldOut, confident, challenge) {
  const recall = rate(heldOut.filter(result => result.candidateRecallAtK).length, heldOut.length) ?? 0;
  const precision = rate(confident.filter(result => result.confidentCorrect).length, confident.length) ?? 0;
  const coverage = rate(confident.length, heldOut.length) ?? 0;
  return recall >= CONFIG.requiredRecallAtK
    && precision >= CONFIG.requiredPrecision
    && coverage >= CONFIG.requiredCoverage
    && challenge.every(result => !result.confident);
}

function validateFixture(fixture, entries) {
  if (fixture.fixtureVersion !== "1") throw new Error("fixtureVersion must be 1");
  const expected = new Set(entries.map(identity));
  const seen = new Set();
  const answerable = fixture.cases.filter(testCase => testCase.label === "ANSWERABLE");
  for (const testCase of fixture.cases) {
    if (!testCase.id || seen.has(testCase.id)) throw new Error(`Duplicate or missing case id: ${testCase.id}`);
    seen.add(testCase.id);
    if (!["calibration", "heldOut", "challenge"].includes(testCase.split)) throw new Error(`${testCase.id}: invalid split`);
    if (!["ANSWERABLE", "AMBIGUOUS", "OUT_OF_SCOPE"].includes(testCase.label)) throw new Error(`${testCase.id}: invalid label`);
    if (typeof testCase.query !== "string" || !testCase.query.trim()) throw new Error(`${testCase.id}: query is required`);
    if (!Array.isArray(testCase.acceptableIdentities)) throw new Error(`${testCase.id}: acceptableIdentities is required`);
    testCase.acceptableIdentities.forEach(id => { if (!expected.has(id)) throw new Error(`${testCase.id}: unknown identity ${id}`); });
  }
  const represented = new Set(answerable.flatMap(testCase => testCase.acceptableIdentities));
  for (const id of expected) if (!represented.has(id)) throw new Error(`fixture has no answerable case for ${id}`);
  if (!answerable.some(testCase => testCase.split === "heldOut")) throw new Error("fixture must have held-out answerable cases");
}

const entries = latestCatalogEntries(readJson(catalogPath));
if (mode === "--write") {
  const fixture = buildFixture(entries);
  fs.mkdirSync(path.dirname(fixturePath), { recursive: true });
  fs.writeFileSync(fixturePath, `${JSON.stringify(fixture, null, 2)}\n`, "utf8");
  console.log(`Generated ${path.relative(repositoryRoot, fixturePath)} for ${entries.length} active identities.`);
}

if (!fs.existsSync(fixturePath)) throw new Error(`Missing ${path.relative(repositoryRoot, fixturePath)}; run with --write first`);
const fixture = readJson(fixturePath);
validateFixture(fixture, entries);
const report = evaluate(fixture, entries);
fs.mkdirSync(path.dirname(reportPath), { recursive: true });
if (mode === "--write") {
  fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
} else {
  if (!fs.existsSync(reportPath)) throw new Error(`Missing ${path.relative(repositoryRoot, reportPath)}; run with --write first`);
  const existingReport = readJson(reportPath);
  if (JSON.stringify(existingReport) !== JSON.stringify(report)) {
    throw new Error(`${path.relative(repositoryRoot, reportPath)} is stale; run with --write and review the deterministic diff`);
  }
}
console.log(JSON.stringify({
  corpusSize: report.corpusSize,
  caseCounts: report.caseCounts,
  heldOut: report.heldOut,
  challenge: report.challenge,
  releaseGate: report.releaseGate,
  report: path.relative(repositoryRoot, reportPath),
}, null, 2));
if (mode === "--release" && report.releaseGate.status !== "PASSED") {
  throw new Error(`Retrieval release gate is ${report.releaseGate.status}; real provider evidence is required.`);
}
