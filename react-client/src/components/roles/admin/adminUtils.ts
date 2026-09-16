import axios from "axios";
import type { CurriculumTree } from "../../../api/adminApi";
export type CurriculumKind = "topic" | "module" | "level" | "lesson";

export const roleLabels: Record<string, string> = { ADMIN: "Quáº£n trá»‹ viÃªn", TEACHER: "GiÃ¡o viÃªn", STUDENT: "Há»c sinh", REVIEWER: "Reviewer" };

export const apiMessage = (error: unknown, fallback: string) => axios.isAxiosError<{ message?: string }>(error)
  ? error.response?.data?.message ?? fallback : fallback;

export function curriculumPath(kind: CurriculumKind, parentId: string) {
  if (kind === "topic") return "topics";
  if (kind === "module") return `topics/${parentId}/modules`;
  if (kind === "level") return `modules/${parentId}/levels`;
  return `levels/${parentId}/lessons`;
}

export function curriculumParentOptions(kind: CurriculumKind, tree: CurriculumTree | null) {
  if (kind === "module") return (tree?.topics ?? []).map(item => ({ id: item.id, label: `Chá»§ Ä‘á»: ${item.name}` }));
  if (kind === "level") return (tree?.topics.flatMap(t => t.modules) ?? []).map(item => ({ id: item.id, label: `Module: ${item.name}` }));
  if (kind === "lesson") return (tree?.topics.flatMap(t => t.modules.flatMap(m => m.levels)) ?? []).map(item => ({ id: item.id, label: `Level: ${item.name}` }));
  return [];
}

