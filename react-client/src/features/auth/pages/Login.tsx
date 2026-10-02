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
import { useEffectiveTheme } from "../../../shared/theme/themeStore";
import "../styles/account.css";

const AURORA_DARK: [string, string, string] = ["#1d4ed8", "#22d3ee", "#8b5cf6"];
const AURORA_LIGHT: [string, string, string] = ["#3b82f6", "#06b6d4", "#8b5cf6"];

export default function Login() {
  const navigate = useNavigate();
  const theme = useEffectiveTheme();
  const setUser = useSessionStore((state) => state.setUser);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [capsLock, setCapsLock] = useState(false);
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
    <Aurora key={theme} colors={theme === "dark" ? AURORA_DARK : AURORA_LIGHT} intensity={0.85} />
    <DotField gap={26} radius={130} />
    <div className="account-auth-inner">
      <aside className="account-auth-aside" aria-label="Giới thiệu PhysLive">
        <h2><SplitWords text="Thay một thông số," /> <SplitWords text="thấy ngay kết quả." wordClassName="text-shine" delay={0.25} /></h2>
        <TrajectoryFigure />
      </aside>
    <section className="account-auth-form">
      <div className="account-heading">
        <h1>Chào mừng trở lại</h1>
        <p>Đăng nhập bằng tài khoản do nhà trường hoặc PhysLive cấp.</p>
      </div>
      <form onSubmit={handleLogin} className="account-form">
        <label>
          <span>Email</span>
          <span className="account-field">
            <svg className="account-field__icon" viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2.5" /><path d="m4 7.5 8 6 8-6" /></svg>
            <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="ten@truong.edu.vn" autoComplete="email" autoFocus required />
          </span>
        </label>
        <label>
          <span>Mật khẩu</span>
          <span className="account-field account-password">
            <svg className="account-field__icon" viewBox="0 0 24 24" aria-hidden="true"><rect x="4.5" y="10.5" width="15" height="10" rx="2.5" /><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3" /></svg>
            <input type={showPassword ? "text" : "password"} value={password} onChange={(event) => setPassword(event.target.value)}
              onKeyUp={(event) => setCapsLock(event.getModifierState("CapsLock"))} onBlur={() => setCapsLock(false)} autoComplete="current-password" required />
            <button type="button" className="account-password-toggle" onClick={() => setShowPassword((value) => !value)} aria-pressed={showPassword}
              aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"} title={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}>
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2.5 12s3.5-6.5 9.5-6.5S21.5 12 21.5 12s-3.5 6.5-9.5 6.5S2.5 12 2.5 12Z" /><circle cx="12" cy="12" r="2.8" />{showPassword && <path d="m4 4 16 16" />}</svg>
            </button>
          </span>
          {capsLock && <span className="account-caps" role="status">Caps Lock đang bật</span>}
        </label>
        {error && <div className="account-error" role="alert"><LearningIcon name="close" /><span>{error}</span></div>}
        <button type="submit" className="account-submit" disabled={loading}>
          {loading ? <span className="account-spinner" aria-label="Đang đăng nhập" /> : <>Đăng nhập<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6" /></svg></>}
        </button>
      </form>
      <p className="account-hint">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 11v5.5M12 7.8v.2" /></svg>
        <span>Quên mật khẩu? Liên hệ quản lý trường để được đặt lại. Giáo viên và học sinh nhận tài khoản từ quản lý trường.</span>
      </p>
      <div className="account-links">
        <span>Trường chưa dùng PhysLive?</span>
        <Link to="/signup">Đăng ký cho trường</Link>
      </div>
    </section>
    </div>
  </main>;
}
