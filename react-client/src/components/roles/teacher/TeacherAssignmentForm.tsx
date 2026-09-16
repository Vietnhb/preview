import type { FormEvent } from "react";
import type { LibraryItem, StudentOption } from "../../../types/physlive";

type TeacherAssignmentFormProps = {
  workspaceLayout: boolean;
  saved: LibraryItem[];
  selectedLibrary: LibraryItem | undefined;
  libraryItemId: string;
  title: string;
  description: string;
  prompt: string;
  dueAt: string;
  students: StudentOption[];
  selectedStudents: number[];
  submitting: boolean;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onLibraryChange: (id: string) => void;
  onTitleChange: (value: string) => void;
  onPromptChange: (value: string) => void;
  onDueAtChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onToggleStudent: (id: number) => void;
  onSelectAll: () => void;
};

export function TeacherAssignmentForm({
  workspaceLayout,
  saved,
  selectedLibrary,
  libraryItemId,
  title,
  description,
  prompt,
  dueAt,
  students,
  selectedStudents,
  submitting,
  onSubmit,
  onLibraryChange,
  onTitleChange,
  onPromptChange,
  onDueAtChange,
  onDescriptionChange,
  onToggleStudent,
  onSelectAll,
}: Readonly<TeacherAssignmentFormProps>) {
  return (
    <div
      className={`modern-card${workspaceLayout ? " assignment-workspace-panel" : ""}`}
    >
      <div className="modern-card-header">
        <div>
          <h2>Giao bài mới</h2>
          <p>Chọn mô phỏng từ thư viện của bạn và chỉ định học sinh.</p>
        </div>
      </div>
      {saved.length === 0 ? (
        <div
          style={{
            padding: "30px 10px",
            textAlign: "center",
            color: "var(--text-muted)",
          }}
        >
          <p style={{ margin: "0 0 10px 0" }}>
            Bạn chưa có mô phỏng nào trong thư viện cá nhân.
          </p>
          <small>
            Hãy hoàn thành một mô phỏng tại Workspace và chọn &quot;Lưu vào thư
            viện&quot; trước.
          </small>
        </div>
      ) : (
        <form onSubmit={onSubmit}>
          <div className="form-group" style={{ marginBottom: "14px" }}>
            <label htmlFor="assignment-library-item">
              Mô phỏng trong thư viện cá nhân *
            </label>
            <select
              id="assignment-library-item"
              required
              value={libraryItemId}
              onChange={(event) => onLibraryChange(event.target.value)}
            >
              <option value="">-- Chọn mô phỏng đã kiểm định --</option>
              {saved.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.title} ({item.topic || "Vật lý"})
                </option>
              ))}
            </select>
          </div>
          {selectedLibrary && (
            <div
              style={{
                background: "#f8fafc",
                padding: "10px 12px",
                borderRadius: "8px",
                border: "1px solid var(--border-subtle)",
                marginBottom: "14px",
                fontSize: "12.5px",
              }}
            >
              Đang giao: <strong>{selectedLibrary.title}</strong> · Chủ đề:{" "}
              {selectedLibrary.topic || "Chung"} · Trạng thái:{" "}
              <span className="status-pill pass">Dual Validation Passed</span>
            </div>
          )}
          <div className="form-group" style={{ marginBottom: "14px" }}>
            <label htmlFor="assignment-title">Tiêu đề bài giao *</label>
            <input
              id="assignment-title"
              required
              placeholder="Ví dụ: Bài tập Ném ngang - Dự đoán vận tốc chạm đất"
              value={title}
              onChange={(event) => onTitleChange(event.target.value)}
            />
          </div>
          <div className="form-group" style={{ marginBottom: "14px" }}>
            <label htmlFor="assignment-prompt">
              Câu hỏi dự đoán cho học sinh (Prediction Gate) *
            </label>
            <textarea
              id="assignment-prompt"
              rows={2}
              required
              placeholder="Ví dụ: Theo em, khi góc bắn tăng từ 30° lên 45° thì tầm xa cực đại tăng hay giảm? Tại sao?"
              value={prompt}
              onChange={(event) => onPromptChange(event.target.value)}
            />
            <small style={{ color: "var(--text-muted)", fontSize: "11.5px" }}>
              Học sinh bắt buộc phải gửi câu trả lời cho câu hỏi này trước khi
              mô phỏng mở khóa kết quả.
            </small>
          </div>
          <div className="form-row" style={{ marginBottom: "14px" }}>
            <div className="form-group">
              <label htmlFor="assignment-due-at">Hạn hoàn thành</label>
              <input
                id="assignment-due-at"
                type="datetime-local"
                value={dueAt}
                onChange={(event) => onDueAtChange(event.target.value)}
              />
            </div>
            <div className="form-group">
              <label htmlFor="assignment-description">
                Ghi chú hướng dẫn thêm
              </label>
              <input
                id="assignment-description"
                placeholder="Không bắt buộc"
                value={description}
                onChange={(event) => onDescriptionChange(event.target.value)}
              />
            </div>
          </div>
          <div className="form-group" style={{ marginBottom: "18px" }}>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                marginBottom: "6px",
              }}
            >
              <span className="form-label">
                Học sinh nhận bài ({selectedStudents.length}/{students.length})
                *
              </span>
              {students.length > 0 && (
                <button
                  type="button"
                  className="role-switch-pill"
                  style={{ border: "1px solid var(--border-subtle)" }}
                  onClick={onSelectAll}
                >
                  {selectedStudents.length === students.length
                    ? "Bỏ chọn tất cả"
                    : "Chọn tất cả"}
                </button>
              )}
            </div>
            <div
              style={{
                maxHeight: "150px",
                overflowY: "auto",
                border: "1px solid var(--border-strong)",
                borderRadius: "8px",
                padding: "10px",
                background: "#ffffff",
              }}
            >
              {students.length === 0 ? (
                <small style={{ color: "var(--text-muted)" }}>
                  Chưa có tài khoản học sinh hoạt động.
                </small>
              ) : (
                students.map((student) => (
                  <label
                    key={student.id}
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "8px",
                      padding: "4px 0",
                      cursor: "pointer",
                      fontSize: "13px",
                    }}
                  >
                    <input
                      type="checkbox"
                      checked={selectedStudents.includes(student.id)}
                      onChange={() => onToggleStudent(student.id)}
                    />
                    <span>{student.fullName}</span>
                  </label>
                ))
              )}
            </div>
          </div>
          <button
            type="submit"
            className="prediction-submit-btn"
            disabled={
              submitting ||
              !libraryItemId ||
              !title.trim() ||
              !prompt.trim() ||
              selectedStudents.length === 0
            }
            style={{ width: "100%", justifyContent: "center" }}
          >
            {submitting
              ? "Đang giao bài…"
              : `Giao bài cho ${selectedStudents.length} học sinh`}
          </button>
        </form>
      )}
    </div>
  );
}

