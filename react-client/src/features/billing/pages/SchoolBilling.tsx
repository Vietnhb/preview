import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Badge, Button, Card, Spinner, Table } from "@radix-ui/themes";
import { motion, useReducedMotion } from "motion/react";
import { getRegistrationPlans, type LicensePlan } from "../api/billingApi";
import { purchaseSchoolPlan, quoteSchoolPlan, schoolBilling } from "../api/billingApi";
import { getLicenseStatus } from "../../account/api/userApi";
import type { PlanQuote, SchoolBilling as Billing } from "../../school/types";
import { apiMessage } from "../../../shared/lib/apiError";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import PlanCard from "../components/PlanCard";
import s from "./SchoolBilling.module.css";

const money = (amount: number) => `${amount.toLocaleString("vi-VN")} ₫`;
const date = (value: string | null) =>
  value
    ? new Date(`${value}T00:00:00`).toLocaleDateString("vi-VN")
    : "Chưa cấp";
const statuses: Record<string, string> = {
  PAID: "Đã thanh toán",
  FAILED: "Thất bại",
  PENDING: "Chờ thanh toán",
  EXPIRED: "Hết phiên",
  REQUIRES_REVIEW: "Cần đối soát",
};
const purposes: Record<string, string> = {
  REGISTRATION: "Đăng ký",
  UPGRADE: "Nâng gói",
  RENEWAL: "Gia hạn",
};

export default function SchoolBilling() {
  const reducedMotion = useReducedMotion();
  const navigate = useNavigate();
  const user = useSessionStore((state) => state.user);
  const setUser = useSessionStore((state) => state.setUser);
  const [billing, setBilling] = useState<Billing | null>(null);
  const [plans, setPlans] = useState<LicensePlan[]>([]);
  const [licenseWritable, setLicenseWritable] = useState<boolean | null>(null);
  const [selected, setSelected] = useState("");
  const [quote, setQuote] = useState<PlanQuote | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [next, catalog, license] = await Promise.all([
        schoolBilling(),
        getRegistrationPlans(),
        getLicenseStatus().catch(() => null),
      ]);
      setBilling(next);
      setPlans(catalog);
      setLicenseWritable(license?.canPerformWriteOperations ?? null);
      const currentChoice = next.planChoices?.find(choice => choice.planCode === next.planCode && choice.allowed);
      setSelected(currentChoice?.planCode || next.planChoices?.find(choice => choice.allowed)?.planCode || "");
      setQuote(null);
      if (
        user &&
        license &&
        user.billingRequired !== !license.canPerformWriteOperations
      ) {
        setUser({
          ...user,
          billingRequired: !license.canPerformWriteOperations,
        });
      }
    } catch (err) {
      setError(apiMessage(err, "Không thể tải thông tin gói."));
    } finally {
      setLoading(false);
    }
  }, [setUser, user]);
  useEffect(() => {
    void load();
  }, [load]);
  const getQuote = async () => {
    setBusy(true);
    setError("");
    setQuote(null);
    try {
      setQuote(await quoteSchoolPlan(selected));
    } catch (err) {
      setError(apiMessage(err, "Không thể lấy báo giá."));
    } finally {
      setBusy(false);
    }
  };
  const pay = async () => {
    if (!quote || busy) return;
    setBusy(true);
    setError("");
    try {
      const payment = await purchaseSchoolPlan(quote.planCode, quote.amountVnd);
      sessionStorage.setItem("physlive.schoolPayment", payment.paymentId);
      if (payment.paymentUrl) window.location.assign(payment.paymentUrl);
      else
        navigate(
          `/signup/payment-result?vnp_TxnRef=${encodeURIComponent(payment.paymentId)}`,
        );
    } catch (err) {
      setError(apiMessage(err, "Không thể tạo thanh toán."));
      setQuote(null);
      setBusy(false);
    }
  };
  const selectedChoice = billing?.planChoices?.find(choice => choice.planCode === selected);
  return <motion.div className={`admin-content ${s.billing}`} initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: reducedMotion ? 0 : .18 }}>
    <header className="admin-content-header"><h1 className="admin-content-title">Gói và thanh toán</h1></header>
    {user?.billingRequired && <div className="admin-error-banner" role="status">Trường chưa có gói còn hiệu lực. Hãy chọn gói và hoàn tất thanh toán để mở các chức năng quản lý.</div>}
    {error && <div className="admin-error-banner" role="alert">{error}<Button variant="soft" color="red" onClick={() => void load()}>Tải lại</Button></div>}
    {loading ? <p role="status"><Spinner /> Đang tải thông tin gói…</p> : billing && <>
      <Card asChild size="3"><section className={`admin-panel school-billing-plan-panel ${s.planPanel}`}>
        <div className={`school-signup-section-heading ${s.planHeading}`}><div><h2>{user?.billingRequired ? "Chọn gói để bắt đầu" : "Nâng gói hoặc gia hạn"}</h2></div><Badge size="2" color="indigo">Gói năm</Badge></div>
        <p style={{ margin: "0 0 18px", color: "var(--gray-11)", fontSize: 14 }}>Nâng gói có hiệu lực ngay sau khi thanh toán, thu chênh lệch theo thời hạn còn lại. Gia hạn gói hiện tại thêm một năm. Khi gói còn hiệu lực, không thể chuyển sang gói nhỏ hơn.</p>
        {licenseWritable === false && <p className="school-license-unavailable" role="status">Gói đã hết hạn hoặc chưa hiệu lực. Chọn gói phù hợp với số học sinh để gia hạn.</p>}
        {licenseWritable === null && <p className="school-license-unavailable" role="status">Chưa xác minh được trạng thái license. Danh sách gói khả dụng được kiểm tra khi lấy báo giá.</p>}
        {plans.length === 0 ? <p className={s.emptyState}>Chưa có gói đăng ký khả dụng.</p> : <div className="school-signup-plans school-billing-plans" role="radiogroup" aria-label="Gói đăng ký">
          {plans.map(plan => {
            const choice = billing.planChoices?.find(entry => entry.planCode === plan.code);
            return <div key={plan.code} style={{ display: "flex", flexDirection: "column", gap: 10, minWidth: 0 }}><PlanCard plan={plan} name="school-plan" selected={selected === plan.code} disabled={busy || choice?.allowed !== true} current={billing.planCode === plan.code} onSelect={code => { setSelected(code); setQuote(null); setError(""); }} />
              <p style={{ margin: 0, color: choice?.allowed ? "var(--indigo-11)" : "var(--gray-11)", fontSize: 13, lineHeight: 1.6 }} role={choice?.allowed ? undefined : "note"}>{choice?.allowed ? choice.purpose === "UPGRADE" ? "Nâng cấp ngay sau thanh toán" : "Gia hạn thêm một năm" : choice?.reason || "Chưa xác minh được gói khả dụng."}</p>
            </div>;
          })}
        </div>}
        <div className="school-billing-plan-actions"><Button disabled={!selected || busy || selectedChoice?.allowed !== true} loading={busy && !quote} onClick={() => void getQuote()}>Lấy báo giá</Button></div>
        {quote && <div className={`admin-billing-quote ${s.quote}`}><div><h3>{purposes[quote.purpose]}</h3><p>Thời hạn: {date(quote.licenseStart)} — {date(quote.licenseEnd)}</p><p>Tổng thanh toán: <strong>{money(quote.amountVnd)}</strong></p>{quote.purpose === "UPGRADE" && <p>Gói mới thay thế gói hiện tại ngay khi thanh toán thành công.</p>}</div><Button disabled={busy} loading={busy} onClick={() => void pay()}>Xác nhận và thanh toán qua VNPAY Sandbox</Button></div>}
      </section></Card>
      <Card asChild size="3"><section className={`admin-panel ${s.currentPanel}`} aria-label="Gói hiện tại và mức sử dụng">
        <div className="admin-panel-heading"><div><h2>{plans.find(plan => plan.code === billing.planCode)?.name || billing.planCode || "License do quản lý cấp"}</h2><p className="admin-panel-description">{date(billing.licenseStart)} — {date(billing.licenseEnd)}</p></div><Badge color={licenseWritable ? "green" : "orange"}>{licenseWritable ? "Đang hiệu lực" : licenseWritable === false ? "Chưa hiệu lực" : "Chưa xác minh"}</Badge></div>
        <div className="admin-stats-grid"><Card size="3" asChild><article className="admin-stat-card"><p>Học sinh đang hoạt động</p><strong>{billing.studentsUsed.toLocaleString("vi-VN")} / {billing.studentQuota == null ? "Không giới hạn" : billing.studentQuota.toLocaleString("vi-VN")}</strong></article></Card><Card size="3" asChild><article className="admin-stat-card"><p>Token AI tháng này</p><strong>{billing.tokensUsed.toLocaleString("vi-VN")} / {billing.monthlyTokenQuota == null ? "Không giới hạn" : billing.monthlyTokenQuota.toLocaleString("vi-VN")}</strong></article></Card></div>
      </section></Card>
      <Card asChild size="3"><section className={`admin-panel ${s.historyPanel}`}><div className="admin-panel-heading"><h2>Lịch sử thanh toán</h2></div><div className="admin-table-scroll"><Table.Root variant="surface" size="2"><Table.Header><Table.Row><Table.ColumnHeaderCell>Thời gian</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Gói</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Nội dung</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Số tiền</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Giao dịch</Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>
        {billing.payments.length === 0 ? <Table.Row><Table.Cell colSpan={6}>Chưa có giao dịch.</Table.Cell></Table.Row> : billing.payments.map(payment => <Table.Row key={payment.id}><Table.Cell>{new Date(payment.createdAt).toLocaleString("vi-VN")}</Table.Cell><Table.Cell>{plans.find(plan => plan.code === payment.planCode)?.name || payment.planCode}</Table.Cell><Table.Cell>{purposes[payment.purpose] || payment.purpose}</Table.Cell><Table.Cell>{money(payment.amountVnd)}</Table.Cell><Table.Cell><Badge color={payment.status === "PAID" ? "green" : payment.status === "PENDING" ? "amber" : "red"}>{statuses[payment.status] || payment.status}</Badge></Table.Cell><Table.Cell><Button asChild variant="ghost" size="1"><Link to={`/signup/payment-result?vnp_TxnRef=${encodeURIComponent(payment.id)}`}>Xem giao dịch</Link></Button></Table.Cell></Table.Row>)}
      </Table.Body></Table.Root></div></section></Card>
    </>}
  </motion.div>;
}