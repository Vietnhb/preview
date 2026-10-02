export const isTokenExpired = (token: string): boolean => {
    try {
        const payload = JSON.parse(atob(token.split(".")[1]));
        const now = Date.now() / 1000;
        return typeof payload.exp === "number" && payload.exp <= now;
    } catch {
        return true;
    }
}