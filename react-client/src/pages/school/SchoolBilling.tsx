import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { getRegistrationPlans, type LicensePlan } from "../../api/authApi";
import { purchaseSchoolPlan, quoteSchoolPlan, schoolBilling, setNextSchoolPlan } from "../../api/schoolApi";
import { getLicenseStatus } from "../../api/userApi";
import type { PlanQuote, SchoolBilling as Billing } from "../../types/school";
import { apiMessage } from "../../components/roles/admin/adminUtils";

const money = (amount: number) => `${amount.toLocaleString("vi-VN")} ₫`;
const date = (value: string | null) => value ? new Date(`${value}T00:00:00`).toLocaleDateString("vi-VN") : "Chưa cấp";
const statuses: Record<string, string> = { PAID: "Đã thanh toán", FAILED: "Thất bại", PENDING: "Chờ thanh toán", EXPIRED: "Hết phiên", REQUIRES_REVIEW: "Cần đối soát" };
const purposes: Record<string, string> = { REGISTRATION: "Đăng ký", UPGRADE: "Nâng gói", RENEWAL: "Gia hạn" };

export default function SchoolBilling() {
  const navigate = useNavigate();
  const [billing, setBilling] = useState<Billing | null>(null);
  const [plans, setPlans] = useState<LicensePlan[]>([]);
  const [licenseWritable, setLicenseWritable] = useState<boolean | null>(null);
  const [selected, setSelected] = useState("");
  const [quote, setQuote] = useState<PlanQuote | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const load = async () => {
    setLoading(true); setError("");
    try { const [next, catalog, license] = await Promise.all([schoolBilling(), getRegistrationPlans(), getLicenseStatus().catch(() => null)]); setBilling(next); setPlans(catalog); setLicenseWritable(license?.canPerformWriteOperations ?? null); setSelected(next.nextPlanCode || next.planCode || ""); setQuote(null); }
    catch (err) { setError(apiMessage(err, "Không thể tải thông tin gói.")); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);
  const getQuote = async () => {
    setBusy(true); setError(""); setQuote(null); setNotice("");
    try { setQuote(await quoteSchoolPlan(selected)); }
    catch (err) { setError(apiMessage(err, "Không thể lấy báo giá.")); }
    finally { setBusy(false); }
  };
  const schedule = async () => {
    setBusy(true); setError(""); setNotice("");
    try { setBilling(await setNextSchoolPlan(selected)); setQuote(null); setNotice("Đã lưu lựa chọn kỳ sau. Gói hiện tại giữ nguyên; bạn cần xác nhận thanh toán khi gia hạn."); }
    catch (err) { setError(apiMessage(err, "Không thể lưu gói kỳ sau.")); }
    finally { setBusy(false); }
  };
  const pay = async () => {
    if (!quote || busy) return; setBusy(true); setError("");
    try {
      const payment = await purchaseSchoolPlan(quote.planCode, quote.amountVnd);
      sessionStorage.setItem("physlive.schoolPayment", payment.paymentId);
      if (payment.paymentUrl) window.location.assign(payment.paymentUrl);
      else navigate(`/signup/payment-result?vnp_TxnRef=${encodeURIComponent(payment.paymentId)}`);
    } catch (err) { setError(apiMessage(err, "Không thể tạo thanh toán.")); setQuote(null); setBusy(false); }
  };
  return <div className="admin-content">
    {error && <div className="admin-error-banner" role="alert">{error}<button className="admin-inline-button" onClick={() => void load()}>Tải lại</button></div>}
    {notice && <p role="status">{notice}</p>}
    {loading ? <p role="status">Đang tải thông tin gói…</p> : billing && <>
      <section className="admin-panel"><div className="admin-panel-heading"><div><h2>{plans.find(plan => plan.code === billing.planCode)?.name || billing.planCode || "License do admin cấp"}</h2><p className="admin-panel-description">{date(billing.licenseStart)} — {date(billing.licenseEnd)}</p></div></div><div className="admin-stats-grid">
        <article className="admin-stat-card"><p>Học sinh đang hoạt động</p><strong>{billing.studentsUsed.toLocaleString("vi-VN")} / {billing.studentQuota == null ? "Không giới hạn" : billing.studentQuota.toLocaleString("vi-VN")}</strong></article>
        <article className="admin-stat-card"><p>Token AI tháng này</p><strong>{billing.tokensUsed.toLocaleString("vi-VN")} / {billing.monthlyTokenQuota == null ? "Không giới hạn" : billing.monthlyTokenQuota.toLocaleString("vi-VN")}</strong></article>
      </div>{billing.nextPlanCode && <p>Gói dự kiến kỳ sau: <strong>{billing.nextPlanCode}</strong>. Chưa thanh toán hoặc tự động chuyển gói.</p>}</section>
      <section className="admin-panel"><div className="admin-panel-heading"><div><h2>Nâng gói hoặc gia hạn</h2><p className="admin-panel-description">Nâng gói thu chênh lệch theo số ngày còn lại, giữ ngày hết hạn. Hạ gói áp dụng kỳ sau. Gia hạn mở khi gói hết hạn.</p></div></div>
        {licenseWritable === false && <p className="school-license-unavailable" role="status">License đã hết hạn hoặc chưa hiệu lực. Hãy lấy báo giá để gia hạn; không thể lưu gói cho kỳ sau.</p>}
        {licenseWritable === null && <p className="school-license-unavailable" role="status">Chưa xác minh được license. Hãy tải lại trang trước khi lưu gói kỳ sau.</p>}
        <div className="admin-form-grid"><label>Chọn gói<select value={selected} disabled={busy} onChange={e => { setSelected(e.target.value); setQuote(null); setError(""); setNotice(""); }}><option value="">Chọn gói</option>{plans.map(plan => <option key={plan.code} value={plan.code}>{plan.name} · {money(plan.annualPriceVnd)} / năm</option>)}</select></label><div className="admin-form-actions"><button className="admin-secondary-button" disabled={!selected || busy || licenseWritable !== true} title={licenseWritable === false ? "Chỉ có thể chọn gói kỳ sau khi license còn hiệu lực." : undefined} onClick={() => void schedule()}>Lưu lựa chọn kỳ sau</button><button className="admin-primary-button" disabled={!selected || busy} onClick={() => void getQuote()}>Lấy báo giá</button></div></div>
        {quote && <div className="admin-billing-quote"><h3>{purposes[quote.purpose]}</h3><p>Thời hạn: {date(quote.licenseStart)} — {date(quote.licenseEnd)}</p><p>Tổng thanh toán: <strong>{money(quote.amountVnd)}</strong></p><button className="admin-primary-button" disabled={busy} onClick={() => void pay()}>{busy ? "Đang xử lý…" : "Xác nhận & thanh toán qua VNPAY Sandbox"}</button></div>}
      </section>
      <section className="admin-panel"><div className="admin-panel-heading"><h2>Lịch sử thanh toán</h2></div><div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Thời gian</th><th>Gói</th><th>Nội dung</th><th>Số tiền</th><th>Trạng thái</th><th /></tr></thead><tbody>{billing.payments.length === 0 ? <tr><td colSpan={6}>Chưa có giao dịch.</td></tr> : billing.payments.map(payment => <tr key={payment.id}><td>{new Date(payment.createdAt).toLocaleString("vi-VN")}</td><td>{payment.planCode}</td><td>{purposes[payment.purpose] || payment.purpose}</td><td>{money(payment.amountVnd)}</td><td>{statuses[payment.status] || payment.status}</td><td><Link to={`/signup/payment-result?vnp_TxnRef=${encodeURIComponent(payment.id)}`}>Xem giao dịch</Link></td></tr>)}</tbody></table></div></section>
    </>}
  </div>;
}
