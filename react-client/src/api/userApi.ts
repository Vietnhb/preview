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