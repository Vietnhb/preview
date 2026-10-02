import axiosClient from "../../../shared/api/client";

export type SupportKind = "FEEDBACK" | "MESSAGE" | "COMPLAINT";
export type SupportStatus = "OPEN" | "READ" | "RESOLVED";
export type SupportItem = { id: string; kind: SupportKind; senderId: number; senderName: string; senderEmail: string; subject: string; content: string; status: SupportStatus; adminResponse?: string | null; createdAt: string; respondedAt?: string | null };

export const adminSupportItems = (kind: SupportKind) => axiosClient.get<SupportItem[]>(`/support/admin?kind=${kind}`).then(r => r.data);
export const updateSupportItem = (id: string, status: SupportStatus, response?: string) => axiosClient.put<SupportItem>(`/support/admin/${id}`, { status, response }).then(r => r.data);
export const submitFeedback = (subject: string, content: string) => axiosClient.post<SupportItem>("/support/feedback", { subject, content }).then(r => r.data);
export const submitMessage = (subject: string, content: string) => axiosClient.post<SupportItem>("/support/messages", { subject, content }).then(r => r.data);
/** Simulation complaints: sent by teachers, answered by reviewers. */
export type SimulationComplaint = SupportItem & { simulationId?: string | null; responderName?: string | null };
export const submitSimulationComplaint = (subject: string, content: string, simulationId?: string) =>
  axiosClient.post<SimulationComplaint>("/support/complaints", { subject, content, simulationId }).then(r => r.data);
export const mySupportItems = () => axiosClient.get<SimulationComplaint[]>("/support/mine").then(r => r.data);
export const complaintsForReview = () => axiosClient.get<SimulationComplaint[]>("/support/complaints/review").then(r => r.data);
export const resolveComplaint = (id: string, status: SupportStatus, response?: string) =>
  axiosClient.put<SimulationComplaint>(`/support/complaints/review/${id}`, { status, response }).then(r => r.data);
