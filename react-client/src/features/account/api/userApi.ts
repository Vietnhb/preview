import type { User } from "../../../shared/auth/types";
import type { PermissionCode } from "../../../shared/auth/permissions";
import axiosClient from "../../../shared/api/client";

export const getMe = async (): Promise<User> => {
    const res = await axiosClient.get<User>("/user/me");
    return res.data;
};
export const updateProfile = async (payload: { fullName: string; dateOfBirth?: string }) => {
    const res = await axiosClient.put<User>("/user/me/profile", payload);
    return res.data;
};

export const updateAvatar = async (avatarUrl: string) => {
    const res = await axiosClient.put<User>("/user/me/avatar", { avatarUrl });
    return res.data;
};

export const changePassword = async (currentPassword: string, newPassword: string) => {
    const res = await axiosClient.put<User>("/user/me/password", { currentPassword, newPassword });
    return res.data;
};

export type LicenseStatus = {
    active: boolean;
    inGraceMode: boolean;
    showRenewalBanner: boolean;
    daysUntilExpiry: number;
    canPerformWriteOperations: boolean;
    licenseEnd: string | null;
};
export const getLicenseStatus = () => axiosClient.get<LicenseStatus>("/user/me/license").then(r => r.data);

const usersPath = (schoolId?: string) => schoolId ? `/schools/${schoolId}/users` : "/admin/users";

export const adminUsers = (schoolId?: string) =>
  axiosClient.get<User[]>(usersPath(schoolId)).then(r => r.data);

export type ManagedUserDetails = { fullName: string; role: string; institutionId?: string; dateOfBirth?: string | null; avatarUrl?: string | null; permissions?: PermissionCode[] };

export const createManagedUser = (payload: ManagedUserDetails & { email: string; password: string }, schoolId?: string) =>
  axiosClient.post<User>(usersPath(schoolId), payload).then(r => r.data);

export const updateManagedUser = (id: number, payload: ManagedUserDetails, schoolId?: string) =>
  axiosClient.put<User>(`${usersPath(schoolId)}/${id}`, payload).then(r => r.data);

export const resetManagedUserPassword = (id: number, newPassword: string, schoolId?: string) =>
  axiosClient.post<User>(`${usersPath(schoolId)}/${id}/reset-password`, { newPassword }).then(r => r.data);

export const setManagedUserActive = (id: number, active: boolean, schoolId?: string) =>
  axiosClient.put<User>(schoolId ? `${usersPath(schoolId)}/${id}/${active ? "restore" : "suspend"}` : `/admin/users/${id}/${active ? "restore" : "suspend"}`).then(r => r.data);