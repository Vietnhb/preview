import { useNavigate } from "react-router-dom";
import { useState, type FormEvent } from "react";
import axios from "axios";
import { login } from "../api/authApi";
import { setToken } from "../utils/token";

function Login() {
    const navigate = useNavigate();
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");

    const handleLogin = async (event: FormEvent) => {
        event.preventDefault();
        setError("");
        try {
            const res = await login(email, password);
            setToken(res.token);
            navigate("/");
        } catch (err: unknown) {
            if (!axios.isAxiosError<{ message?: string }>(err) || !err.response) {
                setError("Không thể kết nối tới server");
            } else if (err.response.status === 401) {
                setError(err.response.data?.message ?? "Email hoặc mật khẩu không đúng");
            } else {
                setError(err.response.data?.message ?? "Có lỗi xảy ra từ server");
            }
        }
    };

    return <main className="auth-page"><form className="auth-card" onSubmit={handleLogin}>
        <p className="auth-kicker">PhysLive workspace</p>
        <h1>Chào mừng trở lại</h1>
        <p className="muted">Đăng nhập để tiếp tục xây dựng và phát lại mô phỏng vật lý.</p>
        <div className="field"><label htmlFor="login-email">Email</label><input id="login-email" type="email" autoComplete="email" placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} required /></div>
        <div className="field"><label htmlFor="login-password">Mật khẩu</label><input id="login-password" type="password" autoComplete="current-password" placeholder="Nhập mật khẩu" value={password} onChange={(e) => setPassword(e.target.value)} required /></div>
        {error && <div className="error" role="alert">{error}</div>}
        <div className="actions"><button type="submit">Đăng nhập</button><button type="button" className="secondary" onClick={() => navigate("/")}>Trang chủ</button></div>
        <p className="auth-footer">Chưa có tài khoản? <a href="/signup" onClick={(event) => { event.preventDefault(); navigate("/signup"); }}>Đăng ký ngay</a></p>
    </form></main>;
}
export default Login;
