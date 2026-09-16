import type { Assignment } from "../../../types/physlive";
import LearningIcon from "../../common/LearningIcon";

type AssignmentListProps = {
  assignments: Assignment[];
  loading: boolean;
  error: string;
  onRefresh: () => void;
  onSelect: (item: Assignment) => void;
};

export function AssignmentList({
  assignments,
  loading,
  error,
  onRefresh,
  onSelect,
}: Readonly<AssignmentListProps>) {
  return (
    <div className="modern-card">
      <div className="modern-card-header">
        <div>
          <h2>Danh sách bài tập cần hoàn thành</h2>
          <p>
            Chọn một bài tập để bắt đầu dự đoán hiện tượng và mở khóa kết quả mô
            phỏng.
          </p>
        </div>
        <button
          type="button"
          className="modern-tab-btn"
          style={{
            border: "1px solid var(--border-subtle)",
            background: "#ffffff",
          }}
          onClick={onRefresh}
          disabled={loading}
        >
          {loading ? "Đang tải…" : "Làm mới"}
        </button>
      </div>
      {loading && (
        <p style={{ color: "var(--text-muted)", padding: "20px" }}>
          Đang tải danh sách bài tập từ giáo viên…
        </p>
      )}
      {error && (
        <div className="status-pill fail" style={{ margin: "16px" }}>
          {error}
        </div>
      )}
      {!loading && assignments.length === 0 ? (
        <div
          style={{
            padding: "40px 20px",
            textAlign: "center",
            color: "var(--text-muted)",
          }}
        >
          <LearningIcon name="book" />
          <h3 style={{ margin: "0 0 8px 0", color: "var(--text-primary)" }}>
            Chưa có bài tập nào được giao
          </h3>
          <p style={{ margin: 0, fontSize: "14px" }}>
            Khi giáo viên giao bài mô phỏng, bài tập sẽ xuất hiện tại đây.
          </p>
        </div>
      ) : (
        <div className="modern-table-wrapper">
          <table className="modern-table">
            <thead>
              <tr>
                <th>Tên bài tập</th>
                <th>Nội dung / Câu hỏi dự đoán</th>
                <th>Hạn hoàn thành</th>
                <th>Trạng thái</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {assignments.map((item) => (
                <tr key={item.id}>
                  <td>
                    <strong>{item.title}</strong>
                  </td>
                  <td style={{ maxWidth: "380px" }}>
                    <span
                      style={{
                        color: "var(--text-secondary)",
                        fontSize: "13px",
                      }}
                    >
                      {typeof item.questions === "object" &&
                      item.questions &&
                      "prompt" in item.questions
                        ? String((item.questions as { prompt: string }).prompt)
                        : item.description || "Dự đoán hiện tượng"}
                    </span>
                  </td>
                  <td>
                    <small style={{ color: "var(--text-muted)" }}>
                      {item.dueAt
                        ? new Date(item.dueAt).toLocaleString("vi-VN")
                        : "Không giới hạn"}
                    </small>
                  </td>
                  <td>
                    <span
                      className={
                        item.predictionSubmitted
                          ? "status-pill completed"
                          : "status-pill draft"
                      }
                    >
                      {item.predictionSubmitted ? "Đã dự đoán" : "Chưa làm"}
                    </span>
                  </td>
                  <td>
                    <button
                      type="button"
                      className="prediction-submit-btn"
                      style={{ padding: "6px 14px", fontSize: "13px" }}
                      onClick={() => onSelect(item)}
                    >
                      {item.predictionSubmitted
                        ? "Mở mô phỏng"
                        : "Bắt đầu dự đoán"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

