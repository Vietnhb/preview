import type { Assignment, AssignmentSubmission } from "../../../types/physlive";
import { questionPrompt } from "./teacherAssignmentUtils";

type SubmissionViewerProps = {
  assignment: Assignment;
  submissions: AssignmentSubmission[];
  loading: boolean;
  error: string;
  onClose: () => void;
};

export function TeacherSubmissionViewer({
  assignment,
  submissions,
  loading,
  error,
  onClose,
}: Readonly<SubmissionViewerProps>) {
  return (
    <dialog
      open
      className="modern-modal-overlay"
      onPointerDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className="modern-modal-content" style={{ maxWidth: "760px" }}>
        <div className="modern-modal-header">
          <div>
            <span className="status-pill info" style={{ marginBottom: "4px" }}>
              Báo cáo nộp bài
            </span>
            <h3 style={{ marginTop: "4px" }}>{assignment.title}</h3>
            <small style={{ color: "var(--text-muted)" }}>
              Câu hỏi dự đoán: &quot;{questionPrompt(assignment.questions)}
              &quot;
            </small>
          </div>
          <button
            type="button"
            className="modern-modal-close"
            onClick={onClose}
          >
            ✕
          </button>
        </div>
        {loading && (
          <p style={{ color: "var(--text-muted)", padding: "20px" }}>
            Đang tải danh sách câu trả lời của học sinh…
          </p>
        )}
        {!loading && error !== "" && (
          <div className="status-pill fail">{error}</div>
        )}
        {!loading && error === "" && submissions.length === 0 && (
          <div
            style={{
              padding: "40px 20px",
              textAlign: "center",
              color: "var(--text-muted)",
            }}
          >
            <p style={{ fontSize: "24px", margin: "0 0 8px 0" }}>⏳</p>
            <strong>Chưa có học sinh nào nộp dự đoán</strong>
            <p style={{ margin: "4px 0 0 0", fontSize: "13px" }}>
              Khi học sinh mở bài và xác nhận dự đoán tại Prediction Gate, câu
              trả lời sẽ xuất hiện tại đây ngay lập tức.
            </p>
          </div>
        )}
        {!loading && error === "" && submissions.length > 0 && (
          <div className="modern-table-wrapper">
            <table className="modern-table">
              <thead>
                <tr>
                  <th>Học sinh</th>
                  <th>Câu trả lời dự đoán</th>
                  <th>Thời gian nộp</th>
                  <th>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {submissions.map((sub) => (
                  <tr key={sub.id}>
                    <td>
                      <strong>{sub.studentName}</strong>
                      <small
                        style={{ display: "block", color: "var(--text-muted)" }}
                      >
                        ID: {sub.studentId}
                      </small>
                    </td>
                    <td style={{ maxWidth: "320px" }}>
                      <div
                        style={{
                          background: "#f8fafc",
                          padding: "8px 10px",
                          borderRadius: "6px",
                          fontSize: "13px",
                        }}
                      >
                        {typeof sub.predictions === "object" &&
                        sub.predictions !== null
                          ? JSON.stringify(sub.predictions)
                          : String(sub.predictions)}
                      </div>
                    </td>
                    <td>
                      <small style={{ color: "var(--text-muted)" }}>
                        {new Date(sub.submittedAt).toLocaleString("vi-VN")}
                      </small>
                    </td>
                    <td>
                      <span className="status-pill pass">Đã nộp</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div style={{ marginTop: "20px", textAlign: "right" }}>
          <button
            type="button"
            className="role-switch-pill"
            style={{
              border: "1px solid var(--border-subtle)",
              padding: "8px 18px",
              fontSize: "13px",
            }}
            onClick={onClose}
          >
            Đóng
          </button>
        </div>
      </div>
    </dialog>
  );
}

