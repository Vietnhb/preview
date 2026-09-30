import type { ImportCredential } from "./importApi";

export function credentialsCsv(rows: ImportCredential[]) {
  const columns: (keyof ImportCredential)[] = ["fullName", "dateOfBirth", "email", "initialPassword", "role", "classCode", "schoolYear"];
  const cell = (value: string, credential: boolean) => `"${(!credential && /^[=+@\-\t\r\n]/.test(value) ? "'" + value : value).replaceAll('"', '""')}"`;
  return "\uFEFF" + [columns.join(","), ...rows.map(row => columns.map(column => cell(row[column] ?? "", column === "initialPassword")).join(","))].join("\r\n") + "\r\n";
}
