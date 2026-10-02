import { useMemo, useState } from "react";
import type { Assignment, StudentOption, TeacherClassOption } from "../../../shared/types/physlive";
import { questionPrompt } from "../model/teacherAssignmentUtils";

type TeacherAssignmentHistoryProps = {
  workspaceLayout: boolean;
  items: Assignment[];
  classes: TeacherClassOption[];
  students: StudentOption[];
  selectedAssignmentId?: string;
  loading: boolean;
  onRefresh: () => void;
  onOpenSubmissions: (assignment: Assignment) => void;
  onToggleOpen: (assignment: Assignment) => void;
};

export function TeacherAssignmentHistory({
  workspaceLayout,
  items,
  classes,
  students,
  selectedAssignmentId,
  loading,
  onRefresh,
  onOpenSubmissions,
  onToggleOpen,
}: Readonly<TeacherAssignmentHistoryProps>) {
  const [classFilter, setClassFilter] = useState("");
  const [studentFilter, setStudentFilter] = useState("");
  const visibleStudents = useMemo(() => {
    if (!classFilter) return students;
    return classes.find(item => item.id === classFilter)?.students ?? [];
  }, [classFilter, classes, students]);
  const filteredItems = useMemo(() => items.filter(item =>
    (!classFilter || item.classId === classFilter)
    && (!studentFilter || item.studentIds.includes(Number(studentFilter)))), [classFilter, items, studentFilter]);
  const studentNames = useMemo(() => new Map(students.map(student => [student.id, student.fullName])), [students]);
  return (
    <section
      className={`modern-card assignment-history-panel${workspaceLayout ? " assignment-workspace-panel" : ""}`}
      aria-labelledby="assigned-work-title"
    >
      <div className="modern-card-header assignment-history-header">
        <div>
          <h2 id="assigned-work-title">Bài đã giao</h2>
          <p>Xem lịch sử theo lớp, học sinh và thời điểm giao.</p>
        </div>
        <button
          type="button"
          className="modern-tab-btn"
          onClick={onRefresh}
          disabled={loading}
        >
          Làm mới
        </button>
      </div>
      <div className="assignment-history-filters">
        <label><span>Lớp</span><select value={classFilter} onChange={event => { setClassFilter(event.target.value); setStudentFilter(""); }}><option value="">Tất cả lớp</option>{classes.map(item => <option key={item.id} value={item.id}>Khối {item.gradeLevel} · {item.name}</option>)}</select></label>
        <label><span>Học sinh</span><select value={studentFilter} onChange={event => setStudentFilter(event.target.value)}><option value="">Tất cả học sinh</option>{visibleStudents.map(student => <option key={student.id} value={student.id}>{student.fullName}</option>)}</select></label>
      </div>
      {loading && (
        <div className="assignment-history-list" role="status" aria-label="Đang tải danh sách bài đã giao">
          {[0, 1, 2].map(index => <div className="assignment-history-card assignment-history-skeleton" key={index}>
            <span className="skeleton skeleton-title" style={{ width: `${70 - index * 12}%` }} />
            <span className="skeleton skeleton-line" style={{ width: "45%" }} />
            <span className="skeleton skeleton-line" />
            <span className="skeleton skeleton-line" style={{ width: "30%" }} />
          </div>)}
        </div>
      )}
      {!loading && filteredItems.length === 0 && (
        <div className="assignment-history-empty">
          {items.length === 0 ? "Chưa giao bài nào. Soạn bài ở khung bên cạnh để bắt đầu." : "Không có lần giao bài phù hợp với bộ lọc."}
        </div>
      )}
      {!loading && filteredItems.length > 0 && (
        <div className="assignment-history-list">
          {filteredItems.map((item) => (
            <article className={`assignment-history-card${selectedAssignmentId === item.id ? " selected" : ""}`} key={item.id} aria-current={selectedAssignmentId === item.id ? "true" : undefined}>
              <div className="assignment-history-card-heading">
                <h3>{item.title}</h3>
                <span className={`status-pill ${item.status.toLowerCase()}`}>
                  {item.status === "ACTIVE" ? "Đang mở" : item.status === "CLOSED" ? "Đã đóng" : item.status}
                </span>
              </div>
              <div className="assignment-history-context">
                <span className="assignment-simulation-chip">◇ {item.libraryItemTitle || "Mô phỏng đã giao"}</span>
                <span>{item.className ? `Khối ${item.classGradeLevel ?? ""} · ${item.className}` : "Giao cá nhân / bài cũ"}</span>
                <time dateTime={item.assignedAt}>Giao lúc {new Date(item.assignedAt).toLocaleString("vi-VN")}</time>
              </div>
              <p className="assignment-history-prompt">
                {questionPrompt(item.questions) ||
                  item.description ||
                  "Bài tập mô phỏng"}
              </p>
              <div className="assignment-history-card-footer">
                <div className="assignment-history-meta">
                  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path d="M16 20v-1.5a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 4 18.5V20m16 0v-1.5a3.5 3.5 0 0 0-2.7-3.4M10 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm7-6.8a3.5 3.5 0 0 1 0 6.6" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                  <span>{item.studentIds.length} học sinh nhận bài</span>
                  {item.dueAt
                    ? <span className={item.status === "ACTIVE" && new Date(item.dueAt).getTime() < Date.now() ? "assignment-overdue" : ""}>Hạn nộp {new Date(item.dueAt).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" })}{item.status === "ACTIVE" && new Date(item.dueAt).getTime() < Date.now() ? " · đã quá hạn" : ""}</span>
                    : ""}
                </div>
                <button
                  type="button"
                  className="assignment-history-open-btn"
                  onClick={() => onOpenSubmissions(item)}
                >
                  <span>Xem và chấm bài nộp</span>

                  <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                    <path d="M4 10h12m-5-5 5 5-5 5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                </button>
              </div>
              <button type="button" className="assignment-history-toggle" onClick={() => onToggleOpen(item)}>
                {item.status === "ACTIVE" ? "Đóng bài (ngừng nhận bài nộp)" : "Mở lại bài"}
              </button>
              <details className="assignment-recipient-details">
                <summary>Xem danh sách học sinh</summary>
                <p>{item.studentIds.map(id => studentNames.get(id) || `Học sinh #${id}`).join(", ")}</p>
              </details>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}