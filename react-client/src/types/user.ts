export interface User {
    id: number;
    email: string;
    fullName: string;
    role: string;
    dateOfBirth?: string | null;
    avatarUrl?: string | null;
}
