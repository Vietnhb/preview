import axiosClient from "./axios";
import type { User } from "../types/physlive";
import type { LicensePlan } from "./authApi";

export type ManagedPlan = LicensePlan & { active: boolean };
export const adminPlans = () => axiosClient.get<ManagedPlan[]>("/admin/plans").then(r => r.data);
export const saveManagedPlan = (payload: ManagedPlan, editing: boolean) =>
  (editing ? axiosClient.put<ManagedPlan>(`/admin/plans/${payload.code}`, payload) : axiosClient.post<ManagedPlan>("/admin/plans", payload)).then(r => r.data);

export type PaymentNotification = { id: string; schoolName: string; planCode: string; amountVnd: number; paidAt: string; status: string };
export const paymentNotifications = () => axiosClient.get<PaymentNotification[]>("/admin/payment-notifications").then(r => r.data);

/**
 * API endpoints for admin operations
 * Handles user management, validation metrics, and system administration
 */

const usersPath = (schoolId?: string) => schoolId ? `/schools/${schoolId}/users` : "/admin/users";

export const adminUsers = (schoolId?: string) =>
  axiosClient.get<User[]>(usersPath(schoolId)).then(r => r.data);

export const validationMetrics = () =>
  axiosClient.get<{
    total: number;
    failed: number;
    failureRate: number
  }>("/admin/metrics/validation").then(r => r.data);

export type ManagedSchool = { id: string; code: string; name: string; address?: string | null; active: boolean; licenseStart?: string | null; licenseEnd?: string | null; monthlyTokenQuota?: number | null };
export type SchoolRequest = { code: string; name: string; address?: string; active: boolean; licenseStart?: string | null; licenseEnd?: string | null; monthlyTokenQuota?: number | null };
export type ValidationRun = {
  id: string; submissionId: string; topic: string; schemaId: string; schemaVersion: string;
  solverVersion: string; passed: boolean; status: string; errorMessage?: string | null; createdAt: string;
};
export type CurriculumLesson = { id: string; name: string; slug: string; active: boolean; sortOrder: number };
export type CurriculumLevel = { id: string; name: string; active: boolean; sortOrder: number; lessons: CurriculumLesson[] };
export type CurriculumModule = { id: string; name: string; slug: string; active: boolean; sortOrder: number; levels: CurriculumLevel[] };
export type CurriculumTopic = { id: string; name: string; slug: string; enabled: boolean; sortOrder: number; modules: CurriculumModule[] };
export type CurriculumTree = { topics: CurriculumTopic[] };

export const createManagedUser = (payload: { email: string; password: string; fullName: string; role: string; institutionId?: string }, schoolId?: string) =>
  axiosClient.post<User>(usersPath(schoolId), payload).then(r => r.data);
export const updateManagedUser = (id: number, payload: { fullName: string; role: string; institutionId?: string }, schoolId?: string) =>
  axiosClient.put<User>(`${usersPath(schoolId)}/${id}`, payload).then(r => r.data);
export const setManagedUserActive = (id: number, active: boolean, schoolId?: string) =>
  axiosClient.put<User>(schoolId ? `${usersPath(schoolId)}/${id}/${active ? "restore" : "suspend"}` : `/admin/users/${id}/${active ? "restore" : "suspend"}`).then(r => r.data);
export const adminSchools = () => axiosClient.get<ManagedSchool[]>("/admin/schools").then(r => r.data);
export const createSchool = (payload: SchoolRequest) => axiosClient.post<ManagedSchool>("/admin/schools", payload).then(r => r.data);
export const updateSchool = (id: string, payload: SchoolRequest) => axiosClient.put<ManagedSchool>(`/admin/schools/${id}`, payload).then(r => r.data);
export const adminCurriculum = () => axiosClient.get<CurriculumTree>("/admin/curriculum").then(r => r.data);
export const toggleCurriculum = (type: "topic" | "module" | "level" | "lesson", id: string) =>
  axiosClient.put<CurriculumTree>(`/admin/curriculum/${type}/${id}/toggle`).then(r => r.data);
export const createCurriculumNode = (path: string, payload: { name: string; slug?: string; sortOrder?: number }) =>
  axiosClient.post<CurriculumTree>(`/admin/curriculum/${path}`, payload).then(r => r.data);
export const validationRuns = () => axiosClient.get<ValidationRun[]>("/admin/validation-runs").then(r => r.data);
