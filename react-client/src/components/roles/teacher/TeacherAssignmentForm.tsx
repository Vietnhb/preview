import { useState, type FormEvent } from "react";
import type { LibraryItem, StudentOption } from "../../../types/physlive";
import { useAssignmentClock } from "../../../utils/useAssignmentClock";

type TeacherAssignmentFormProps = {
  workspaceLayout: boolean;
  saved: LibraryItem[];
  selectedLibrary: LibraryItem | undefined;
  libraryItemId: string;
  title: string;
  description: string;
  prompt: string;
  maxScore: string;
  autoGrade: boolean;
  expectedValue: string;
  tolerance: string;
  dueAt: string;
  students: StudentOption[];
  selectedStudents: number[];
  submitting: boolean;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onLibraryChange: (id: string) => void;
  onTitleChange: (value: string) => void;
  onPromptChange: (value: string) => void;
  onMaxScoreChange: (value: string) => void;
  onAutoGradeChange: (value: boolean) => void;
  onExpectedValueChange: (value: string) => void;
  onToleranceChange: (value: string) => void;
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
  maxScore,
  autoGrade,
  expectedValue,
  tolerance,
  dueAt,
  students,
  selectedStudents,
  submitting,
  onSubmit,
  onLibraryChange,
  onTitleChange,
  onPromptChange,
  onMaxScoreChange,
  onAutoGradeChange,
  onExpectedValueChange,
  onToleranceChange,
  onDueAtChange,
  onDescriptionChange,
  onToggleStudent,
  onSelectAll,
}: Readonly<TeacherAssignmentFormProps>) {
  const [step, setStep] = useState(0);
  const [search, setSearch] = useState("");
  const now = useAssignmentClock();
  const steps = ["Nội dung bài tập", "Học sinh & hạn nộp", "Kiểm tra & giao bài"];
  const contentValid = Boolean(selectedLibrary && title.trim() && title.trim().length <= 160 && prompt.trim()) && Number(maxScore) >= 0.001 && Number(maxScore) <= 99999.999 && Number.isFinite(Number(maxScore)) && (!autoGrade || (expectedValue.trim() !== "" && Number.isFinite(Number(expectedValue)) && tolerance.trim() !== "" && Number.isFinite(Number(tolerance)) && Number(tolerance) >= 0));
  const recipientsValid = selectedStudents.length > 0 && (!dueAt || new Date(dueAt).getTime() > now);
  return (
    <div
      className={`modern-card${workspaceLayout ? " assignment-workspace-panel" : ""}`}
    >
      <div className="modern-card-header">
        <div>
          <h2>Thiết kế bài tập</h2>
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
            Chưa có mô phỏng đã kiểm định để giao bài.
          </p>
          <small>
            Hãy hoàn thành một mô phỏng tại Workspace và chọn &quot;Lưu vào thư
            viện&quot; trước.
          </small>
        </div>
      ) : (
        <form noValidate onSubmit={event => { if (step < 2) { event.preventDefault(); if (step === 0 ? contentValid : recipientsValid) setStep(step + 1); } else if (contentValid && recipientsValid) onSubmit(event); else { event.preventDefault(); setStep(contentValid ? 1 : 0); } }}>
          <ol className="assignment-steps">{steps.map((label, index) => <li key={label} aria-current={index === step ? "step" : undefined} className={index <= step ? "is-current" : ""}><span>{index + 1}</span>{label}</li>)}</ol>
          <fieldset className="assignment-step-fields" disabled={submitting} hidden={step !== 0}>
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
              <span className="status-pill pass">Sẵn sàng giao bài</span>
            </div>
          )}
          <div className="form-group" style={{ marginBottom: "14px" }}>
            <label htmlFor="assignment-title">Tiêu đề bài giao *</label>
            <input
              id="assignment-title"
              maxLength={160}
              required
              placeholder="Ví dụ: Bài tập Ném ngang - Dự đoán vận tốc chạm đất"
              value={title}
              onChange={(event) => onTitleChange(event.target.value)}
            />
          </div>
          <div className="form-group" style={{ marginBottom: "14px" }}>
            <label htmlFor="assignment-prompt">
              Câu hỏi dành cho học sinh *
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
            <div className="form-group"><label htmlFor="assignment-max-score">Điểm tối đa</label><input id="assignment-max-score" type="number" min="0.001" step="0.001" value={maxScore} onChange={event => onMaxScoreChange(event.target.value)} /></div>
            <label style={{ display: "flex", alignItems: "center", gap: "8px", marginTop: "24px" }}><input type="checkbox" checked={autoGrade} onChange={event => onAutoGradeChange(event.target.checked)} /> Chấm tự động theo đáp án số</label>
          </div>
          {autoGrade && <div className="form-row" style={{ marginBottom: "14px" }}><div className="form-group"><label htmlFor="assignment-expected-value">Đáp án số</label><input id="assignment-expected-value" type="number" step="any" required={autoGrade} value={expectedValue} onChange={event => onExpectedValueChange(event.target.value)} /></div><div className="form-group"><label htmlFor="assignment-tolerance">Sai số cho phép</label><input id="assignment-tolerance" type="number" min="0" step="any" required={autoGrade} value={tolerance} onChange={event => onToleranceChange(event.target.value)} /></div></div>}
          </fieldset>
          <fieldset className="assignment-step-fields" disabled={submitting} hidden={step !== 1}>
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
          <div className="form-group"><label htmlFor="student-search">Tìm học sinh</label><input id="student-search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Nhập tên học sinh…" /></div>
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
                students.filter(student => student.fullName.toLocaleLowerCase("vi").includes(search.toLocaleLowerCase("vi"))).map((student) => (
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
          </fieldset>
          {step === 2 && <section className="assignment-review">
            <span className="assignment-eyebrow">BẢN XEM TRƯỚC</span><h3>{title}</h3>
            <p className="assignment-review-prompt">{prompt}</p>{description && <p>{description}</p>}
            <dl><div><dt>Mô phỏng</dt><dd>{selectedLibrary?.title}</dd></div><div><dt>Người nhận</dt><dd>{selectedStudents.length} học sinh</dd></div><div><dt>Hạn nộp</dt><dd>{dueAt ? new Date(dueAt).toLocaleString("vi-VN") : "Không giới hạn"}</dd></div><div><dt>Chấm điểm</dt><dd>{autoGrade ? "Tự động theo đáp án số" : "Giáo viên chấm"} · Thang {maxScore}</dd></div></dl>
            <p><strong>Học sinh nhận bài:</strong> {students.filter(student => selectedStudents.includes(student.id)).map(student => student.fullName).join(", ")}</p>
            {autoGrade && <p><strong>Đáp án chấm:</strong> {expectedValue} · Sai số cho phép: {tolerance}. Đáp án này chỉ hiển thị cho giáo viên.</p>}
            <p>Học sinh đọc đề, gửi dự đoán và lập luận, sau đó chạy mô phỏng để đối chiếu kết quả.</p>
          </section>}
          {step === 1 && dueAt && new Date(dueAt).getTime() <= now && <p role="alert">Hãy chọn hạn nộp trong tương lai.</p>}
          {step === 0 && !contentValid && <p className="assignment-field-hint">Chọn mô phỏng, nhập tiêu đề, câu hỏi và thang điểm từ 0.001 đến 99999.999. Nếu chấm tự động, cần có đáp án số và sai số không âm.</p>}
          {step === 1 && selectedStudents.length === 0 && <p className="assignment-field-hint">Chọn ít nhất một học sinh để tiếp tục.</p>}
          <div className="assignment-form-actions">
          {step > 0 && <button type="button" className="modern-tab-btn" disabled={submitting} onClick={() => setStep(step - 1)}>← Quay lại</button>}
          {step < 2 && <button type="button" className="prediction-submit-btn" disabled={step === 0 ? !contentValid : !recipientsValid} onClick={() => setStep(step + 1)}>Tiếp tục →</button>}
          <button hidden={step !== 2}
            type="submit"
            className="prediction-submit-btn"
            disabled={
              submitting ||
              !contentValid || !recipientsValid ||
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
          </div>
        </form>
      )}
    </div>
  );
}
