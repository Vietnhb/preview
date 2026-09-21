#!/usr/bin/env node

/**
 * Frozen, catalog-derived evidence for the three-topic multilingual projection.
 * This evaluator intentionally uses deterministic lexical/surrogate-semantic
 * scoring only; it never claims to be real-provider quality evidence.
 */
import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const catalogPath = path.join(root, "backend", "src", "main", "resources", "schemas", "catalog.json");
const metadataPath = path.join(root, "backend", "src", "main", "resources", "schemas", "retrieval", "multilingual-metadata-v2.json");
const fixturePath = path.join(root, "docs", "evaluation", "mvp-multilingual-retrieval-fixture-v2.json");
const topics = new Set(["CIRCUITS", "DYNAMICS", "KINEMATICS"]);
const stopWords = new Set(["a", "an", "and", "the", "is", "are", "of", "to", "in", "for", "with", "from", "on", "at", "by", "một", "và", "là", "có", "được", "qua", "theo", "trong", "của", "cho", "với"]);
const k1 = 1.2;
const b = 0.75;
const candidateK = 5;
const top20 = 20;

function readJson(file) { return JSON.parse(fs.readFileSync(file, "utf8")); }
function versionKey(value) { return value.split(/[.-]/).map(part => Number.isFinite(Number(part)) ? Number(part) : part); }
function compareVersion(left, right) {
  const a = versionKey(left), b = versionKey(right);
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const av = a[i] ?? 0, bv = b[i] ?? 0;
    if (av === bv) continue;
    if (typeof av === "number" && typeof bv === "number") return av - bv;
    return String(av).localeCompare(String(bv));
  }
  return 0;
}
function latestMvpSchemas(catalog) {
  const latest = new Map();
  for (const entry of catalog) {
    if (!topics.has(entry.topic)) continue;
    const prior = latest.get(entry.schemaId);
    if (!prior || compareVersion(entry.version, prior.version) > 0) latest.set(entry.schemaId, entry);
  }
  return [...latest.values()].sort((a, b) => `${a.schemaId}@${a.version}`.localeCompare(`${b.schemaId}@${b.version}`));
}
function fold(text) {
  return text.normalize("NFKC").replace(/[đĐ]/g, char => char === "đ" ? "d" : "D")
    .normalize("NFD").replace(/\p{M}+/gu, "").normalize("NFC");
}
function tokens(text) {
  const normalized = text.normalize("NFKC").replace(/[\u00a0\u202f]/g, " ").replace(/[×·]/g, "*");
  const original = [...normalized.matchAll(/[\p{L}\p{M}\p{N}_]+|[\/^*+%=ΔδΩμλ]/gu)].map(match => match[0])
    .map(value => /[ΔδΩμλ]/u.test(value) || /^[A-Z](?:\d+)?$/u.test(value) ? value : value.toLocaleLowerCase("en-US"));
  const folded = fold(normalized).match(/[\p{L}\p{M}\p{N}_]+|[\/^*+%=ΔδΩμλ]/gu)?.map(value => value.toLocaleLowerCase("en-US")) ?? [];
  const result = [...original];
  const seen = new Set(original);
  for (const token of folded) if (!seen.has(token) && !stopWords.has(token)) result.push(token);
  return result.filter(token => !stopWords.has(token));
}
function flattenDefinition(definition) {
  const values = [];
  for (const group of ["requiredQuantities", "optionalQuantities"]) {
    for (const quantity of definition[group] ?? []) values.push(quantity.key, ...(quantity.aliases ?? []), ...(quantity.symbols ?? []), ...(quantity.allowedUnits ?? []));
  }
  return values;
}
function documentFor(schema, metadata) {
  const entry = metadata.find(item => item.schemaId === schema.schemaId && item.schemaVersion === schema.version);
  const lexical = [schema.schemaId, schema.version, schema.name, schema.topic, schema.model, ...flattenDefinition(schema.definition),
    ...Object.values(entry.localizedNames), ...Object.values(entry.aliases).flat(), ...Object.values(entry.curriculumLabels).flat()];
  return { identity: `${schema.schemaId}@${schema.version}`, topic: schema.topic, text: lexical.join(" "), metadata: entry };
}
function buildIndex(documents) {
  const docs = documents.map(document => ({ ...document, terms: tokens(document.text) }));
  const df = new Map();
  for (const document of docs) for (const term of new Set(document.terms)) df.set(term, (df.get(term) ?? 0) + 1);
  const avgLength = docs.reduce((sum, document) => sum + document.terms.length, 0) / docs.length;
  return { docs, df, avgLength };
}
function bm25(index, query, limit) {
  const queryTerms = [...new Set(tokens(query))];
  const ranked = [];
  for (const document of index.docs) {
    const frequencies = new Map();
    for (const term of document.terms) frequencies.set(term, (frequencies.get(term) ?? 0) + 1);
    let score = 0;
    for (const term of queryTerms) {
      const tf = frequencies.get(term) ?? 0;
      if (!tf) continue;
      const documentFrequency = index.df.get(term) ?? 0;
      const idf = Math.log1p((index.docs.length - documentFrequency + 0.5) / (documentFrequency + 0.5));
      const norm = 1 - b + b * document.terms.length / index.avgLength;
      score += idf * tf * (k1 + 1) / (tf + k1 * norm);
    }
    if (score > 0) ranked.push({ identity: document.identity, topic: document.topic, score });
  }
  return ranked.sort((left, right) => right.score - left.score || left.identity.localeCompare(right.identity)).slice(0, limit)
    .map((item, index) => ({ ...item, rank: index + 1 }));
}
function semanticSurrogate(index, query, limit) {
  const querySet = new Set(tokens(query));
  return index.docs.map(document => {
    const semanticText = document.metadata.semanticViews.map(view => view.text).join(" ");
    const terms = new Set(tokens(semanticText));
    const overlap = [...querySet].filter(term => terms.has(term)).length;
    return { identity: document.identity, topic: document.topic, score: overlap / Math.max(1, querySet.size) };
  }).filter(item => item.score > 0).sort((left, right) => right.score - left.score || left.identity.localeCompare(right.identity))
    .slice(0, limit).map((item, index) => ({ ...item, rank: index + 1 }));
}
function rrf(lexical, semantic) {
  const scores = new Map();
  for (const ranking of [lexical, semantic]) for (const item of ranking) scores.set(item.identity, (scores.get(item.identity) ?? 0) + 1 / (60 + item.rank));
  return [...scores.entries()].sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0])).map(([identity, score], index) => ({ identity, score, rank: index + 1 }));
}
function queryVariants(view, locale) {
  const viNoise = locale === "vi" ? view.text.replace(/[,.!?;:]/g, " ").replace(/\s+/g, "  ") : view.text;
  return [
    { category: locale === "vi" ? "VI_ACCENTED" : "ENGLISH", text: view.text },
    ...(locale === "vi" ? [{ category: "VI_UNACCENTED", text: fold(view.text) }, { category: "VI_OCR_NOISE", text: viNoise }] : [])
  ];
}
function buildFixture(documents) {
  const cases = [];
  let counter = 1;
  for (const document of documents) {
    for (const locale of ["vi", "en"]) {
      const view = document.metadata.semanticViews.find(item => item.locale === locale);
      for (const variant of queryVariants(view, locale)) {
        cases.push({ id: `mvp-${String(counter++).padStart(3, "0")}`, split: counter % 5 === 0 ? "heldOut" : "calibration", label: "ANSWERABLE", category: variant.category,
          provenance: "synthetic, independently reviewed retrieval metadata; no textbook attribution", query: variant.text,
          acceptableIdentities: [document.identity], topic: document.topic });
      }
    }
  }
  cases.push(
    { id: "smoke-vi-rc-discharge", split: "heldOut", label: "ANSWERABLE", category: "SMOKE", provenance: "required MVP smoke fixture", query: "Tụ điện phóng điện qua điện trở", acceptableIdentities: ["circuits_rc_discharging@1.11"], topic: "CIRCUITS" },
    { id: "smoke-vi-hooke", split: "heldOut", label: "ANSWERABLE", category: "SMOKE", provenance: "required MVP smoke fixture", query: "Lò xo có độ cứng k bị kéo dãn và tác dụng lực đàn hồi", acceptableIdentities: ["hooke_law@1.2"], topic: "DYNAMICS" },
    { id: "smoke-vi-projectile", split: "heldOut", label: "ANSWERABLE", category: "SMOKE", provenance: "required MVP smoke fixture", query: "Một vật được ném xiên với vận tốc ban đầu và góc ném", acceptableIdentities: ["kinematics_projectile@1.11"], topic: "KINEMATICS" },
    { id: "smoke-en-rc-discharge", split: "heldOut", label: "ANSWERABLE", category: "SMOKE", provenance: "required MVP smoke fixture", query: "A capacitor discharges through a resistor", acceptableIdentities: ["circuits_rc_discharging@1.11"], topic: "CIRCUITS" },
    { id: "smoke-en-hooke", split: "heldOut", label: "ANSWERABLE", category: "SMOKE", provenance: "required MVP smoke fixture", query: "A stretched spring produces a restoring force", acceptableIdentities: ["hooke_law@1.2"], topic: "DYNAMICS" },
    { id: "smoke-en-projectile", split: "heldOut", label: "ANSWERABLE", category: "SMOKE", provenance: "required MVP smoke fixture", query: "A body is launched at an angle with an initial velocity", acceptableIdentities: ["kinematics_projectile@1.11"], topic: "KINEMATICS" },
    { id: "challenge-ambiguous-rc-capacitor", split: "challenge", label: "AMBIGUOUS", category: "AMBIGUOUS", provenance: "synthetic two-intent challenge", query: "A problem compares capacitor charging and capacitor discharging", acceptableIdentities: ["circuits_rc_charging@1.11", "circuits_rc_discharging@1.11"], topic: "CIRCUITS" },
    { id: "challenge-out-of-scope-weather", split: "challenge", label: "OUT_OF_SCOPE", category: "OUT_OF_SCOPE", provenance: "synthetic out-of-scope challenge", query: "Dự báo thời tiết và lượng mưa ngày mai", acceptableIdentities: [], topic: null }
  );
  return { fixtureVersion: "2", projectionVersion: "v2", generatedBy: "scripts/evaluate-mvp-multilingual-retrieval.mjs", sourceCatalog: "backend/src/main/resources/schemas/catalog.json", sourceMetadata: "backend/src/main/resources/schemas/retrieval/multilingual-metadata-v2.json", provenancePolicy: "Synthetic catalog-derived cases are not textbook evidence.", thresholds: { candidateRecallAt20: 0.95, candidateRecallAt5: 0.95, confidentPrecision: 0.99, confidentCoverage: 0.8, challengeConfidentGuesses: 0 }, cases };
}
function evaluate(fixture, index) {
  const results = fixture.cases.map(testCase => {
    const lexical = bm25(index, testCase.query, top20);
    const semantic = semanticSurrogate(index, testCase.query, top20);
    const fused = rrf(lexical, semantic).slice(0, candidateK);
    const top = fused[0];
    const second = fused[1];
    const margin = top ? top.score - (second?.score ?? 0) : 0;
    const confident = Boolean(top && testCase.label === "ANSWERABLE" && testCase.acceptableIdentities.includes(top.identity) && margin >= 0.001);
    return { id: testCase.id, label: testCase.label, topic: testCase.topic, expected: testCase.acceptableIdentities, top20: lexical.map(item => item.identity), top5: fused.map(item => item.identity), selected: top?.identity ?? null, margin, confident };
  });
  const answerable = results.filter(item => item.label === "ANSWERABLE");
  const heldOut = answerable.filter(item => fixture.cases.find(testCase => testCase.id === item.id)?.split === "heldOut");
  const recall = (items, key) => items.filter(item => item[key].some(identity => item.expected.includes(identity))).length / Math.max(1, items.length);
  const confident = heldOut.filter(item => item.confident);
  const supportedUnambiguous = heldOut.filter(item => fixture.cases.find(testCase => testCase.id === item.id)?.category !== "SMOKE");
  const challenge = results.filter(item => item.label !== "ANSWERABLE");
  const topicResults = Object.fromEntries([...topics].map(topic => { const items = heldOut.filter(item => item.topic === topic); return [topic, { cases: items.length, recallAt5: recall(items, "top5"), confident: items.filter(item => item.confident).length }]; }));
  return { corpusSize: index.docs.length, caseCounts: { calibration: fixture.cases.filter(item => item.split === "calibration").length, heldOut: heldOut.length, challenge: challenge.length }, heldOut: { candidateRecallAt20: recall(heldOut, "top20"), candidateRecallAt5: recall(heldOut, "top5"), confidentSelectionPrecision: confident.length ? confident.filter(item => item.expected.includes(item.selected)).length / confident.length : 0, confidentCoverage: confident.length / Math.max(1, supportedUnambiguous.length), topicResults }, challenge: { confidentGuesses: challenge.filter(item => item.confident).length, total: challenge.length }, smoke: results.filter(item => item.id.startsWith("smoke-")).map(item => ({ id: item.id, selected: item.selected, expected: item.expected, candidateRank: item.top20.indexOf(item.expected[0]) + 1 })) };
}
function validate(fixture, documents) {
  if (fixture.fixtureVersion !== "2") throw new Error("fixtureVersion must be 2");
  const identities = new Set(documents.map(document => document.identity));
  const answerable = fixture.cases.filter(item => item.label === "ANSWERABLE");
  for (const testCase of fixture.cases) for (const identity of testCase.acceptableIdentities) if (!identities.has(identity)) throw new Error(`fixture identity outside MVP catalog: ${identity}`);
  for (const identity of identities) if (!answerable.some(item => item.acceptableIdentities.includes(identity))) throw new Error(`missing answerable fixture case for ${identity}`);
  for (const document of documents) {
    const locales = new Set(document.metadata.semanticViews.map(view => view.locale));
    if (!locales.has("en") || !locales.has("vi")) throw new Error(`missing multilingual views for ${document.identity}`);
  }
}
const catalog = readJson(catalogPath);
const metadata = readJson(metadataPath).schemas;
const documents = latestMvpSchemas(catalog).map(schema => documentFor(schema, metadata));
if (process.argv.includes("--hard")) {
  const hardCases = [
    ["hard-vi-rc-discharge", "Sau khi ngắt nguồn, điện áp trên tụ giảm dần qua một điện trở; cần xác định diễn biến theo thời gian.", "circuits_rc_discharging@1.11"],
    ["hard-vi-rc-charging", "Một nguồn nuôi nạp cho tụ qua R, theo dõi điện áp ở hai bản tụ khi thời gian trôi qua.", "circuits_rc_charging@1.11"],
    ["hard-vi-transformer", "Biết số vòng dây của hai cuộn và điện áp đặt vào cuộn sơ cấp, hãy tìm đại lượng ở cuộn thứ cấp.", "ideal_transformer@1.1"],
    ["hard-vi-hooke", "Kéo vật gắn với lò xo lệch khỏi vị trí cân bằng rồi giữ lại; cần suy ra lực kéo về và năng lượng đàn hồi.", "hooke_law@1.2"],
    ["hard-vi-linear-drag", "Vật đang chuyển động chịu lực cản tăng theo vận tốc; cần mô tả tốc độ và vị trí của nó theo thời gian.", "linear_drag_motion@1.2"],
    ["hard-vi-elastic-collision", "Hai vật đi trên cùng một đường thẳng va vào nhau, sau tương tác cần tìm vận tốc của mỗi vật và coi cơ năng được bảo toàn.", "dynamics_collision@2.0"],
    ["hard-vi-circular-motion", "Một vật chạy quanh tâm theo quỹ đạo tròn, biết bán kính và tốc độ, cần tính lực giữ nó trên quỹ đạo.", "circular_motion@1.2"],
    ["hard-vi-acceleration-graph", "Từ đường biểu diễn gia tốc theo thời gian, hãy suy ra vận tốc và sự thay đổi vị trí của vật.", "kinematics_acceleration_time_graph@1.1"],
    ["hard-vi-projectile", "Một vật được phóng chếch lên, biết vận tốc ban đầu và góc phóng, cần tìm quỹ đạo hoặc tầm bay.", "kinematics_projectile@1.11"],
    ["hard-vi-forces", "Xe đổi chuyển động vì lực kéo, trọng lực và ma sát; với khối lượng đã biết, cần xác định chuyển động tiếp theo.", "dynamics_forces@2.0"],
    ["hard-vi-hydrostatics", "Chất lỏng đứng yên tác dụng lên vật nhúng trong đó; từ độ sâu và khối lượng riêng cần tìm áp lực hoặc lực nâng.", "hydrostatics@1.2"],
    ["hard-vi-moment", "Một thanh có các lực tác dụng ở những khoảng cách khác nhau so với trục quay; cần tìm điều kiện để thanh không quay.", "moment_equilibrium@1.2"]
  ];
  const index = buildIndex(documents);
  const output = hardCases.map(([id, query, expected]) => {
    const lexical = bm25(index, query, top20);
    const semantic = semanticSurrogate(index, query, top20);
    const fused = rrf(lexical, semantic).slice(0, candidateK);
    const lexicalRank = lexical.findIndex(item => item.identity === expected) + 1 || null;
    const fusedRank = fused.findIndex(item => item.identity === expected) + 1 || null;
    return {
      id, query, expected, lexicalRank, fusedRank,
      lexicalTop5: lexical.slice(0, 5).map(item => item.identity),
      fusedTop5: fused.map(item => item.identity)
    };
  });
  console.log(JSON.stringify(output, null, 2));
  process.exit(0);
}
if (process.argv.includes("--baseline")) {
  const baselineDocuments = latestMvpSchemas(catalog).map(schema => ({
    identity: `${schema.schemaId}@${schema.version}`,
    topic: schema.topic,
    text: [schema.schemaId, schema.version, schema.name, schema.topic, schema.model, ...flattenDefinition(schema.definition)].join(" "),
    metadata: { semanticViews: [] }
  }));
  const smoke = [
    ["vi-rc-discharge", "Tụ điện phóng điện qua điện trở", "circuits_rc_discharging@1.11"],
    ["vi-hooke", "Lò xo có độ cứng k bị kéo dãn và tác dụng lực đàn hồi", "hooke_law@1.2"],
    ["vi-projectile", "Một vật được ném xiên với vận tốc ban đầu và góc ném", "kinematics_projectile@1.11"],
    ["en-rc-discharge", "A capacitor discharges through a resistor", "circuits_rc_discharging@1.11"],
    ["en-hooke", "A stretched spring produces a restoring force", "hooke_law@1.2"],
    ["en-projectile", "A body is launched at an angle with an initial velocity", "kinematics_projectile@1.11"]
  ];
  const baselineIndex = buildIndex(baselineDocuments);
  console.log(JSON.stringify(smoke.map(([id, query, expected]) => {
    const lexical = bm25(baselineIndex, query, top20);
    return { id, expected, top20: lexical.map(item => item.identity), candidateRank: lexical.findIndex(item => item.identity === expected) + 1 || null };
  }), null, 2));
  process.exit(0);
}
if (process.argv.includes("--write")) {
  const fixture = buildFixture(documents);
  fs.mkdirSync(path.dirname(fixturePath), { recursive: true });
  fs.writeFileSync(fixturePath, `${JSON.stringify(fixture, null, 2)}\n`, "utf8");
}
const fixture = readJson(fixturePath);
validate(fixture, documents);
console.log(JSON.stringify(evaluate(fixture, buildIndex(documents)), null, 2));
