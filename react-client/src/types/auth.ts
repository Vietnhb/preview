import type { RoleType } from './roles';

export interface User {
    id: number;
    email: string;
    fullName: string;
    role: RoleType;
    schoolId?: string | null; // UUID for school users, null for platform users
    schoolName?: string | null;
    dateOfBirth?: string | null;
    avatarUrl?: string | null;
    billingRequired?: boolean;
    isActive?: boolean;
    lastLogin?: string | null;
}

export interface LoginResponse {
    token: string;
    user: User;
}
