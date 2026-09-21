import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import axios from "axios";
import { login } from "../../api/authApi";
import { setToken } from "../../utils/token";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import LearningIcon from "../../components/common/LearningIcon";
import { DotPatternBackground } from "../../components/effects/DotPatternBackground";
import { ROLE_NAMES } from "../../types/roles";
import "../../styles/account.css";

export default function Login() {
  const navigate = useNavigate();
  const setUser = usePhysliveStore((state) => state.setUser);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleLogin = async (event: FormEvent) => {
    event.preventDefault(); setError(""); setLoading(true);
    try {
      const response = await login(email, password);
      setToken(response.token);
      setUser(response.user);
      if (response.user.role === ROLE_NAMES.SCHOOL_MANAGER) {
        navigate(response.user.billingRequired ? "/school/billing" : "/school", { replace: true });
      } else navigate("/", { replace: true });
    }
    catch (err: unknown) {
      if (axios.isAxiosError<{ message?: string }>(err) && err.response) setError(err.response.data?.message ?? (err.response.status === 401 ? "Email hoặc mật khẩu không đúng." : "Không thể đăng nhập."));
      else setError("Không thể kết nối tới máy chủ.");
    } finally { setLoading(false); }
  };

  return <div className="account-auth-page">
    <div className="account-auth-visual" aria-hidden="true"><DotPatternBackground /></div>
    <div className="account-auth-form-wrap"><div className="account-auth-form">
      <div className="account-heading"><h1>Chào mừng trở lại</h1><p>Vui lòng đăng nhập để tiếp tục.</p></div>
      <form onSubmit={handleLogin} className="account-form">
        <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="Email" autoComplete="email" required />
        <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Mật khẩu" autoComplete="current-password" required />
        {error && <div className="account-error"><LearningIcon name="close" /><span>{error}</span></div>}
        <button type="submit" disabled={loading}>{loading ? <span className="account-spinner" /> : "Tiếp tục"}</button>
      </form>
      <div className="account-links"><Link to="/signup">Đăng ký PhysLive cho nhà trường</Link><Link to="/">Về trang chủ</Link></div>
      <p className="account-legal">Bằng việc tiếp tục, bạn đồng ý với <a href="#terms">Điều khoản dịch vụ</a> và <a href="#privacy">Chính sách bảo mật</a>.</p>
    </div></div>
  </div>;
}
