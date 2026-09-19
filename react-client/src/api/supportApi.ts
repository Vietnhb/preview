import axiosClient from "./axios";

export type SupportKind = "FEEDBACK" | "MESSAGE";
export type SupportStatus = "OPEN" | "READ" | "RESOLVED";
export type SupportItem = { id: string; kind: SupportKind; senderId: number; senderName: string; senderEmail: string; subject: string; content: string; status: SupportStatus; adminResponse?: string | null; createdAt: string; respondedAt?: string | null };

export const adminSupportItems = (kind: SupportKind) => axiosClient.get<SupportItem[]>(`/support/admin?kind=${kind}`).then(r => r.data);
export const updateSupportItem = (id: string, status: SupportStatus, response?: string) => axiosClient.put<SupportItem>(`/support/admin/${id}`, { status, response }).then(r => r.data);
export const submitFeedback = (subject: string, content: string) => axiosClient.post<SupportItem>("/support/feedback", { subject, content }).then(r => r.data);
export const submitMessage = (subject: string, content: string) => axiosClient.post<SupportItem>("/support/messages", { subject, content }).then(r => r.data);
