import type { Curriculum, LibraryItem } from "../../../shared/types/physlive";

export type CatalogLesson = { id: string; name: string };
export type CatalogModule = { id: string; name: string; topicId: string; topic: string; lessons: CatalogLesson[] };
export type CatalogGrade = { name: string; modules: CatalogModule[] };
export type CatalogSubject = { id: string; name: string; grades: CatalogGrade[] };
export type CatalogSelection = { subjectId?: string; grade?: string; moduleId?: string; lessonId?: string };
export type LessonPath = { subjectId: string; subject: string; grade: string; topicId: string; topic: string; moduleId: string; module: string; lesson: string };
export type LibraryScope = "ALL" | "PUBLIC" | "SHARED";

export function buildCatalog(curriculum: Curriculum | null): CatalogSubject[] {
  const topics = (curriculum?.topics ?? []).filter(topic => topic.enabled);
  if (!topics.length) return [];
  const grades = new Map<string, CatalogModule[]>();
  // API topics are physics strands (KINEMATICS, DYNAMICS…), not school subjects.
  // PhysLive currently teaches Physics only. Keep the real topic identity on each
  // module while presenting the requested subject → class → module → lesson tree.
  for (const topic of topics) {
    for (const module of topic.modules) for (const level of module.levels) {
      const modules = grades.get(level.name) ?? [];
      const existing = modules.find(entry => entry.id === module.id);
      if (existing) {
        const ids = new Set(existing.lessons.map(lesson => lesson.id));
        existing.lessons.push(...level.lessons.filter(lesson => !ids.has(lesson.id)));
      } else modules.push({ id: module.id, name: module.name, topicId: topic.id, topic: topic.name, lessons: [...level.lessons] });
      grades.set(level.name, modules);
    }
  }
  return [{ id: "physics", name: "Vật lý", grades: [...grades].sort(([a], [b]) => a.localeCompare(b, "vi", { numeric: true })).map(([name, modules]) => ({ name, modules })) }];
}

export function indexLessonPaths(catalog: CatalogSubject[]): Map<string, LessonPath> {
  const paths = new Map<string, LessonPath>();
  for (const subject of catalog) for (const grade of subject.grades) for (const module of grade.modules) for (const lesson of module.lessons) {
    paths.set(lesson.id, { subjectId: subject.id, subject: subject.name, grade: grade.name, topicId: module.topicId, topic: module.topic, moduleId: module.id, module: module.name, lesson: lesson.name });
  }
  return paths;
}

export function filterLibrary(items: LibraryItem[], paths: Map<string, LessonPath>, selection: CatalogSelection, scope: LibraryScope, query: string): LibraryItem[] {
  const search = normalizeSearch(query.trim());
  return items.filter(item => {
    if (scope !== "ALL" && item.visibility !== scope) return false;
    const path = paths.get(item.lessonId);
    if (selection.subjectId && path?.subjectId !== selection.subjectId) return false;
    if (selection.grade && path?.grade !== selection.grade) return false;
    if (selection.moduleId && path?.moduleId !== selection.moduleId) return false;
    if (selection.lessonId && item.lessonId !== selection.lessonId) return false;
    const text = [item.title, item.topic, item.sharedByName, item.schoolName, path?.subject, path?.grade, path?.topic, path?.module, path?.lesson].filter(Boolean).join(" ");
    return !search || normalizeSearch(text).includes(search);
  });
}

function normalizeSearch(value: string): string {
  return value.toLocaleLowerCase("vi").normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/đ/g, "d");
}