import { API_URL } from "../config/api";
import type { User } from "../types/user";
import axiosClient from "./axios";

export const getMe = async (): Promise<User> => {
    const res = await axiosClient.get<User>(`${API_URL}/user/me`);
    return res.data;
};
export const getUsers = async (): Promise<User[]> => {
    const res = await axiosClient.get<User[]>(`${API_URL}/user/all`);
    return res.data;
}

export const updateProfile = async (payload: { fullName: string; dateOfBirth?: string }) => {
    const res = await axiosClient.put<User>(`${API_URL}/user/me/profile`, payload);
    return res.data;
};

export const updateAvatar = async (avatarUrl: string) => {
    const res = await axiosClient.put<User>(`${API_URL}/user/me/avatar`, { avatarUrl });
    return res.data;
};

export const changePassword = async (currentPassword: string, newPassword: string) => {
    await axiosClient.put(`${API_URL}/user/me/password`, { currentPassword, newPassword });
};
