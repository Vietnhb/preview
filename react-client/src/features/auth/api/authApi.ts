import axios from "axios";
import { API_URL } from "../../../config/api";
import type { LoginResponse } from "../../../shared/auth/types";
import axiosClient from "../../../shared/api/client";

export const logout = () => axiosClient.post<void>("/auth/logout");

export const login = async (
    email: string,
    password: string
): Promise<LoginResponse> => {
    const res = await axios.post<LoginResponse>(`${API_URL}/auth/login`, { email, password })
    return res.data;
}