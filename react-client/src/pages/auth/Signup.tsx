import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import axios from "axios";
import { signup } from "../../api/authApi";
import LearningIcon from "../../components/common/LearningIcon";
import { DotPatternBackground } from "../../components/effects/DotPatternBackground";
import "../../styles/account.css";

export default function Signup() {
  const navigate = useNavigate();
  const [fullName, setFullName] = useState(""); const [email, setEmail] = useState("");
  const [password, setPassword] = useState(""); const [confirmPassword, setConfirmPassword] = useState("");
  const [loading, setLoading] = useState(false); const [error, setError] = useState(""); const [success, setSuccess] = useState(false);

  const handleSignup = async (event: FormEvent) => {
    event.preventDefault(); setError("");
    if (password !== confirmPassword) { setError("Mật khẩu xác nhận không khớp."); return; }
    if (password.length < 8) { setError("Mật khẩu phải có ít nhất 8 ký tự."); return; }
    setLoading(true);
    try { await signup(email, fullName, password); setSuccess(true); }
    catch (err: unknown) { setError(axios.isAxiosError<{ message?: string }>(err) ? err.response?.data?.message ?? "Đăng ký thất bại." : "Không thể kết nối tới máy chủ."); }
    finally { setLoading(false); }
  };

  if (success) return <div className="account-success-page"><div className="account-success-card"><div className="account-success-icon"><LearningIcon name="check" /></div><h1>Đăng ký thành công!</h1><p>Tài khoản đã được tạo. Bạn có thể đăng nhập để bắt đầu sử dụng PhysLive.</p><button type="button" onClick={() => navigate("/login")}>Quay lại đăng nhập</button></div></div>;

  return <div className="account-auth-page">
    <div className="account-auth-visual" aria-hidden="true"><DotPatternBackground /></div>
    <div className="account-auth-form-wrap"><div className="account-auth-form">
      <div className="account-heading"><h1>Tạo tài khoản</h1><p>Nhập thông tin để bắt đầu.</p></div>
      <form onSubmit={handleSignup} className="account-form">
        <input type="text" value={fullName} onChange={(event) => setFullName(event.target.value)} placeholder="Họ và tên" autoComplete="name" required />
        <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="Email" autoComplete="email" required />
        <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Mật khẩu (tối thiểu 8 ký tự)" autoComplete="new-password" required />
        <input type="password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} placeholder="Xác nhận mật khẩu" autoComplete="new-password" required />
        {error && <div className="account-error"><LearningIcon name="close" /><span>{error}</span></div>}
        <button type="submit" disabled={loading}>{loading ? <span className="account-spinner" /> : "Tiếp tục"}</button>
      </form>
      <div className="account-links"><Link to="/login">Đã có tài khoản? Đăng nhập</Link></div>
      <p className="account-legal">Bằng việc tiếp tục, bạn đồng ý với <a href="#terms">Điều khoản dịch vụ</a> và <a href="#privacy">Chính sách bảo mật</a>.</p>
    </div></div>
  </div>;
}
