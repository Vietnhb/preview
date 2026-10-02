import axios from "axios";

export const apiMessage = (error: unknown, fallback: string) => axios.isAxiosError<{ message?: string }>(error)
  ? error.response?.data?.message ?? fallback : fallback;