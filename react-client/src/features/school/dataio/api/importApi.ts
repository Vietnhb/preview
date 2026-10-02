import axiosClient from "../../../../shared/api/client";

export type ImportKind = "USERS" | "CLASSES" | "ENROLLMENTS" | "TEACHER_ASSIGNMENTS";
export type ImportRow = { row: number; data: Record<string, string> };
export type PreviewRow = ImportRow & { errors: string[]; warnings: string[]; matches: { id: number; email: string; fullName: string; dateOfBirth: string }[] };
export type ImportPreview = { kind: ImportKind; columns: string[]; rows: PreviewRow[]; validRows: number; invalidRows: number; canCommit: boolean; previewToken: string | null };
export type ImportCredential = { fullName: string; dateOfBirth: string; email: string; initialPassword: string; role: string; classCode: string; schoolYear: string };
export type ImportResult = { kind: ImportKind; imported: number; credentials: ImportCredential[] };

const base = (schoolId: string) => `/schools/${schoolId}/imports`;
export async function previewImportFile(schoolId: string, kind: ImportKind, file: File) {
  const data = new FormData(); data.append("file", file);
  return (await axiosClient.post<ImportPreview>(`${base(schoolId)}/preview`, data, { params: { kind } })).data;
}
export async function previewImportRows(schoolId: string, kind: ImportKind, rows: ImportRow[]) {
  return (await axiosClient.post<ImportPreview>(`${base(schoolId)}/preview`, { kind, rows })).data;
}
export async function commitImport(schoolId: string, preview: ImportPreview) {
  return (await axiosClient.post<ImportResult>(`${base(schoolId)}/commit`, { kind: preview.kind, rows: preview.rows.map(({ row, data }) => ({ row, data })), previewToken: preview.previewToken })).data;
}
export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a"); link.href = url; link.download = filename;
  document.body.appendChild(link); link.click(); link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export async function downloadImportTemplate(schoolId: string, kind: ImportKind) {
  const response = await axiosClient.get<Blob>(`${base(schoolId)}/template`, { params: { kind }, responseType: "blob" });
  downloadBlob(response.data, `mau-${kind.toLowerCase()}.csv`);
}
export { credentialsCsv } from "../model/credentialsCsv";