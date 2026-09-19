import { useCallback, useEffect, useState } from "react";
import axiosClient from "../../api/axios";
import { apiMessage } from "../../components/roles/admin/adminUtils";

type Payment = {
  id: string;
  schoolName: string;
  managerEmail: string;
  planCode: string;
  purpose: string;
  amountVnd: number;
  status: string;
  createdAt: string;
  paidAt: string | null;
};
type RevenueSummary = { paidTransactions: number; pendingTransactions: number; reviewTransactions: number; grossPaidVnd: number };

export default function AdminPayments() {
  const [items, setItems] = useState<Payment[]>([]);
  const [summary, setSummary] = useState<RevenueSummary | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const [payments, report] = await Promise.all([
        axiosClient.get<Payment[]>("/admin/payments"),
        axiosClient.get<RevenueSummary>("/admin/payment-report"),
      ]);
      setItems(payments.data);
      setSummary(report.data);
      setError("");
    } catch (err) {
      setError(apiMessage(err, "Không thể tải giao dịch."));
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const reconcile = async (id: string) => {
    try {
      await axiosClient.post(`/admin/payments/${id}/reconcile`);
      await load();
    } catch (err) {
      setError(apiMessage(err, "Không thể đối soát giao dịch."));
    }
  };

  return (
    <div className="admin-content">
      {summary && <section className="admin-stats-grid">
        <article className="admin-stat-card"><span>Doanh thu đã ghi nhận</span><strong>{summary.grossPaidVnd.toLocaleString("vi-VN")} ₫</strong></article>
        <article className="admin-stat-card"><span>Đã thanh toán</span><strong>{summary.paidTransactions}</strong></article>
        <article className="admin-stat-card"><span>Đang chờ</span><strong>{summary.pendingTransactions}</strong></article>
        <article className="admin-stat-card"><span>Cần đối soát</span><strong>{summary.reviewTransactions}</strong></article>
      </section>}
      {error && <div className="admin-error-banner">{error}</div>}
      <section className="admin-panel">
        <div className="admin-panel-heading"><div><h2>Giao dịch trường</h2><p className="admin-panel-description">Đối soát các giao dịch VNPAY đang chờ hoặc cần kiểm tra.</p></div><button type="button" className="admin-secondary-button" onClick={() => void load()}>Làm mới</button></div>
        <div className="admin-table-scroll"><table className="admin-table">
          <thead><tr><th>Trường</th><th>Quản lý</th><th>Gói</th><th>Số tiền</th><th>Trạng thái</th><th>Thời gian</th><th /></tr></thead>
          <tbody>{items.length === 0 ? <tr><td colSpan={7}>Chưa có giao dịch.</td></tr> : items.map((item) => <tr key={item.id}>
            <td>{item.schoolName}</td><td>{item.managerEmail}</td><td>{item.planCode}<small>{item.purpose}</small></td><td>{item.amountVnd.toLocaleString("vi-VN")} ₫</td><td>{item.status}</td><td>{new Date(item.createdAt).toLocaleString("vi-VN")}</td>
            <td>{item.status === "PENDING" || item.status === "REQUIRES_REVIEW" ? <button type="button" className="admin-inline-button" onClick={() => void reconcile(item.id)}>Đối soát</button> : null}</td>
          </tr>)}</tbody>
        </table></div>
      </section>
    </div>
  );
}
