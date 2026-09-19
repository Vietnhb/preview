import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getLicenseStatus, type LicenseStatus } from "../../api/userApi";
import { schoolReportClasses, schoolReportSummary, type SchoolReportClass, type SchoolReportSummary } from "../../api/schoolApi";
import { apiMessage } from "../../components/roles/admin/adminUtils";
import LearningIcon from "../../components/common/LearningIcon";
import { usePhysliveStore } from "../../store/usePhysliveStore";

const dateLabel = (value: string | null) => value
  ? new Date(`${value}T00:00:00`).toLocaleDateString("vi-VN")
  : "Chưa cấp";

function SummaryCard({ label, value, icon, tone, detail }: Readonly<{ label: string; value: string | number; icon: "users" | "book" | "activity" | "chart"; tone: string; detail?: string }>) {
  return <article className="admin-stat-card school-summary-card">
    <div className={`admin-stat-icon ${tone}`}><LearningIcon name={icon} /></div>
    <p>{label}</p>
    <strong>{value}</strong>
    {detail && <small>{detail}</small>}
  </article>;
}

export default function SchoolDashboard() {
  const schoolId = usePhysliveStore(state => state.user?.schoolId);
  const fullName = usePhysliveStore(state => state.user?.fullName);
  const [summary, setSummary] = useState<SchoolReportSummary | null>(null);
  const [classes, setClasses] = useState<SchoolReportClass[]>([]);
  const [license, setLicense] = useState<LicenseStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [licenseUnavailable, setLicenseUnavailable] = useState(false);
  const [classesUnavailable, setClassesUnavailable] = useState(false);

  const load = useCallback(async () => {
    if (!schoolId) return;
    setLoading(true);
    setError("");
    setLicenseUnavailable(false);
    setClassesUnavailable(false);
    const [summaryResult, classesResult, licenseResult] = await Promise.allSettled([
      schoolReportSummary(schoolId),
      schoolReportClasses(schoolId),
      getLicenseStatus(),
    ]);
    if (summaryResult.status === "fulfilled") setSummary(summaryResult.value);
    else setError(apiMessage(summaryResult.reason, "Không thể tải tổng quan trường."));
    if (classesResult.status === "fulfilled") setClasses(classesResult.value);
    else {
      setClasses([]);
      setClassesUnavailable(true);
      if (summaryResult.status === "fulfilled") setError(apiMessage(classesResult.reason, "Không thể tải danh sách lớp."));
    }
    if (licenseResult.status === "fulfilled") setLicense(licenseResult.value);
    else {
      setLicense(null);
      setLicenseUnavailable(true);
    }
    setLoading(false);
  }, [schoolId]);

  useEffect(() => {
    const timer = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const quota = summary?.tokenQuota;
  const tokenUsage = summary?.usedTokens ?? 0;
  const quotaPercent = quota == null ? 0 : quota > 0
    ? Math.min(100, Math.round(tokenUsage / quota * 100))
    : tokenUsage > 0 ? 100 : 0;

  return <div className="admin-content school-dashboard">
    <section className="school-welcome">
      <div>
        <p className="admin-eyebrow">KHÔNG GIAN TRƯỜNG HỌC</p>
        <h1>Xin chào{fullName ? `, ${fullName}` : ""}</h1>
        <p>{summary?.schoolName ?? "Theo dõi tài khoản, lớp học và license của trường tại đây."}</p>
      </div>
      <button type="button" className="admin-secondary-button" onClick={() => void load()} disabled={loading}>
        <LearningIcon name="refresh" />{loading ? "Đang tải…" : "Làm mới dữ liệu"}
      </button>
    </section>

    {error && <div className="admin-error-banner" role="alert">{error}<button type="button" className="admin-inline-button" onClick={() => void load()}>Thử lại</button></div>}
    {loading && !summary ? <div className="admin-loading"><span className="admin-loading-spinner" /><p>Đang tải dữ liệu trường…</p></div> : summary && <>
      <section className="admin-stats-grid school-summary-grid" aria-label="Chỉ số trường học">
        <SummaryCard label="Học sinh đang hoạt động" value={summary.students.toLocaleString("vi-VN")} icon="users" tone="blue" detail={`${summary.enrolledStudents.toLocaleString("vi-VN")} lượt xếp lớp`} />
        <SummaryCard label="Giáo viên" value={summary.teachers.toLocaleString("vi-VN")} icon="book" tone="purple" detail="Tài khoản thuộc trường" />
        <SummaryCard label="Lớp đang hoạt động" value={summary.activeClasses.toLocaleString("vi-VN")} icon="activity" tone="green" detail="Theo năm học hiện tại" />
        <SummaryCard label="Token AI đã dùng" value={tokenUsage.toLocaleString("vi-VN")} icon="chart" tone="orange" detail={quota == null ? "Không giới hạn theo gói" : `Trong tổng ${quota.toLocaleString("vi-VN")}`} />
      </section>

      <div className="school-dashboard-grid">
        <section className="admin-panel school-license-panel">
          <div className="admin-panel-heading">
            <div><h2>License & quota</h2><p className="admin-panel-description">Trạng thái quyền sử dụng và giới hạn gói hiện tại.</p></div>
            <span className={`admin-status ${licenseUnavailable ? "" : license?.canPerformWriteOperations ? "active" : "inactive"}`}><i />{licenseUnavailable ? "Chưa xác minh" : license?.canPerformWriteOperations ? "Đang hiệu lực" : "Chưa hiệu lực"}</span>
          </div>
          {licenseUnavailable && <p className="school-license-unavailable" role="status">Không tải được trạng thái license. Hãy thử làm mới để kiểm tra lại.</p>}
          <div className="school-license-details">
            <div><span>Hạn sử dụng</span><strong>{dateLabel(license?.licenseEnd ?? summary.licenseEnd)}</strong></div>
            <div><span>Thời gian còn lại</span><strong>{license ? (license.daysUntilExpiry >= 0 ? `${license.daysUntilExpiry} ngày` : "Đã hết hạn") : "—"}</strong></div>
            <div><span>Quota token tháng</span><strong>{quota == null ? "Không giới hạn" : `${Math.max(0, quota - tokenUsage).toLocaleString("vi-VN")} còn lại`}</strong></div>
          </div>
          {quota != null && <div className="school-quota-meter" aria-label={`Đã dùng ${quotaPercent}% quota token tháng`}><span style={{ width: `${quotaPercent}%` }} /></div>}
          <Link to="/school/billing" className="admin-primary-button school-billing-link">Xem gói & thanh toán<LearningIcon name="arrow" /></Link>
        </section>

        <section className="admin-panel school-quick-panel">
          <div className="admin-panel-heading"><div><h2>Thao tác nhanh</h2><p className="admin-panel-description">Các việc quản lý thường dùng.</p></div></div>
          <div className="school-quick-actions">
            <Link to="/school/users"><LearningIcon name="users" /><span><strong>Quản lý tài khoản</strong><small>Tạo hoặc tạm khóa tài khoản</small></span><LearningIcon name="arrow" /></Link>
            <Link to="/school/classes"><LearningIcon name="book" /><span><strong>Quản lý lớp học</strong><small>Phân giáo viên và xếp học sinh</small></span><LearningIcon name="arrow" /></Link>
            <Link to="/school/reports"><LearningIcon name="chart" /><span><strong>Xem báo cáo</strong><small>Nhập dữ liệu và theo dõi sử dụng</small></span><LearningIcon name="arrow" /></Link>
          </div>
        </section>

        <section className="admin-panel school-classes-panel">
          <div className="admin-panel-heading"><div><h2>Lớp học gần đây</h2><p className="admin-panel-description">Danh sách lớp đang hoạt động trong trường.</p></div><Link to="/school/classes" className="admin-secondary-button">Tất cả lớp học</Link></div>
          {classesUnavailable ? <p className="school-license-unavailable" role="status">Không thể tải danh sách lớp. Hãy thử làm mới.</p> : classes.length === 0 ? <div className="school-empty-classes"><LearningIcon name="book" /><p>Trường chưa có lớp đang hoạt động.</p><Link to="/school/classes" className="admin-primary-button">Tạo lớp đầu tiên</Link></div> : <div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Lớp</th><th>Năm học</th><th>Giáo viên</th><th>Học sinh</th></tr></thead><tbody>{classes.slice(0, 5).map(item => <tr key={item.id}><td><strong>{item.name}</strong><small className="school-class-subtitle">Khối {item.gradeLevel}</small></td><td>{item.schoolYear}</td><td>{item.teachers}</td><td>{item.students}</td></tr>)}</tbody></table></div>}
        </section>
      </div>
    </>}
  </div>;
}
