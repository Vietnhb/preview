import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { buildCatalog, indexLessonPaths, filterLibrary } from "../src/features/library/model/catalogModel.ts";

const curriculum = { topics: [
  { id: "physics", name: "Vật lý", enabled: true, modules: [
    { id: "mechanics", name: "Cơ học", levels: [
      { id: "mechanics12", name: "Lớp 12", lessons: [{ id: "pendulum", name: "Con lắc đơn" }] },
      { id: "mechanics10", name: "Lớp 10", lessons: [{ id: "motion", name: "Chuyển động thẳng" }] },
    ] },
    { id: "thermodynamics", name: "Nhiệt học", levels: [
      { id: "heat10", name: "Lớp 10", lessons: [{ id: "heat", name: "Nhiệt lượng" }] },
    ] },
  ] },
  { id: "hidden", name: "Môn ẩn", enabled: false, modules: [] },
] };
const catalog = buildCatalog(curriculum);
const paths = indexLessonPaths(catalog);
const items = [
  { id: "1", lessonId: "motion", title: "Vận tốc", visibility: "PUBLIC", sharedByName: "Nguyễn An", schoolName: "Trường A" },
  { id: "2", lessonId: "heat", title: "Thí nghiệm 2", visibility: "SHARED", sharedByName: "Trần Bình", schoolName: "Trường A" },
  { id: "3", lessonId: "pendulum", title: "Thí nghiệm 3", visibility: "PUBLIC", sharedByName: "Lê Chi", schoolName: "Trường B" },
];

test("catalog groups class before module and excludes disabled subjects", () => {
  assert.equal(catalog.length, 1);
  assert.deepEqual(catalog[0].grades.map(grade => grade.name), ["Lớp 10", "Lớp 12"]);
  assert.deepEqual(catalog[0].grades[0].modules.map(module => module.id), ["mechanics", "thermodynamics"]);
  assert.equal(paths.get("motion").grade, "Lớp 10");
  assert.equal(paths.get("heat").module, "Nhiệt học");
});

test("class selection includes all its modules and module selection respects selected class", () => {
  assert.deepEqual(filterLibrary(items, paths, { subjectId: "physics", grade: "Lớp 10" }, "ALL", "").map(item => item.id), ["1", "2"]);
  assert.deepEqual(filterLibrary(items, paths, { subjectId: "physics", grade: "Lớp 10", moduleId: "mechanics" }, "ALL", "").map(item => item.id), ["1"]);
  assert.deepEqual(filterLibrary(items, paths, { lessonId: "pendulum" }, "ALL", "").map(item => item.id), ["3"]);
});

test("scope filters distinguish system and school resources", () => {
  assert.deepEqual(filterLibrary(items, paths, {}, "PUBLIC", "").map(item => item.id), ["1", "3"]);
  assert.deepEqual(filterLibrary(items, paths, {}, "SHARED", "").map(item => item.id), ["2"]);
  assert.deepEqual(filterLibrary(items, paths, { grade: "Lớp 12" }, "SHARED", ""), []);
});

test("search includes curriculum paths and accepts Vietnamese without accents", () => {
  assert.deepEqual(filterLibrary(items, paths, {}, "ALL", "chuyen dong thang").map(item => item.id), ["1"]);
  assert.deepEqual(filterLibrary(items, paths, {}, "ALL", "nhiet hoc").map(item => item.id), ["2"]);
  assert.deepEqual(filterLibrary(items, paths, {}, "ALL", "nguyen an").map(item => item.id), ["1"]);
  assert.deepEqual(filterLibrary(items, paths, {}, "ALL", "lop 12").map(item => item.id), ["3"]);
});

test("resources without a catalog path remain searchable in all resources", () => {
  const unknown = [{ id: "4", lessonId: "missing", title: "Mô phỏng tự do", visibility: "PUBLIC" }];
  assert.equal(filterLibrary(unknown, new Map(), {}, "PUBLIC", "tu do").length, 1);
  assert.equal(filterLibrary(unknown, new Map(), { subjectId: "physics" }, "ALL", "").length, 0);
});

test("real backend topic strands group into Physics without changing module or lesson identities", () => {
  const bundled = JSON.parse(readFileSync(new URL("../../backend/src/main/resources/curriculum/catalog.json", import.meta.url), "utf8"));
  const apiCatalog = { topics: bundled.topics.map(topic => ({
    ...topic, id: topic.slug, enabled: true,
    modules: topic.modules.map(module => ({ ...module, id: `${topic.slug}/${module.slug}`, levels: module.levels.map(level => ({
      ...level, id: `${module.slug}/${level.name}`, lessons: level.lessons.map(lesson => ({ ...lesson, id: `${module.slug}/${level.name}/${lesson.slug}` })),
    })) })),
  })) };
  const grouped = buildCatalog(apiCatalog);
  assert.equal(grouped.length, 1);
  assert.equal(grouped[0].name, "Vật lý");
  assert.deepEqual(grouped[0].grades.map(grade => grade.name), ["Lớp 10", "Lớp 11", "Lớp 12"]);
  const realPaths = indexLessonPaths(grouped);
  const realLessons = apiCatalog.topics.flatMap(topic => topic.modules.flatMap(module => module.levels.flatMap(level => level.lessons)));
  assert.equal(realPaths.size, realLessons.length);
  for (const topic of apiCatalog.topics) for (const module of topic.modules) for (const level of module.levels) for (const lesson of level.lessons) {
    const path = realPaths.get(lesson.id);
    assert.equal(path.topic, topic.name);
    assert.equal(path.topicId, topic.id);
    assert.equal(path.grade, level.name);
    assert.equal(path.moduleId, module.id);
    assert.equal(path.lesson, lesson.name);
  }
});
