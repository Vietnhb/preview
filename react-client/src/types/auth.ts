export interface User {
    id: number;
    email: string;
    fullName: string;
    role: string;
}
export interface LoginResponse {
    token: string;
    user: User;
}