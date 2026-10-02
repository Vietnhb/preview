import { useCallback, useEffect, useState } from "react";
import { Badge, Button, Card, Spinner, Table } from "@radix-ui/themes";
import { motion, useReducedMotion } from "motion/react";
import { downloadSchoolClassesCsv, schoolReportClasses, schoolReportSummary, schoolReportTokenAudit, type SchoolReportClass, type SchoolReportSummary, type SchoolTokenAudit } from "../api/schoolApi";
import { apiMessage } from "../../../shared/lib/apiError";
import LearningIcon from "../../../shared/ui/LearningIcon";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import s from "./SchoolReports.module.css";
import SchoolBulkImport from "../dataio/components/SchoolBulkImport";

function ReportSummaryCard({ label, value, detail, icon, tone }: Readonly<{ label: string; value: string; detail: string; icon: "users" | "book" | "activity" | "chart"; tone: string }>) {
  const reducedMotion = useReducedMotion();
  return <Card size="3" asChild><motion.article className="admin-stat-card" initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: reducedMotion ? 0 : .18 }}><div className={`admin-stat-icon ${tone}`}><LearningIcon name={icon} /></div><p>{label}</p><strong>{value}</strong><small>{detail}</small></motion.article></Card>;
}

export default function SchoolReports() {
  const reducedMotion = useReducedMotion();
  const schoolId = useSessionStore((state) => state.user?.schoolId);
  const [summary, setSummary] = useState<SchoolReportSummary | null>(null);
  const [classes, setClasses] = useState<SchoolReportClass[]>([]);
  const [tokens, setTokens] = useState<SchoolTokenAudit[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);

  const load = useCallback(async () => {
    if (!schoolId) return;
    setLoading(true);
    setError("");
    try {
      const [nextSummary, nextClasses, nextTokens] = await Promise.all([
        schoolReportSummary(schoolId),
        schoolReportClasses(schoolId),
        schoolReportTokenAudit(schoolId),
      ]);
      setSummary(nextSummary);
      setClasses(nextClasses);
      setTokens(nextTokens);
    } catch (err) {
      setError(apiMessage(err, "Không thể tải báo cáo trường."));
    } finally {
      setLoading(false);
    }
  }, [schoolId]);

  useEffect(() => {
    const timer = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const exportClasses = async () => {
    if (!schoolId || exporting) return;
    setExporting(true);
    setError("");
    try {
      await downloadSchoolClassesCsv(schoolId);
    } catch (err) {
      setError(apiMessage(err, "Không thể xuất danh sách lớp."));
    } finally {
      setExporting(false);
    }
  };

  if (!schoolId) return null;

  return <div className={`admin-content school-reports ${s.reports}`}>
    <header className="admin-content-header"><div><h1 className="admin-content-title">Báo cáo</h1></div></header>
    {error && <div className="admin-error-banner" role="alert">{error}<Button variant="soft" color="red" onClick={() => void load()}>Thử lại</Button></div>}
    {loading && !summary ? <div className="admin-loading"><Spinner size="3" /><p>Đang tải báo cáo…</p></div> : summary && <>
      <section className={`admin-stats-grid school-summary-grid ${s.summary}`} aria-label="Tổng quan trường">
        <ReportSummaryCard label="Học sinh" value={summary.students.toLocaleString("vi-VN")} detail={`${summary.enrolledStudents.toLocaleString("vi-VN")} lượt xếp lớp`} icon="users" tone="blue" />
        <ReportSummaryCard label="Giáo viên" value={summary.teachers.toLocaleString("vi-VN")} detail="Tài khoản thuộc trường" icon="book" tone="purple" />
        <ReportSummaryCard label="Lớp hoạt động" value={summary.activeClasses.toLocaleString("vi-VN")} detail="Đang được sử dụng" icon="activity" tone="green" />
        <ReportSummaryCard label="Token đã dùng" value={summary.usedTokens.toLocaleString("vi-VN")} detail={summary.tokenQuota == null ? "Không giới hạn" : `Quota ${summary.tokenQuota.toLocaleString("vi-VN")}/tháng`} icon="chart" tone="orange" />
      </section>

      <motion.div style={{ display: "grid", gap: 20, minWidth: 0 }} initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: reducedMotion ? 0 : .18 }}>
      <Card size="3" asChild><section className={`admin-panel ${s.importPanel}`}><div className="admin-panel-heading"><h2>Nhập dữ liệu CSV</h2></div><div className="school-bulk-tools"><SchoolBulkImport schoolId={schoolId} kind="USERS" label="Tài khoản" onImported={load} /><SchoolBulkImport schoolId={schoolId} kind="CLASSES" label="Lớp học" onImported={load} /><SchoolBulkImport schoolId={schoolId} kind="ENROLLMENTS" label="Xếp lớp học sinh" onImported={load} /><SchoolBulkImport schoolId={schoolId} kind="TEACHER_ASSIGNMENTS" label="Phân công giáo viên" onImported={load} /></div></section></Card>

      <Card size="3" asChild><section className={`admin-panel ${s.dataPanel}`}>
        <div className="admin-panel-heading"><div><h2>Danh sách lớp</h2></div><Button variant="soft" onClick={() => void exportClasses()} loading={exporting} disabled={exporting || loading}><LearningIcon name="download" />{exporting ? "Đang xuất…" : "Xuất CSV"}</Button></div>
        <div className="admin-table-scroll"><Table.Root size="2" variant="surface"><Table.Header><Table.Row><Table.ColumnHeaderCell>Lớp</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Khối</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Năm học</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Giáo viên</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Học sinh</Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>{classes.length === 0 ? <Table.Row><Table.Cell colSpan={5}>Chưa có lớp đang hoạt động.</Table.Cell></Table.Row> : classes.map(item => <Table.Row key={item.id}><Table.RowHeaderCell>{item.name}</Table.RowHeaderCell><Table.Cell><Badge color="indigo" variant="soft">{item.gradeLevel}</Badge></Table.Cell><Table.Cell>{item.schoolYear}</Table.Cell><Table.Cell>{item.teachers}</Table.Cell><Table.Cell>{item.students}</Table.Cell></Table.Row>)}</Table.Body></Table.Root></div>
      </section></Card>

      <Card size="3" asChild><section className={`admin-panel ${s.dataPanel}`}>
        <div className="admin-panel-heading"><div><h2>Lịch sử token AI</h2><p className="admin-panel-description">Tối đa 200 lượt sử dụng gần nhất của trường.</p></div></div>
        <div className="admin-table-scroll"><Table.Root size="2" variant="surface"><Table.Header><Table.Row><Table.ColumnHeaderCell>Thời điểm</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Tài khoản</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Hoạt động</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Tháng</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Token</Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>{tokens.length === 0 ? <Table.Row><Table.Cell colSpan={5}>Chưa có dữ liệu sử dụng.</Table.Cell></Table.Row> : tokens.map((item, index) => <Table.Row key={`${item.recordedAt}-${index}`}><Table.Cell>{new Date(item.recordedAt).toLocaleString("vi-VN")}</Table.Cell><Table.Cell>{item.userEmail}</Table.Cell><Table.Cell>{item.operation}</Table.Cell><Table.Cell>{item.usageMonth}</Table.Cell><Table.Cell>{item.tokens.toLocaleString("vi-VN")}</Table.Cell></Table.Row>)}</Table.Body></Table.Root></div>
      </section></Card>
      </motion.div>
    </>}
  </div>;
}