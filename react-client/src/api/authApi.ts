import axios from "axios";
import { API_URL } from "../config/api";
import type { LoginResponse, User } from "../types/auth";

export const login = async (
    email: string,
    password: string
): Promise<LoginResponse> => {
    const res = await axios.post<LoginResponse>(`${API_URL}/auth/login`, { email, password })
    return res.data;
}

export const signup = async (
    email: string,
    fullName: string,
    password: string
): Promise<User> => {
    const res = await axios.post<User>(`${API_URL}/auth/signup`, { email, fullName, password })
    return res.data;
}