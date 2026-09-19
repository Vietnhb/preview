import { useState } from "react";
import type { Assignment } from "../../../types/physlive";
import { questionPrompt } from "../teacher/teacherAssignmentUtils";
import { useAssignmentClock } from "../../../utils/useAssignmentClock";

type AssignmentListProps = {
  assignments: Assignment[];
  loading: boolean;
  error: string;
  onRefresh: () => void;
  onSelect: (item: Assignment) => void;
};
type Filter = "todo" | "submitted" | "all";
const needsWork = (item: Assignment) => !item.submissionCompleted || item.retryAllowed;

export function AssignmentList({ assignments, loading, error, onRefresh, onSelect }: Readonly<AssignmentListProps>) {
  const [filter, setFilter] = useState<Filter>("todo");
  const [search, setSearch] = useState("");
  const now = useAssignmentClock();
  const pending = assignments.filter(needsWork).length;
  const visible = assignments.filter(item =>
    (filter === "all" || (filter === "todo" ? needsWork(item) : !needsWork(item))) &&
    item.title.toLocaleLowerCase("vi").includes(search.toLocaleLowerCase("vi")),
  ).sort((a, b) => Number(Boolean(b.retryAllowed)) - Number(Boolean(a.retryAllowed)) ||
    (a.dueAt ? new Date(a.dueAt).getTime() : Infinity) - (b.dueAt ? new Date(b.dueAt).getTime() : Infinity) ||
    new Date(b.assignedAt).getTime() - new Date(a.assignedAt).getTime());

  return <section className="assignment-dashboard">
    <div className="assignment-stat-grid">
      <div><span>Cần làm</span><strong>{pending}</strong></div>
      <div><span>Đã gửi bài</span><strong>{assignments.length - pending}</strong></div>
      <div><span>Được yêu cầu làm lại</span><strong>{assignments.filter(item => item.retryAllowed).length}</strong></div>
    </div>
    <div className="assignment-toolbar">
      <div className="modern-tabs">{([["todo", "Cần làm"], ["submitted", "Đã gửi"], ["all", "Tất cả"]] as const).map(([value, label]) => <button key={value} type="button" aria-pressed={filter === value} className={`modern-tab-btn ${filter === value ? "active" : ""}`} onClick={() => setFilter(value)}>{label}</button>)}</div>
      <input aria-label="Tìm bài tập" placeholder="Tìm theo tên bài tập…" value={search} onChange={event => setSearch(event.target.value)} />
      <button className="modern-tab-btn" disabled={loading} onClick={onRefresh}>{loading ? "Đang tải…" : "Làm mới"}</button>
    </div>
    {error && <p role="alert" className="assignment-notice">{error}</p>}
    {loading ? <p role="status">Đang tải bài tập…</p> : !error && visible.length === 0 ? <div className="assignment-empty"><h2>{assignments.length === 0 ? "Chưa có bài tập được giao" : "Không có bài tập trong mục này"}</h2><p>{assignments.length === 0 ? "Bài tập từ giáo viên sẽ xuất hiện tại đây." : "Bạn có thể xem các bài đã gửi hoặc thay đổi từ khóa tìm kiếm."}</p>{filter !== "all" && <button className="modern-tab-btn" onClick={() => setFilter("all")}>Xem tất cả bài tập</button>}</div> : <div className="assignment-card-grid">{visible.map(item => {
      const overdue = Boolean(item.dueAt && new Date(item.dueAt).getTime() < now);
      const status = item.retryAllowed ? "Cần làm lại" : item.gradingStatus === "TEACHER_CONFIRMED" ? "Đã chấm" : item.submissionCompleted ? "Đã nộp · Chờ chấm" : item.predictionSubmitted ? "Đang thí nghiệm" : overdue ? "Quá hạn" : "Chưa làm";
      return <article key={item.id} className="assignment-task-card">
        <div className="assignment-task-meta"><span className={`status-pill ${item.retryAllowed || (!item.submissionCompleted && overdue) ? "fail" : item.submissionCompleted ? "pass" : "info"}`}>{status}</span><span>Thang điểm {item.maxScore ?? 10}</span></div>
        <h2>{item.title}</h2><p className="assignment-task-prompt">{questionPrompt(item.questions) || item.description || "Bài tập mô phỏng vật lý"}</p>
        <p className="assignment-task-due">{item.dueAt ? `Hạn nộp: ${new Date(item.dueAt).toLocaleString("vi-VN")}` : "Không giới hạn thời gian"}</p>
        {item.score != null && !item.retryAllowed && <p><strong>{item.score}/{item.maxScore ?? 10}</strong>{item.gradingStatus === "AI_GRADED" ? " · Điểm tự động" : " · Điểm bài tập"}</p>}
        {item.feedback && <p className="assignment-task-feedback">{item.feedback}</p>}
        <button className="prediction-submit-btn" onClick={() => onSelect(item)}>{item.retryAllowed ? "Xem góp ý & làm lại" : item.submissionCompleted ? "Xem bài đã nộp" : item.predictionSubmitted ? "Tiếp tục làm bài" : "Bắt đầu làm bài"}<span aria-hidden="true"> →</span></button>
      </article>;
    })}</div>}
  </section>;
}
