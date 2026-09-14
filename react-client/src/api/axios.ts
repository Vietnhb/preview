import axios, { type InternalAxiosRequestConfig } from "axios";
import { API_URL } from "../config/api";
import { getToken } from "../utils/token";

const axiosClient = axios.create({
    baseURL: API_URL
});
axiosClient.interceptors.request.use((config) => {
    // Bound reads without shortening long-running AI/solver requests.
    if (config.method === "get" && !config.timeout) config.timeout = 15000;
    const token = getToken();
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config;
});
type ReadRetryConfig = InternalAxiosRequestConfig & { readRetryCount?: number };
axiosClient.interceptors.response.use(response => response, async (error: unknown) => {
    if (!axios.isAxiosError(error) || axios.isCancel(error)) throw error;
    const config = error.config as ReadRetryConfig | undefined;
    const status = error.response?.status;
    const transient = status === undefined || [500, 502, 503, 504].includes(status);
    if (!config || config.method !== "get" || !transient || (config.readRetryCount ?? 0) >= 2) throw error;
    const authorization = config.headers.Authorization;
    config.readRetryCount = (config.readRetryCount ?? 0) + 1;
    await new Promise(resolve => setTimeout(resolve, 500 * config.readRetryCount!));
    // Never replay a previous user's request after a login/logout.
    if (config.signal?.aborted || authorization !== (getToken() ? `Bearer ${getToken()}` : undefined)) throw error;
    return axiosClient.request(config);
});
export default axiosClient;
