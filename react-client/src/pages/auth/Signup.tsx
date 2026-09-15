import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { signup } from "../api/authApi";

function Signup() {
    const navigate = useNavigate();
    const [email, setEmail] = useState("");
    const [fullName, setFullName] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");

    const handleSignup = async (event: FormEvent) => {
        event.preventDefault();
        setError("");
        try {
            await signup(email, fullName, password);
            navigate("/login");
        } catch (err: unknown) {
            if (axios.isAxiosError<{ message?: string }>(err) && err.response) {
                setError(err.response.data?.message ?? "Đăng ký thất bại");
            } else {
                setError("Không thể kết nối tới server");
            }
        }
    };

    return <main className="auth-page"><form className="auth-card" onSubmit={handleSignup}>
        <p className="auth-kicker">Bắt đầu với PhysLive</p>
        <h1>Tạo tài khoản</h1>
        <p className="muted">Thiết lập workspace để lưu đề, mô phỏng và bài giao.</p>
        <div className="field"><label htmlFor="signup-name">Họ và tên</label><input id="signup-name" type="text" autoComplete="name" placeholder="Nguyễn Văn A" value={fullName} onChange={(e) => setFullName(e.target.value)} required /></div>
        <div className="field"><label htmlFor="signup-email">Email</label><input id="signup-email" type="email" autoComplete="email" placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} required /></div>
        <div className="field"><label htmlFor="signup-password">Mật khẩu</label><input id="signup-password" type="password" autoComplete="new-password" minLength={8} placeholder="Tối thiểu 8 ký tự" value={password} onChange={(e) => setPassword(e.target.value)} required /></div>
        {error && <div className="error" role="alert">{error}</div>}
        <div className="actions"><button type="submit">Tạo tài khoản</button><button type="button" className="secondary" onClick={() => navigate("/login")}>Đã có tài khoản</button></div>
    </form></main>;
}
export default Signup;
