import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const coveragePath = path.join(root, "docs", "curriculum", "coverage.csv");
const registryPath = path.join(root, "docs", "curriculum", "registry-ids.json");
const curriculumCatalogPath = path.join(
  root,
  "backend",
  "src",
  "main",
  "resources",
  "curriculum",
  "catalog.json",
);

function parseCsv(text) {
  const rows = [];
  let row = [];
  let cell = "";
  let quoted = false;
  for (let i = 0; i < text.length; i += 1) {
    const character = text[i];
    const next = text[i + 1];
    if (quoted) {
      if (character === '"' && next === '"') {
        cell += '"';
        i += 1;
      } else if (character === '"') {
        quoted = false;
      } else {
        cell += character;
      }
    } else if (character === '"') {
      quoted = true;
    } else if (character === ",") {
      row.push(cell.trim());
      cell = "";
    } else if (character === "\n") {
      row.push(cell.trim());
      if (row.some((value) => value.length > 0)) rows.push(row);
      row = [];
      cell = "";
    } else if (character !== "\r") {
      cell += character;
    }
  }
  if (cell.length > 0 || row.length > 0) {
    row.push(cell.trim());
    if (row.some((value) => value.length > 0)) rows.push(row);
  }
  return rows;
}

const requiredColumns = [
  "outcome_id",
  "grade",
  "scope",
  "textbook",
  "edition",
  "chapter",
  "lesson",
  "source_url",
  "source_locator",
  "requirement_summary",
  "representation_type",
  "model_ids",
  "template_ids",
  "activity_ids",
  "test_ids",
  "reviewer",
  "status",
  "gap",
];
const errors = [];
const csv = fs.readFileSync(coveragePath, "utf8");
const rows = parseCsv(csv);
if (rows.length === 0) errors.push("coverage.csv is empty");
const header = rows[0] ?? [];
if (
  header.length !== requiredColumns.length ||
  header.some((value, index) => value !== requiredColumns[index])
) {
  errors.push(
    `coverage.csv header must be exactly: ${requiredColumns.join(",")}`,
  );
}

const registry = JSON.parse(fs.readFileSync(registryPath, "utf8"));
const known = Object.fromEntries(
  Object.entries(registry).map(([key, values]) => [key, new Set(values)]),
);
const allowedStatus = new Set(["missing", "implemented", "tested", "approved"]);
const seen = new Set();
for (const [registryKey, values] of Object.entries(registry)) {
  if (!Array.isArray(values))
    errors.push(`registry ${registryKey} must be an array`);
  else if (new Set(values).size !== values.length)
    errors.push(`registry ${registryKey} contains duplicate IDs`);
}
const dataRows = rows.slice(1);
// Every lesson declared by the versioned curriculum catalog must have at
// least one lesson-level row in the evidence matrix. Aggregate outcome rows
// are allowed in addition to those rows.
const catalog = JSON.parse(fs.readFileSync(curriculumCatalogPath, "utf8"));
const catalogLessons = new Set();
const catalogGradeByLesson = new Map();
for (const topic of catalog.topics ?? []) {
  for (const module of topic.modules ?? []) {
    for (const level of module.levels ?? []) {
      const grade = String(level.name ?? "").match(/\d+/)?.[0] ?? "";
      for (const lesson of level.lessons ?? []) {
        if (!lesson.slug) continue;
        catalogLessons.add(lesson.slug);
        catalogGradeByLesson.set(lesson.slug, grade);
      }
    }
  }
}
const listedLessons = new Set(
  dataRows.map((values) => values[6]).filter(Boolean),
);
for (const lesson of catalogLessons) {
  if (!listedLessons.has(lesson))
    errors.push(`catalog lesson is missing from coverage.csv: ${lesson}`);
}
for (const [offset, values] of dataRows.entries()) {
  const line = offset + 2;
  if (values.length !== requiredColumns.length) {
    errors.push(
      `line ${line}: expected ${requiredColumns.length} columns, got ${values.length}`,
    );
    continue;
  }
  const record = Object.fromEntries(
    requiredColumns.map((key, index) => [key, values[index]]),
  );
  if (!record.outcome_id) errors.push(`line ${line}: outcome_id is required`);
  if (seen.has(record.outcome_id))
    errors.push(`line ${line}: duplicate outcome_id ${record.outcome_id}`);
  seen.add(record.outcome_id);
  for (const field of [
    "grade",
    "scope",
    "chapter",
    "lesson",
    "source_url",
    "source_locator",
    "requirement_summary",
    "representation_type",
    "status",
    "gap",
  ]) {
    if (!record[field]) errors.push(`line ${line}: ${field} is required`);
  }
  // Historical/aggregate rows (repo.*, pilot.*, wave.*, thermal.*, etc.)
  // may use a module or phenomenon slug. Canonical curriculum.* rows must
  // point to an actual versioned catalog lesson and matching grade.
  const canonicalLessonRow =
    record.outcome_id.startsWith("curriculum.") ||
    record.outcome_id.startsWith("practical.");
  if (
    canonicalLessonRow &&
    record.lesson &&
    !catalogLessons.has(record.lesson)
  ) {
    errors.push(
      `line ${line}: coverage lesson is not declared in curriculum catalog: ${record.lesson}`,
    );
  } else if (
    canonicalLessonRow &&
    record.lesson &&
    catalogGradeByLesson.get(record.lesson) !== record.grade
  ) {
    errors.push(
      `line ${line}: grade ${record.grade} does not match catalog grade ${catalogGradeByLesson.get(record.lesson)} for ${record.lesson}`,
    );
  }
  if (!/^\d+$/.test(record.grade))
    errors.push(`line ${line}: grade must be numeric`);
  if (!allowedStatus.has(record.status))
    errors.push(`line ${line}: invalid status ${record.status}`);
  for (const [field, registryKey] of [
    ["model_ids", "model_ids"],
    ["template_ids", "template_ids"],
    ["activity_ids", "activity_ids"],
    ["test_ids", "test_ids"],
  ]) {
    const value = record[field];
    if (!value || value === "missing") continue;
    for (const id of value
      .split("|")
      .map((token) => token.trim())
      .filter(Boolean)) {
      if (!known[registryKey]?.has(id))
        errors.push(`line ${line}: orphan ${field} mapping ${id}`);
    }
  }
  if (
    ["tested", "approved"].includes(record.status) &&
    record.test_ids === "missing"
  ) {
    errors.push(`line ${line}: ${record.status} rows require test_ids`);
  }
  if (
    record.status === "approved" &&
    (!record.reviewer || record.reviewer === "unassigned")
  ) {
    errors.push(`line ${line}: approved rows require reviewer`);
  }
}

const total = dataRows.length;
const approved = dataRows.filter((values) => values[16] === "approved").length;
const testedOrBetter = dataRows.filter((values) =>
  ["tested", "approved"].includes(values[16]),
).length;
const mapped = dataRows.filter(
  (values) => values[11] && values[11] !== "missing",
).length;
const implementedOrBetter = dataRows.filter((values) =>
  ["implemented", "tested", "approved"].includes(values[16]),
).length;
const byGrade = Object.fromEntries(
  [...new Set(dataRows.map((values) => values[1]))].sort().map((grade) => {
    const gradeRows = dataRows.filter((values) => values[1] === grade);
    const gradeApproved = gradeRows.filter(
      (values) => values[16] === "approved",
    ).length;
    return [
      grade,
      {
        total: gradeRows.length,
        approved: gradeApproved,
        approvedPercent: gradeRows.length
          ? Math.round((gradeApproved / gradeRows.length) * 10000) / 100
          : 0,
      },
    ];
  }),
);
const releaseCoveragePercent =
  total === 0 ? 0 : Math.round((approved / total) * 10000) / 100;
// This percentage is scoped to the versioned repository catalog. It is not an
// audit of every outcome in the official TT32 curriculum PDF.
const catalogRequirementCoveragePercent = releaseCoveragePercent;
const simulationMappingCoveragePercent =
  total === 0 ? 0 : Math.round((mapped / total) * 10000) / 100;
const implementedSimulationCoveragePercent =
  total === 0 ? 0 : Math.round((implementedOrBetter / total) * 10000) / 100;
const sourceLinked = dataRows.filter(
  (values) => values[8] && !values[8].toLowerCase().includes("unverified"),
).length;
const sourceFallback = dataRows.filter((values) =>
  values[8]?.includes("source:moet_tt32;"),
).length;
const sourceExact = sourceLinked - sourceFallback;
const sourceMappingPercent =
  total === 0 ? 0 : Math.round((sourceLinked / total) * 10000) / 100;
console.log(
  JSON.stringify(
    {
      totalRows: total,
      testedOrBetter,
      approved,
      releaseCoveragePercent,
      programRequirementCoveragePercent: catalogRequirementCoveragePercent,
      programRequirementScope: "internal-curriculum-catalog",
      officialProgramCoveragePercent: null,
      officialProgramCoverageStatus: "not-asserted",
      simulationMappingCoveragePercent,
      implementedSimulationCoveragePercent,
      sourceLinkedRows: sourceLinked,
      sourceExactRows: sourceExact,
      sourceFallbackRows: sourceFallback,
      sourceMappingPercent,
      catalogLessonCount: catalogLessons.size,
      catalogLessonsMapped: [...catalogLessons].filter((lesson) =>
        listedLessons.has(lesson),
      ).length,
      byGrade,
      errors,
    },
    null,
    2,
  ),
);
if (errors.length > 0) process.exitCode = 1;
