import axios from "axios";
import { API_URL } from "../config/api";
import type { LoginResponse, User } from "../types/auth";

export type LicensePlan = {
    code: string; name: string; description: string; annualPriceVnd: number;
    studentQuota: number; monthlyTokenQuota: number | null;
};
export const getRegistrationPlans = () =>
    axios.get<LicensePlan[]>(`${API_URL}/auth/plans`).then(response => response.data);
export type SchoolCheckout = { planCode: string; schoolName: string; schoolCode: string; address: string; fullName: string; email: string; phoneNumber: string; password: string };
export type CheckoutResult = { paymentId: string; paymentUrl: string | null };
export const createSchoolCheckout = (payload: SchoolCheckout) =>
    axios.post<CheckoutResult>(`${API_URL}/auth/school-checkout`, payload).then(response => response.data);
export const recoverSchoolCheckout = (email: string, password: string) =>
    axios.post<CheckoutResult>(`${API_URL}/auth/school-checkout/recover`, { email, password }).then(response => response.data);
export const getSchoolPaymentStatus = (id: string) =>
    axios.get<{ status: "PENDING" | "PAID" | "FAILED" | "EXPIRED" | "REQUIRES_REVIEW" }>(`${API_URL}/auth/payments/${id}`).then(response => response.data);

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
