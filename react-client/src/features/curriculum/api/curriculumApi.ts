import axiosClient from "../../../shared/api/client";
import type { Curriculum } from "../../../shared/types/physlive";

/**
 * API endpoints for curriculum management
 * Handles curriculum structure and educational content organization
 */

export const curriculum = () => 
  axiosClient.get<Curriculum>("/curriculum").then(r => r.data);

export const simulationCurriculum = (schemaId: string, schemaVersion: string, signal: AbortSignal) =>
  axiosClient.get<Curriculum>("/curriculum", { params: { schemaId, schemaVersion }, signal }).then(r => r.data);

export type CurriculumLesson = { id: string; name: string; slug: string; active: boolean; sortOrder: number };

export type CurriculumLevel = { id: string; name: string; active: boolean; sortOrder: number; lessons: CurriculumLesson[] };

export type CurriculumModule = { id: string; name: string; slug: string; active: boolean; sortOrder: number; levels: CurriculumLevel[] };

export type CurriculumTopic = { id: string; name: string; slug: string; enabled: boolean; sortOrder: number; modules: CurriculumModule[] };

export type CurriculumTree = { topics: CurriculumTopic[] };

export const adminCurriculum = () => axiosClient.get<CurriculumTree>("/admin/curriculum").then(r => r.data);

export const toggleCurriculum = (type: "topic" | "module" | "level" | "lesson", id: string) =>
  axiosClient.put<CurriculumTree>(`/admin/curriculum/${type}/${id}/toggle`).then(r => r.data);

export const createCurriculumNode = (path: string, payload: { name: string; slug?: string; sortOrder?: number }) =>
  axiosClient.post<CurriculumTree>(`/admin/curriculum/${path}`, payload).then(r => r.data);