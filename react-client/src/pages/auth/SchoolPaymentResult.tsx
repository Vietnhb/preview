import { useEffect, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import axios from "axios";
import { getSchoolPaymentStatus, recoverSchoolCheckout } from "../../api/authApi";
import "../../styles/account.css";

export default function SchoolPaymentResult() {
  const [params] = useSearchParams();
  const id = params.get("vnp_TxnRef") || sessionStorage.getItem("physlive.schoolPayment");
  const [status, setStatus] = useState("PENDING");
  const [error, setError] = useState("");
  const [checking, setChecking] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [recoveryOpen, setRecoveryOpen] = useState(!id);
  const recover = async (event: FormEvent) => {
    event.preventDefault(); if (busy) return; setBusy(true); setError("");
    try {
      const payment = await recoverSchoolCheckout(email, password);
      sessionStorage.setItem("physlive.schoolPayment", payment.paymentId);
      if (payment.paymentUrl) window.location.assign(payment.paymentUrl);
      else { setPassword(""); window.location.assign(`/signup/payment-result?vnp_TxnRef=${encodeURIComponent(payment.paymentId)}`); }
    } catch (err) { setError(axios.isAxiosError<{ message?: string }>(err) ? err.response?.data?.message || "Không thể tiếp tục thanh toán." : "Không thể kết nối máy chủ."); setBusy(false); }
  };
  const check = async () => {
    if (!id) { setError("Không tìm thấy mã giao dịch."); return; }
    setChecking(true); setError("");
    try { setStatus((await getSchoolPaymentStatus(id)).status); }
    catch { setError("Không thể kiểm tra giao dịch. Vui lòng thử lại."); }
    finally { setChecking(false); }
  };
  useEffect(() => {
    let active = true;
    if (!id) return;
    // Browser redirects cannot activate a subscription; wait for the verified IPN.
    const poll = () => void getSchoolPaymentStatus(id).then(value => { if (active) { setStatus(value.status); setError(""); } }).catch(() => { if (active) setError("Không thể kiểm tra giao dịch. Vui lòng thử lại."); });
    poll();
    const interval = window.setInterval(poll, 3000);
    const deadline = window.setTimeout(() => window.clearInterval(interval), 60000);
    return () => { active = false; window.clearInterval(interval); window.clearTimeout(deadline); };
  }, [id]);
  return <main className="school-signup"><header className="school-signup-header"><Link to="/" className="school-signup-brand">PhysLive<span>FOR SCHOOLS</span></Link><Link to="/login">Đăng nhập</Link></header>
    <section className="school-signup-payment-result"><p className="school-signup-eyebrow">KẾT QUẢ THANH TOÁN</p><div aria-live="polite"><h1>{!id ? "Tiếp tục đăng ký trường" : status === "PAID" ? "Nhà trường đã sẵn sàng" : status === "FAILED" ? "Thanh toán chưa thành công" : status === "EXPIRED" ? "Phiên thanh toán đã hết hạn" : status === "REQUIRES_REVIEW" ? "Giao dịch cần đối soát" : "Đang xác nhận thanh toán"}</h1>
      <p>{!id ? "Dùng email và mật khẩu đã đăng ký để khôi phục phiên thanh toán." : status === "PAID" ? "Gói đã được cập nhật. Admin đã nhận thông báo thanh toán của trường." : status === "REQUIRES_REVIEW" ? "Admin cần đối soát giao dịch này trước khi cập nhật gói. Vui lòng cung cấp mã giao dịch bên dưới khi liên hệ hỗ trợ." : status === "FAILED" || status === "EXPIRED" ? "Bạn có thể tiếp tục đăng ký. PhysLive sẽ xác minh giao dịch trước khi tạo thanh toán mới." : "PhysLive đang chờ xác nhận từ VNPAY. Bạn có thể kiểm tra lại sau ít phút."}</p></div>
      {id && <small>Mã giao dịch: {id}</small>}{error && <p role="alert">{error}</p>}
      {status !== "PAID" && status !== "REQUIRES_REVIEW" && <div className="school-signup-recovery">{!recoveryOpen ? <button type="button" className="school-signup-secondary" onClick={() => setRecoveryOpen(true)}>Tiếp tục đăng ký / thanh toán lại</button> : <form onSubmit={recover}><div className="school-signup-fields"><label className="school-signup-wide">Email quản lý<input type="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} /></label><label className="school-signup-wide">Mật khẩu<input type="password" autoComplete="current-password" required value={password} onChange={e => setPassword(e.target.value)} /></label></div><button type="submit" className="school-signup-primary" disabled={busy}>{busy ? "Đang xác minh…" : "Tiếp tục thanh toán"}</button><p>Đã kích hoạt trường? <Link to="/school/billing">Quản lý gói và lấy báo giá mới</Link></p></form>}</div>}
      <div className="school-signup-form-footer">{status === "PAID" ? <Link className="school-signup-primary" to="/login">Đăng nhập tài khoản quản lý</Link> : <button type="button" className="school-signup-primary" disabled={checking || !id} onClick={() => void check()}>{checking ? "Đang kiểm tra…" : "Kiểm tra trạng thái"}</button>}<Link to="/">Về trang chủ</Link></div>
    </section>
  </main>;
}
