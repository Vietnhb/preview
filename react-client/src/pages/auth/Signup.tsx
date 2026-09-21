import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import axios from "axios";
import { createSchoolCheckout, getRegistrationPlans, type LicensePlan } from "../../api/authApi";
import "../../styles/account.css";

const money = (value: number) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(value);
const count = (value: number) => value.toLocaleString("vi-VN");
const steps = ["Chọn gói", "Thông tin trường", "Thanh toán"];
const initial = { planCode: "", schoolName: "", schoolCode: "", address: "", fullName: "", email: "", phoneNumber: "", password: "" };

export default function Signup() {
  const navigate = useNavigate();
  const [plans, setPlans] = useState<LicensePlan[]>([]);
  const [form, setForm] = useState(initial);
  const [step, setStep] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [accepted, setAccepted] = useState(false);
  const [busy, setBusy] = useState(false);
  const selected = plans.find(plan => plan.code === form.planCode);
  const loadPlans = async () => {
    setLoading(true); setError("");
    try { setPlans(await getRegistrationPlans()); }
    catch { setError("Không thể tải danh sách gói. Vui lòng thử lại."); }
    finally { setLoading(false); }
  };
  useEffect(() => { void loadPlans(); }, []);
  const field = (name: keyof typeof initial, value: string) => setForm(current => ({ ...current, [name]: value }));
  const next = (event: FormEvent) => { event.preventDefault(); setError(""); setStep(2); };
  const checkout = async () => {
    if (!selected || !accepted || busy) return;
    setBusy(true); setError("");
    try {
      const payment = await createSchoolCheckout(form);
      sessionStorage.setItem("physlive.schoolPayment", payment.paymentId);
      if (payment.paymentUrl) window.location.assign(payment.paymentUrl);
      else navigate(`/signup/payment-result?vnp_TxnRef=${encodeURIComponent(payment.paymentId)}`);
    } catch (err) {
      setError(axios.isAxiosError<{ message?: string }>(err) ? err.response?.data?.message || "Không thể tạo thanh toán. Vui lòng thử lại." : "Không thể kết nối tới máy chủ.");
      setBusy(false);
    }
  };

  return <main className="school-signup">
    <header className="school-signup-header"><Link to="/" className="school-signup-brand">PhysLive<span>FOR SCHOOLS</span></Link><span>Đã có tài khoản? <Link to="/login">Đăng nhập</Link></span></header>
    <div className="school-signup-body">
      <div className="school-signup-intro"><p className="school-signup-eyebrow">KHÔNG GIAN HỌC TẬP CỦA NHÀ TRƯỜNG</p><h1>Bắt đầu cùng PhysLive</h1><p>Chọn gói phù hợp và thiết lập tài khoản quản lý cho trường của bạn.</p></div>
      <ol className="school-signup-steps" aria-label="Tiến trình đăng ký">{steps.map((label, index) => <li key={label} aria-current={step === index ? "step" : undefined} className={index <= step ? "is-current" : ""}><span>{index < step ? "✓" : index + 1}</span>{label}</li>)}</ol>
      {error && <div className="school-signup-error" role="alert">{error}{step === 0 && <button type="button" onClick={() => void loadPlans()}>Thử lại</button>}</div>}
      {step === 0 ? <section aria-labelledby="plans-heading">
        <div className="school-signup-section-heading"><div><h2 id="plans-heading">Một gói cho cả nhà trường</h2><p>Thanh toán theo năm. Quota AI tính theo token thực tế.</p></div><span className="school-signup-billing">Gói năm</span></div>
        {loading ? <p role="status">Đang tải các gói…</p> : plans.length === 0 ? <p>Chưa có gói đăng ký khả dụng.</p> : <div className="school-signup-plans" role="radiogroup" aria-label="Gói đăng ký">{plans.map(plan => <label key={plan.code} className={`school-signup-plan ${selected?.code === plan.code ? "is-selected" : ""}`}>
          <div className="school-signup-plan-title"><h3>{plan.name}</h3><input type="radio" name="plan" value={plan.code} checked={selected?.code === plan.code} onChange={() => field("planCode", plan.code)} /></div><p>{plan.description}</p>
          <div className="school-signup-price">{money(plan.annualPriceVnd)}<span>/ năm</span></div>
          <ul><li><span aria-hidden="true">✓</span><strong>{count(plan.studentQuota)}</strong> học sinh</li><li><span aria-hidden="true">✓</span>{plan.monthlyTokenQuota === null ? "Token AI không giới hạn" : `${count(plan.monthlyTokenQuota)} token AI / tháng`}</li><li><span aria-hidden="true">✓</span>Giáo viên và lớp học không giới hạn</li><li><span aria-hidden="true">✓</span>Học sinh chạy mô phỏng miễn phí</li></ul>
          <span className="school-signup-plan-select">{selected?.code === plan.code ? "Đã chọn gói" : "Chọn gói này"}</span>
        </label>)}</div>}
        <div className="school-signup-bottom"><p>Giáo viên và học sinh nhận tài khoản từ quản lý trường.</p><button type="button" className="school-signup-primary" disabled={!selected || loading} onClick={() => setStep(1)}>Tiếp tục <span aria-hidden="true">→</span></button></div>
      </section> : <div className="school-signup-layout"><section className="school-signup-card">
        {step === 1 ? <form onSubmit={next}>
          <h2>Thiết lập nhà trường</h2><p className="school-signup-muted">Thông tin trường và người phụ trách quản lý.</p>
          <fieldset><legend>Thông tin trường</legend><div className="school-signup-fields">
            <label className="school-signup-wide">Tên trường<input required maxLength={200} autoComplete="organization" value={form.schoolName} onChange={e => field("schoolName", e.target.value)} placeholder="Tên đầy đủ của nhà trường" /></label>
            <label>Mã trường<input required maxLength={80} pattern={"[A-Za-z0-9_\\-]+"} title="Chỉ dùng chữ không dấu, số, dấu gạch ngang hoặc gạch dưới" value={form.schoolCode} onChange={e => field("schoolCode", e.target.value.toUpperCase())} placeholder="THPT-NGUYENTRAI" /></label>
            <label>Số điện thoại liên hệ<input required type="tel" maxLength={20} autoComplete="tel" value={form.phoneNumber} onChange={e => field("phoneNumber", e.target.value)} /></label>
            <label className="school-signup-wide">Địa chỉ<input required maxLength={300} autoComplete="street-address" value={form.address} onChange={e => field("address", e.target.value)} /></label>
          </div></fieldset>
          <fieldset><legend>Tài khoản quản lý</legend><div className="school-signup-fields">
            <label className="school-signup-wide">Họ và tên<input required maxLength={200} autoComplete="name" value={form.fullName} onChange={e => field("fullName", e.target.value)} /></label>
            <label className="school-signup-wide">Email liên hệ và đăng nhập<input required type="email" maxLength={100} autoComplete="email" value={form.email} onChange={e => field("email", e.target.value)} /></label>
            <label className="school-signup-wide">Mật khẩu<input required type="password" minLength={8} maxLength={72} autoComplete="new-password" value={form.password} onChange={e => field("password", e.target.value)} /><small>Từ 8 đến 72 ký tự. Không chia sẻ tài khoản quản lý.</small></label>
          </div></fieldset><div className="school-signup-form-footer"><button type="button" className="school-signup-secondary" onClick={() => setStep(0)}>Quay lại</button><button className="school-signup-primary" type="submit">Tiếp tục thanh toán →</button></div>
        </form> : <div>
          <h2>Kiểm tra & thanh toán</h2><p className="school-signup-muted">Gói được kích hoạt sau khi thanh toán thành công.</p>
          <div className="school-signup-review-heading"><h3>Thông tin đăng ký</h3><button type="button" onClick={() => setStep(1)}>Chỉnh sửa</button></div>
          <dl className="school-signup-review">{[["Nhà trường", form.schoolName], ["Mã trường", form.schoolCode], ["Địa chỉ", form.address], ["Người quản lý", form.fullName], ["Email", form.email], ["Điện thoại", form.phoneNumber]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
          <div className="school-signup-total"><span>Tổng thanh toán / năm</span><strong>{selected && money(selected.annualPriceVnd)}</strong></div>
          <div className="school-signup-note"><strong>Kích hoạt tự động</strong><p>Sau khi xác nhận thanh toán thành công, PhysLive mở tài khoản quản lý, kích hoạt gói và thông báo cho admin.</p></div>
          <label className="school-signup-consent"><input type="checkbox" checked={accepted} onChange={e => setAccepted(e.target.checked)} /> <span>Tôi đại diện nhà trường và đồng ý với <Link to="/terms" target="_blank" rel="noopener noreferrer">Điều khoản dịch vụ</Link>.</span></label>
          <div className="school-signup-form-footer"><button type="button" className="school-signup-secondary" disabled={busy} onClick={() => setStep(1)}>Quay lại</button><button type="button" className="school-signup-primary" disabled={!accepted || busy} onClick={() => void checkout()}>{busy ? "Đang chuyển đến VNPAY…" : `Thanh toán ${selected ? money(selected.annualPriceVnd) : ""}`}</button></div><p className="school-signup-muted">Bạn sẽ thanh toán trên VNPAY Sandbox — môi trường thử nghiệm.</p>
        </div>}
      </section><aside className="school-signup-summary"><p className="school-signup-eyebrow">GÓI ĐÃ CHỌN</p><h2>{selected?.name}</h2><div className="school-signup-price">{selected && money(selected.annualPriceVnd)}<span>/ năm</span></div><hr /><p>{selected && count(selected.studentQuota)} học sinh</p><p>{selected?.monthlyTokenQuota == null ? "Token AI không giới hạn" : `${count(selected.monthlyTokenQuota)} token AI / tháng`}</p><button type="button" onClick={() => setStep(0)}>Đổi gói</button><div className="school-signup-note">Một tài khoản quản lý cho mỗi trường. Bạn có thể cấp tài khoản giáo viên và học sinh sau khi kích hoạt.</div></aside></div>}
      <footer className="school-signup-help"><Link to="/signup/payment-result">Đã đăng ký nhưng chưa thanh toán? Tiếp tục đăng ký</Link><p>Bạn là giáo viên hoặc học sinh? Liên hệ quản lý trường để được cấp tài khoản.</p></footer>
    </div>
  </main>;
}
