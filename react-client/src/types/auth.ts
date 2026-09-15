export interface User {
    id: number;
    email: string;
    fullName: string;
    role: string;
    dateOfBirth?: string | null;
    avatarUrl?: string | null;
}
export interface LoginResponse {
    token: string;
    user: User;
}
