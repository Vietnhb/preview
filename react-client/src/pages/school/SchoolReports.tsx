import { useCallback, useEffect, useState, type ChangeEvent } from "react";
import {
  downloadSchoolClassesCsv,
  importSchoolUsers,
  schoolReportClasses,
  schoolReportSummary,
  schoolReportTokenAudit,
  type SchoolImportResult,
  type SchoolReportClass,
  type SchoolReportSummary,
  type SchoolTokenAudit,
} from "../../api/schoolApi";
import { apiMessage } from "../../components/roles/admin/adminUtils";
import LearningIcon from "../../components/common/LearningIcon";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import s from "./SchoolReports.module.css";

const classCsvColumns = "email,fullname,role,password";

export default function SchoolReports() {
  const schoolId = usePhysliveStore((state) => state.user?.schoolId);
  const [summary, setSummary] = useState<SchoolReportSummary | null>(null);
  const [classes, setClasses] = useState<SchoolReportClass[]>([]);
  const [tokens, setTokens] = useState<SchoolTokenAudit[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [importing, setImporting] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [result, setResult] = useState<SchoolImportResult | null>(null);

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

  const importCsv = async (file: File) => {
    if (!schoolId || importing) return;
    setImporting(true);
    setError("");
    setResult(null);
    try {
      setResult(await importSchoolUsers(schoolId, file));
      await load();
    } catch (err) {
      setError(apiMessage(err, "Không thể nhập CSV."));
    } finally {
      setImporting(false);
    }
  };

  const handleImportChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.currentTarget.files?.[0];
    event.currentTarget.value = "";
    if (file) void importCsv(file);
  };

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
    {error && <div className="admin-error-banner" role="alert">{error}<button type="button" className="admin-inline-button" onClick={() => void load()}>Thử lại</button></div>}
    {loading && !summary ? <div className="admin-loading"><span className="admin-loading-spinner" /><p>Đang tải báo cáo…</p></div> : summary && <>
      <section className={`admin-stats-grid school-summary-grid ${s.summary}`} aria-label="Tổng quan trường">
        <article className="admin-stat-card"><p>Học sinh</p><strong>{summary.students.toLocaleString("vi-VN")}</strong><small>{summary.enrolledStudents.toLocaleString("vi-VN")} lượt xếp lớp</small></article>
        <article className="admin-stat-card"><p>Giáo viên</p><strong>{summary.teachers.toLocaleString("vi-VN")}</strong><small>Tài khoản thuộc trường</small></article>
        <article className="admin-stat-card"><p>Lớp hoạt động</p><strong>{summary.activeClasses.toLocaleString("vi-VN")}</strong><small>Đang được sử dụng</small></article>
        <article className="admin-stat-card"><p>Token đã dùng</p><strong>{summary.usedTokens.toLocaleString("vi-VN")}</strong><small>{summary.tokenQuota == null ? "Không giới hạn" : `Quota ${summary.tokenQuota.toLocaleString("vi-VN")}/tháng`}</small></article>
      </section>

      <section className={`admin-panel school-import-panel ${s.importPanel}`}>
        <div className="admin-panel-heading"><div><h2>Nhập tài khoản bằng CSV</h2><p className="admin-panel-description">Tạo giáo viên hoặc học sinh. Mỗi dòng phải có email, họ tên, vai trò và mật khẩu.</p></div>
          <label className={`admin-secondary-button school-import-button${importing ? " disabled" : ""}`}>
            <LearningIcon name="upload" />{importing ? "Đang nhập…" : "Chọn tệp CSV"}
            <input type="file" accept=".csv,text/csv" disabled={importing} onChange={handleImportChange} />
          </label>
        </div>
        <p className={`school-csv-format ${s.formatNote}`}>Dòng tiêu đề: <code>{classCsvColumns}</code>. Vai trò nhận <code>TEACHER</code> hoặc <code>STUDENT</code>.</p>
        {result && <div className={`school-import-result${result.failed ? " has-failures" : ""}`} role="status">
          <strong>Đã xử lý {result.total} dòng: {result.imported} thành công, {result.failed} lỗi.</strong>
          {result.rows.length > 0 && <details><summary>Xem kết quả từng dòng</summary><div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Dòng</th><th>Email</th><th>Kết quả</th><th>Thông tin</th></tr></thead><tbody>{result.rows.map(row => <tr key={`${row.row}-${row.email}`}><td>{row.row}</td><td>{row.email || "—"}</td><td>{row.status === "IMPORTED" ? "Đã tạo" : "Lỗi"}</td><td>{row.message}</td></tr>)}</tbody></table></div></details>}
        </div>}
      </section>

      <section className={`admin-panel ${s.dataPanel}`}>
        <div className="admin-panel-heading"><div><h2>Danh sách lớp</h2><p className="admin-panel-description">Lớp, giáo viên phụ trách và số học sinh đã xếp lớp.</p></div><button type="button" className="admin-secondary-button" onClick={() => void exportClasses()} disabled={exporting || loading}><LearningIcon name="download" />{exporting ? "Đang xuất…" : "Xuất CSV"}</button></div>
        <div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Lớp</th><th>Khối</th><th>Năm học</th><th>Giáo viên</th><th>Học sinh</th></tr></thead><tbody>{classes.length === 0 ? <tr><td colSpan={5}>Chưa có lớp đang hoạt động.</td></tr> : classes.map(item => <tr key={item.id}><td><strong>{item.name}</strong></td><td>{item.gradeLevel}</td><td>{item.schoolYear}</td><td>{item.teachers}</td><td>{item.students}</td></tr>)}</tbody></table></div>
      </section>

      <section className={`admin-panel ${s.dataPanel}`}>
        <div className="admin-panel-heading"><div><h2>Lịch sử token AI</h2><p className="admin-panel-description">Tối đa 200 lượt sử dụng gần nhất của trường.</p></div></div>
        <div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Thời điểm</th><th>Tài khoản</th><th>Hoạt động</th><th>Tháng</th><th>Token</th></tr></thead><tbody>{tokens.length === 0 ? <tr><td colSpan={5}>Chưa có dữ liệu sử dụng.</td></tr> : tokens.map((item, index) => <tr key={`${item.recordedAt}-${index}`}><td>{new Date(item.recordedAt).toLocaleString("vi-VN")}</td><td>{item.userEmail}</td><td>{item.operation}</td><td>{item.usageMonth}</td><td>{item.tokens.toLocaleString("vi-VN")}</td></tr>)}</tbody></table></div>
      </section>
    </>}
  </div>;
}
