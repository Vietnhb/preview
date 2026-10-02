import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import axios from "axios";
import { login } from "../api/authApi";
import { setToken } from "../../../shared/lib/token";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import LearningIcon from "../../../shared/ui/LearningIcon";
import { userHome } from "../../../shared/auth/permissions";
import TrajectoryFigure from "../components/TrajectoryFigure";
import DotField from "../../../shared/effects/DotField";
import Aurora from "../../../shared/effects/Aurora";
import { SplitWords } from "../../../shared/effects/Motion";
import "../styles/account.css";

export default function Login() {
  const navigate = useNavigate();
  const setUser = useSessionStore((state) => state.setUser);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleLogin = async (event: FormEvent) => {
    event.preventDefault(); setError(""); setLoading(true);
    try {
      const response = await login(email, password);
      setToken(response.token);
      setUser(response.user);
      navigate(response.user.mustChangePassword ? "/change-password" : userHome(response.user), { replace: true });
    }
    catch (err: unknown) {
      if (axios.isAxiosError<{ message?: string }>(err) && err.response) setError(err.response.data?.message ?? (err.response.status === 401 ? "Email hoặc mật khẩu không đúng." : "Không thể đăng nhập."));
      else setError("Không thể kết nối tới máy chủ.");
    } finally { setLoading(false); }
  };

  return <main className="account-auth-page">
    <section className="account-auth-form">
      <div className="account-heading">
        <h1>Đăng nhập</h1>
        <p>Dùng tài khoản do nhà trường hoặc PhysLive cấp.</p>
      </div>
      <form onSubmit={handleLogin} className="account-form">
        <label>
          <span>Email</span>
          <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="ten@truong.edu.vn" autoComplete="email" required />
        </label>
        <label>
          <span>Mật khẩu</span>
          <span className="account-password">
            <input type={showPassword ? "text" : "password"} value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required />
            <button type="button" className="account-password-toggle" onClick={() => setShowPassword((value) => !value)} aria-pressed={showPassword}>
              {showPassword ? "Ẩn" : "Hiện"}
            </button>
          </span>
        </label>
        {error && <div className="account-error" role="alert"><LearningIcon name="close" /><span>{error}</span></div>}
        <button type="submit" className="account-submit" disabled={loading}>{loading ? <span className="account-spinner" aria-label="Đang đăng nhập" /> : "Đăng nhập"}</button>
      </form>
      <p className="account-hint">
        Giáo viên và học sinh nhận tài khoản từ quản lý trường. Quên mật khẩu? Liên hệ quản lý trường để đặt lại.
      </p>
      <div className="account-links">
        <span>Trường chưa dùng PhysLive?</span>
        <Link to="/signup">Đăng ký cho trường</Link>
      </div>
    </section>
    <aside className="account-auth-aside" aria-label="Giới thiệu PhysLive">
      <Aurora intensity={0.85} />
      <DotField gap={22} radius={110} />
      <p className="account-aside-label">Thí nghiệm trên trình duyệt</p>
      <h2><SplitWords text="Thay một thông số," /> <SplitWords text="thấy ngay kết quả." wordClassName="text-shine" delay={0.25} /></h2>
      <TrajectoryFigure />
    </aside>
  </main>;
}
