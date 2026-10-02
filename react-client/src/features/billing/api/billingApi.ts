import axiosClient from "../../../shared/api/client";
import axios from "axios";
import { API_URL } from "../../../config/api";
import type { SchoolBilling, PlanQuote } from "../../school/types";

export type ManagedPlan = LicensePlan & { active: boolean };

export const adminPlans = () => axiosClient.get<ManagedPlan[]>("/admin/plans").then(r => r.data);

export const saveManagedPlan = (payload: ManagedPlan, editing: boolean) =>
  (editing ? axiosClient.put<ManagedPlan>(`/admin/plans/${payload.code}`, payload) : axiosClient.post<ManagedPlan>("/admin/plans", payload)).then(r => r.data);

export type PaymentNotification = { id: string; schoolName: string; planCode: string; amountVnd: number; paidAt: string; status: string };

export const paymentNotifications = () => axiosClient.get<PaymentNotification[]>("/admin/payment-notifications").then(r => r.data);

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

export const schoolBilling = () => axiosClient.get<SchoolBilling>("/school/billing").then(r => r.data);

export const quoteSchoolPlan = (planCode: string) => axiosClient.post<PlanQuote>("/school/billing/quote", { planCode }).then(r => r.data);

export const purchaseSchoolPlan = (planCode: string, expectedAmountVnd: number) => axiosClient.post<CheckoutResult>("/school/billing/checkout", { planCode, expectedAmountVnd }).then(r => r.data);