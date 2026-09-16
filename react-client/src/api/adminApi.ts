import axiosClient from "./axios";
import type { User } from "../types/physlive";

/**
 * API endpoints for admin operations
 * Handles user management, validation metrics, and system administration
 */

export const adminUsers = () => 
  axiosClient.get<User[]>("/admin/users").then(r => r.data);

export const validationMetrics = () => 
  axiosClient.get<{ 
    total: number; 
    failed: number; 
    failureRate: number 
  }>("/admin/metrics/validation").then(r => r.data);

export type ManagedSchool = { id: string; code: string; name: string; address?: string | null; active: boolean };
export type SchoolRequest = { code: string; name: string; address?: string; active: boolean };
export type ValidationRun = {
  id: string; submissionId: string; topic: string; schemaId: string; schemaVersion: string;
  solverVersion: string; passed: boolean; status: string; errorMessage?: string | null; createdAt: string;
};
export type CurriculumLesson = { id: string; name: string; slug: string; active: boolean; sortOrder: number };
export type CurriculumLevel = { id: string; name: string; active: boolean; sortOrder: number; lessons: CurriculumLesson[] };
export type CurriculumModule = { id: string; name: string; slug: string; active: boolean; sortOrder: number; levels: CurriculumLevel[] };
export type CurriculumTopic = { id: string; name: string; slug: string; enabled: boolean; sortOrder: number; modules: CurriculumModule[] };
export type CurriculumTree = { topics: CurriculumTopic[] };

export const createManagedUser = (payload: { email: string; password: string; fullName: string; role: string; institutionId?: string }) =>
  axiosClient.post<User>("/admin/users", payload).then(r => r.data);
export const updateManagedUser = (id: number, payload: { fullName: string; role: string; institutionId?: string }) =>
  axiosClient.put<User>(`/admin/users/${id}`, payload).then(r => r.data);
export const setManagedUserActive = (id: number, active: boolean) =>
  axiosClient.put<User>(`/admin/users/${id}/${active ? "restore" : "suspend"}`).then(r => r.data);
export const adminSchools = () => axiosClient.get<ManagedSchool[]>("/admin/schools").then(r => r.data);
export const createSchool = (payload: SchoolRequest) => axiosClient.post<ManagedSchool>("/admin/schools", payload).then(r => r.data);
export const updateSchool = (id: string, payload: SchoolRequest) => axiosClient.put<ManagedSchool>(`/admin/schools/${id}`, payload).then(r => r.data);
export const adminCurriculum = () => axiosClient.get<CurriculumTree>("/admin/curriculum").then(r => r.data);
export const toggleCurriculum = (type: "topic" | "module" | "level" | "lesson", id: string) =>
  axiosClient.put<CurriculumTree>(`/admin/curriculum/${type}/${id}/toggle`).then(r => r.data);
export const createCurriculumNode = (path: string, payload: { name: string; slug?: string; sortOrder?: number }) =>
  axiosClient.post<CurriculumTree>(`/admin/curriculum/${path}`, payload).then(r => r.data);
export const validationRuns = () => axiosClient.get<ValidationRun[]>("/admin/validation-runs").then(r => r.data);
