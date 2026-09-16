import type { Assignment } from "../../../types/physlive";
import { questionPrompt } from "./teacherAssignmentUtils";

type TeacherAssignmentHistoryProps = {
  workspaceLayout: boolean;
  items: Assignment[];
  loading: boolean;
  onRefresh: () => void;
  onOpenSubmissions: (assignment: Assignment) => void;
};

export function TeacherAssignmentHistory({
  workspaceLayout,
  items,
  loading,
  onRefresh,
  onOpenSubmissions,
}: Readonly<TeacherAssignmentHistoryProps>) {
  return (
    <div
      className={`modern-card${workspaceLayout ? " assignment-workspace-panel" : ""}`}
    >
      <div className="modern-card-header">
        <div>
          <h2>Bài đã giao &amp; Theo dõi nộp bài</h2>
          <p>Danh sách các bài tập đã phát hành cho học sinh.</p>
        </div>
        <button
          type="button"
          className="modern-tab-btn"
          style={{
            background: "#ffffff",
            border: "1px solid var(--border-subtle)",
          }}
          onClick={onRefresh}
          disabled={loading}
        >
          {loading ? "…" : "Làm mới"}
        </button>
      </div>
      {loading && (
        <p style={{ color: "var(--text-muted)", padding: "20px" }}>
          Đang tải danh sách bài đã giao…
        </p>
      )}
      {!loading && items.length === 0 && (
        <div
          style={{
            padding: "40px 10px",
            textAlign: "center",
            color: "var(--text-muted)",
          }}
        >
          Chưa có bài tập nào được giao.
        </div>
      )}
      {!loading && items.length > 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
          {items.map((item) => (
            <div
              key={item.id}
              style={{
                border: "1px solid var(--border-subtle)",
                borderRadius: "10px",
                padding: "16px",
                background: "#ffffff",
                boxShadow: "var(--shadow-sm)",
              }}
            >
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "flex-start",
                  marginBottom: "6px",
                }}
              >
                <strong
                  style={{ fontSize: "15px", color: "var(--text-primary)" }}
                >
                  {item.title}
                </strong>
                <span className="status-pill pass">{item.status}</span>
              </div>
              <p
                style={{
                  margin: "0 0 10px 0",
                  fontSize: "13px",
                  color: "var(--text-secondary)",
                }}
              >
                {questionPrompt(item.questions) ||
                  item.description ||
                  "Bài tập mô phỏng"}
              </p>
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  fontSize: "12px",
                  color: "var(--text-muted)",
                }}
              >
                <span>
                  👥 {item.studentIds.length} học sinh nhận bài
                  {item.dueAt
                    ? ` · Hạn ${new Date(item.dueAt).toLocaleDateString("vi-VN")}`
                    : ""}
                </span>
                <button
                  type="button"
                  className="prediction-submit-btn"
                  style={{
                    padding: "5px 12px",
                    fontSize: "12px",
                    background: "var(--role-teacher)",
                  }}
                  onClick={() => onOpenSubmissions(item)}
                >
                  Xem bài nộp ({item.studentIds.length}) →
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

