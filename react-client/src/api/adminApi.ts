import axiosClient from "./axios";
import type { User } from "../types/physlive";

/**
 * API endpoints for admin operations
 * Handles user management, validation metrics, and system administration
 */

export const adminUsers = () => 
  axiosClient.get<User[]>("/admin/users").then(r => r.data);

export const validationMetrics = () => 
  axiosClient.get<{ 
    total: number; 
    failed: number; 
    failureRate: number 
  }>("/admin/metrics/validation").then(r => r.data);
