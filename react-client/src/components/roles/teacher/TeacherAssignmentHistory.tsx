import type { Assignment } from "../../../types/physlive";
import { questionPrompt } from "./teacherAssignmentUtils";

type TeacherAssignmentHistoryProps = {
  workspaceLayout: boolean;
  items: Assignment[];
  selectedAssignmentId?: string;
  loading: boolean;
  onRefresh: () => void;
  onOpenSubmissions: (assignment: Assignment) => void;
};

export function TeacherAssignmentHistory({
  workspaceLayout,
  items,
  selectedAssignmentId,
  loading,
  onRefresh,
  onOpenSubmissions,
}: Readonly<TeacherAssignmentHistoryProps>) {
  return (
    <section
      className={`modern-card assignment-history-panel${workspaceLayout ? " assignment-workspace-panel" : ""}`}
      aria-labelledby="assigned-work-title"
    >
      <div className="modern-card-header assignment-history-header">
        <div>
          <h2 id="assigned-work-title">Bài đã giao</h2>
          <p>Theo dõi hạn nộp và bài học sinh đã gửi.</p>
        </div>
        <button
          type="button"
          className="modern-tab-btn"
          onClick={onRefresh}
          disabled={loading}
        >
          {loading ? "Đang tải…" : "Làm mới"}
        </button>
      </div>
      {loading && (
        <p className="assignment-history-message" role="status">Đang tải danh sách bài đã giao…</p>
      )}
      {!loading && items.length === 0 && (
        <div className="assignment-history-empty">
          Chưa có bài tập nào được giao.
        </div>
      )}
      {!loading && items.length > 0 && (
        <div className="assignment-history-list">
          {items.map((item) => (
            <article className={`assignment-history-card${selectedAssignmentId === item.id ? " selected" : ""}`} key={item.id} aria-current={selectedAssignmentId === item.id ? "true" : undefined}>
              <div className="assignment-history-card-heading">
                <h3>{item.title}</h3>
                <span className={`status-pill ${item.status.toLowerCase()}`}>
                  {item.status === "ACTIVE" ? "Đang mở" : item.status === "CLOSED" ? "Đã đóng" : item.status}
                </span>
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
                    ? <span>Hạn nộp {new Date(item.dueAt).toLocaleDateString("vi-VN")}</span>
                    : ""}
                </div>
                <button
                  type="button"
                  className="assignment-history-open-btn"
                  onClick={() => onOpenSubmissions(item)}
                >
                  <span>Xem bài nộp</span>

                  <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                    <path d="M4 10h12m-5-5 5 5-5 5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
