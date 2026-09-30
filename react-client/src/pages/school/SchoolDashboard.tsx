import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Badge, Button, Card, Progress, Spinner, Table } from "@radix-ui/themes";
import { motion, useReducedMotion } from "motion/react";
import { getLicenseStatus, type LicenseStatus } from "../../api/userApi";
import { schoolReportClasses, schoolReportSummary, type SchoolReportClass, type SchoolReportSummary } from "../../api/schoolApi";
import { apiMessage } from "../../components/roles/admin/adminUtils";
import LearningIcon from "../../components/common/LearningIcon";
import { usePhysliveStore } from "../../store/usePhysliveStore";

const dateLabel = (value: string | null) => value
  ? new Date(`${value}T00:00:00`).toLocaleDateString("vi-VN")
  : "Chưa cấp";

function SummaryCard({ label, value, icon, tone, detail }: Readonly<{ label: string; value: string | number; icon: "users" | "book" | "activity" | "chart"; tone: string; detail?: string }>) {
  const reducedMotion = useReducedMotion();
  return <Card asChild size="3"><motion.article className="admin-stat-card school-summary-card" initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: reducedMotion ? 0 : .18 }}>
    <div className={`admin-stat-icon ${tone}`}><LearningIcon name={icon} /></div>
    <p>{label}</p>
    <strong>{value}</strong>
    {detail && <small>{detail}</small>}
  </motion.article></Card>;
}

export default function SchoolDashboard() {
  const reducedMotion = useReducedMotion();
  const schoolId = usePhysliveStore(state => state.user?.schoolId);
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
    <header className="admin-content-header"><div><h1 className="admin-content-title">Tổng quan</h1></div></header>
    {error && <div className="admin-error-banner" role="alert">{error}<Button variant="soft" color="red" onClick={() => void load()}>Thử lại</Button></div>}
    {loading && !summary ? <div className="admin-loading"><Spinner size="3" /><p>Đang tải dữ liệu trường…</p></div> : summary && <>
      <section className="admin-stats-grid school-summary-grid" aria-label="Chỉ số trường học">
        <SummaryCard label="Học sinh đang hoạt động" value={summary.students.toLocaleString("vi-VN")} icon="users" tone="blue" detail={`${summary.enrolledStudents.toLocaleString("vi-VN")} lượt xếp lớp`} />
        <SummaryCard label="Giáo viên" value={summary.teachers.toLocaleString("vi-VN")} icon="book" tone="purple" detail="Tài khoản thuộc trường" />
        <SummaryCard label="Lớp đang hoạt động" value={summary.activeClasses.toLocaleString("vi-VN")} icon="activity" tone="green" detail="Theo năm học hiện tại" />
        <SummaryCard label="Token AI đã dùng" value={tokenUsage.toLocaleString("vi-VN")} icon="chart" tone="orange" detail={quota == null ? "Không giới hạn theo gói" : `Trong tổng ${quota.toLocaleString("vi-VN")}`} />
      </section>

      <motion.div className="school-dashboard-grid" initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: reducedMotion ? 0 : .18 }}>
        <Card size="3" asChild><section className="admin-panel school-license-panel">
          <div className="admin-panel-heading">
            <div><h2>Gói sử dụng</h2></div>
            <Badge color={licenseUnavailable ? "gray" : license?.canPerformWriteOperations ? "green" : "orange"} size="2">{licenseUnavailable ? "Chưa xác minh" : license?.canPerformWriteOperations ? "Đang hiệu lực" : "Chưa hiệu lực"}</Badge>
          </div>
          {licenseUnavailable && <p className="school-license-unavailable" role="status">Không tải được trạng thái license. Hãy thử làm mới để kiểm tra lại.</p>}
          <div className="school-license-details">
            <div><span>Hạn sử dụng</span><strong>{dateLabel(license?.licenseEnd ?? summary.licenseEnd)}</strong></div>
            <div><span>Thời gian còn lại</span><strong>{license ? (license.daysUntilExpiry >= 0 ? `${license.daysUntilExpiry} ngày` : "Đã hết hạn") : "—"}</strong></div>
            <div><span>Quota token tháng</span><strong>{quota == null ? "Không giới hạn" : `${Math.max(0, quota - tokenUsage).toLocaleString("vi-VN")} còn lại`}</strong></div>
          </div>
          {quota != null && <Progress value={quotaPercent} size="2" color={quotaPercent >= 80 ? "orange" : "indigo"} aria-label={`Đã dùng ${quotaPercent}% quota token tháng`} />}
          <Button asChild size="2" mt="5"><Link to="/school/billing">Xem gói & thanh toán<LearningIcon name="arrow" /></Link></Button>
        </section></Card>

        <Card size="3" asChild><section className="admin-panel school-quick-panel">
          <div className="admin-panel-heading"><div><h2>Thao tác nhanh</h2></div></div>
          <div className="school-quick-actions">
            <Button asChild variant="soft" color="indigo" size="3"><Link to="/school/users"><LearningIcon name="users" /><span><strong>Quản lý tài khoản</strong><small>Tạo hoặc tạm khóa tài khoản</small></span><LearningIcon name="arrow" /></Link></Button>
            <Button asChild variant="soft" color="cyan" size="3"><Link to="/school/classes"><LearningIcon name="book" /><span><strong>Quản lý lớp học</strong><small>Phân giáo viên và xếp học sinh</small></span><LearningIcon name="arrow" /></Link></Button>
            <Button asChild variant="soft" color="amber" size="3"><Link to="/school/reports"><LearningIcon name="chart" /><span><strong>Xem báo cáo</strong><small>Nhập dữ liệu và theo dõi sử dụng</small></span><LearningIcon name="arrow" /></Link></Button>
          </div>
        </section></Card>

        <Card size="3" asChild><section className="admin-panel school-classes-panel">
          <div className="admin-panel-heading"><div><h2>Lớp học gần đây</h2></div><Button asChild variant="soft"><Link to="/school/classes">Tất cả lớp học</Link></Button></div>
          {classesUnavailable ? <p className="school-license-unavailable" role="status">Không thể tải danh sách lớp. Hãy thử làm mới.</p> : classes.length === 0 ? <div className="school-empty-classes"><LearningIcon name="book" /><p>Trường chưa có lớp đang hoạt động.</p><Button asChild><Link to="/school/classes">Tạo lớp đầu tiên</Link></Button></div> : <div className="admin-table-scroll"><Table.Root variant="surface" size="2"><Table.Header><Table.Row><Table.ColumnHeaderCell>Lớp</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Năm học</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Giáo viên</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Học sinh</Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>{classes.slice(0, 5).map(item => <Table.Row key={item.id}><Table.RowHeaderCell><strong>{item.name}</strong><small className="school-class-subtitle">Khối {item.gradeLevel}</small></Table.RowHeaderCell><Table.Cell>{item.schoolYear}</Table.Cell><Table.Cell>{item.teachers}</Table.Cell><Table.Cell>{item.students}</Table.Cell></Table.Row>)}</Table.Body></Table.Root></div>}
        </section></Card>
      </motion.div>
    </>}
  </div>;
}
