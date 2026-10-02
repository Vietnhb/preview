import axiosClient from "../../../shared/api/client";
import type { SchoolClassDetail, SchoolClassRequest, SchoolClassSummary } from "../types";

const classesPath = (schoolId: string) => `/schools/${schoolId}/classes`;

export const schoolClasses = (schoolId: string) => axiosClient.get<SchoolClassSummary[]>(classesPath(schoolId)).then(r => r.data);

export const schoolClass = (schoolId: string, classId: string) => axiosClient.get<SchoolClassDetail>(`${classesPath(schoolId)}/${classId}`).then(r => r.data);

export const createSchoolClass = (schoolId: string, payload: SchoolClassRequest) => axiosClient.post<SchoolClassDetail>(classesPath(schoolId), payload).then(r => r.data);

export const updateSchoolClass = (schoolId: string, classId: string, payload: SchoolClassRequest) => axiosClient.put<SchoolClassDetail>(`${classesPath(schoolId)}/${classId}`, payload).then(r => r.data);

export const archiveSchoolClass = (schoolId: string, classId: string) => axiosClient.delete<void>(`${classesPath(schoolId)}/${classId}`).then(r => r.data);

export const assignClassTeacher = (schoolId: string, classId: string, teacherId: number) => axiosClient.post(`${classesPath(schoolId)}/${classId}/teachers`, { teacherId }).then(r => r.data);

export const removeClassTeacher = (schoolId: string, classId: string, teacherId: number) => axiosClient.delete(`${classesPath(schoolId)}/${classId}/teachers/${teacherId}`).then(r => r.data);

export const enrollClassStudent = (schoolId: string, classId: string, studentId: number) => axiosClient.post(`${classesPath(schoolId)}/${classId}/students`, { studentId }).then(r => r.data);

export const transferClassStudent = (schoolId: string, classId: string, studentId: number) => axiosClient.put(`${classesPath(schoolId)}/${classId}/students/${studentId}/transfer`).then(r => r.data);

export const removeClassStudent = (schoolId: string, classId: string, studentId: number) => axiosClient.delete(`${classesPath(schoolId)}/${classId}/students/${studentId}`).then(r => r.data);

export type StudentClassSummary = { id: string; name: string; gradeLevel: number; schoolYear: string; subject?: string | null; schoolId: string; schoolName: string; teachers: { id: number; fullName: string }[]; classmateCount: number };

export const studentClasses = () => axiosClient.get<StudentClassSummary[]>("/student/classes").then(r => r.data);

export type SchoolReportSummary = { schoolId: string; schoolName: string; students: number; teachers: number; managers: number; activeClasses: number; enrolledStudents: number; usedTokens: number; tokenQuota: number | null; licenseEnd: string | null };

export type SchoolReportClass = { id: string; name: string; gradeLevel: number; schoolYear: string; teachers: number; students: number };

export type SchoolTokenAudit = { userEmail: string; tokens: number; operation: string; usageMonth: string; recordedAt: string };

const reportPath = (schoolId: string) => `/schools/${schoolId}/reports`;

export const schoolReportSummary = (schoolId: string) => axiosClient.get<SchoolReportSummary>(`${reportPath(schoolId)}/summary`).then(r => r.data);

export const schoolReportClasses = (schoolId: string) => axiosClient.get<SchoolReportClass[]>(`${reportPath(schoolId)}/classes`).then(r => r.data);

export const schoolReportTokenAudit = (schoolId: string) => axiosClient.get<SchoolTokenAudit[]>(`${reportPath(schoolId)}/token-audit`).then(r => r.data);

export const downloadSchoolClassesCsv = async (schoolId: string) => {
  const response = await axiosClient.get<Blob>(`${reportPath(schoolId)}/classes.csv`, { responseType: "blob" });
  const url = URL.createObjectURL(response.data);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = "danh-sach-lop.csv";
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
};

export type ManagedSchool = { id: string; code: string; name: string; address?: string | null; active: boolean; licenseStart?: string | null; licenseEnd?: string | null; monthlyTokenQuota?: number | null };

export type SchoolRequest = { code: string; name: string; address?: string; active: boolean; licenseStart?: string | null; licenseEnd?: string | null; monthlyTokenQuota?: number | null };

export const adminSchools = () => axiosClient.get<ManagedSchool[]>("/admin/schools").then(r => r.data);

export const createSchool = (payload: SchoolRequest) => axiosClient.post<ManagedSchool>("/admin/schools", payload).then(r => r.data);

export const updateSchool = (id: string, payload: SchoolRequest) => axiosClient.put<ManagedSchool>(`/admin/schools/${id}`, payload).then(r => r.data);