import { useState, type FormEvent } from "react";
import type { AssignmentActivityType, LibraryItem, Simulation, StudentOption, TeacherClassOption } from "../../../types/physlive";
import { useAssignmentClock } from "../../../utils/useAssignmentClock";

type TeacherAssignmentFormProps = {
  workspaceLayout: boolean;
  saved: LibraryItem[];
  selectedLibrary: LibraryItem | undefined;
  libraryItemId: string;
  title: string;
  description: string;
  prompt: string;
  activityType: AssignmentActivityType;
  simulation: Simulation | null;
  simulationOptionsLoading: boolean;
  targetSeriesSource: string;
  sampleTime: string;
  measurementTolerance: string;
  investigationParameter: string;
  investigationOutcome: string;
  maxScore: string;
  dueAt: string;
  students: StudentOption[];
  classes: TeacherClassOption[];
  selectedClassId: string;
  selectedStudents: number[];
  submitting: boolean;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onLibraryChange: (id: string) => void;
  onTitleChange: (value: string) => void;
  onPromptChange: (value: string) => void;
  onActivityTypeChange: (value: AssignmentActivityType) => void;
  onTargetSeriesSourceChange: (value: string) => void;
  onSampleTimeChange: (value: string) => void;
  onMeasurementToleranceChange: (value: string) => void;
  onInvestigationParameterChange: (value: string) => void;
  onInvestigationOutcomeChange: (value: string) => void;
  onMaxScoreChange: (value: string) => void;
  onDueAtChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onToggleStudent: (id: number) => void;
  onSelectAll: () => void;
  onClassChange: (id: string) => void;
};

export function TeacherAssignmentForm({
  workspaceLayout,
  saved,
  selectedLibrary,
  libraryItemId,
  title,
  description,
  prompt,
  activityType,
  simulation,
  simulationOptionsLoading,
  targetSeriesSource,
  sampleTime,
  measurementTolerance,
  investigationParameter,
  investigationOutcome,
  maxScore,
  dueAt,
  students,
  classes,
  selectedClassId,
  selectedStudents,
  submitting,
  onSubmit,
  onLibraryChange,
  onTitleChange,
  onPromptChange,
  onActivityTypeChange,
  onTargetSeriesSourceChange,
  onSampleTimeChange,
  onMeasurementToleranceChange,
  onInvestigationParameterChange,
  onInvestigationOutcomeChange,
  onMaxScoreChange,
  onDueAtChange,
  onDescriptionChange,
  onToggleStudent,
  onSelectAll,
  onClassChange,
}: Readonly<TeacherAssignmentFormProps>) {
  const [step, setStep] = useState(0);
  const [search, setSearch] = useState("");
  const now = useAssignmentClock();
  const steps = ["Nội dung bài tập", "Học sinh & hạn nộp", "Kiểm tra & giao bài"];
  const typeConfigValid = activityType === "MEASUREMENT"
    ? Boolean(targetSeriesSource && sampleTime.trim() && Number.isFinite(Number(sampleTime)) && measurementTolerance.trim() && Number.isFinite(Number(measurementTolerance)) && Number(measurementTolerance) >= 0)
    : activityType === "PARAMETER_INVESTIGATION" ? Boolean(investigationParameter) : true;
  const contentValid = Boolean(selectedLibrary && simulation && title.trim() && title.trim().length <= 160 && prompt.trim()) && Number(maxScore) >= 0.001 && Number(maxScore) <= 99999.999 && Number.isFinite(Number(maxScore)) && typeConfigValid;
  const recipientsValid = Boolean(selectedClassId) && selectedStudents.length > 0 && (!dueAt || new Date(dueAt).getTime() > now);
  const selectedClass = classes.find(item => item.id === selectedClassId);
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
          <div className="form-group assignment-type-field" style={{ marginBottom: "16px" }}>
            <span className="form-label">Loại hoạt động *</span>
            <div className="assignment-type-grid">
              {([
                ["PREDICT_OBSERVE_EXPLAIN", "Dự đoán – giải thích", "Dự đoán trước, mở mô phỏng rồi đối chiếu kết quả."],
                ["MEASUREMENT", "Đo đại lượng", "Đọc một đại lượng tại thời điểm do giáo viên chọn."],
                ["PARAMETER_INVESTIGATION", "Khảo sát tham số", "Thay đổi một tham số và tìm quy luật ảnh hưởng."],
                ["FREE_EXPLORATION", "Khám phá tự do", "Mô phỏng mở ngay, học sinh quan sát và kết luận."],
              ] as const).map(([value, label, help]) => <button key={value} type="button" className={activityType === value ? "selected" : ""} aria-pressed={activityType === value} onClick={() => onActivityTypeChange(value)}><strong>{label}</strong><small>{help}</small></button>)}
            </div>
          </div>
          {simulationOptionsLoading && <p className="assignment-field-hint" role="status">Đang đọc đại lượng và tham số từ mô phỏng…</p>}
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
              {activityType === "PREDICT_OBSERVE_EXPLAIN" ? "Câu hỏi dự đoán" : activityType === "MEASUREMENT" ? "Yêu cầu đo và giải thích" : activityType === "PARAMETER_INVESTIGATION" ? "Câu hỏi khảo sát" : "Nhiệm vụ khám phá"} *
            </label>
            <textarea
              id="assignment-prompt"
              rows={2}
              required
              placeholder="Ví dụ: Theo em, khi góc bắn tăng từ 30° lên 45° thì tầm xa cực đại tăng hay giảm? Tại sao?"
              value={prompt}
              onChange={(event) => onPromptChange(event.target.value)}
            />
            <small style={{ color: "var(--text-muted)", fontSize: "11.5px" }}>{activityType === "PREDICT_OBSERVE_EXPLAIN" ? "Học sinh gửi dự đoán trước khi mô phỏng mở khóa." : "Mô phỏng mở ngay để học sinh vừa thí nghiệm vừa trả lời."}</small>
          </div>
          {activityType === "MEASUREMENT" && <div className="assignment-config-box">
            <strong>Cấu hình phép đo từ dữ liệu mô phỏng</strong>
            <div className="form-row"><div className="form-group"><label htmlFor="measurement-series">Đại lượng cần đo *</label><select id="measurement-series" value={targetSeriesSource} onChange={event => onTargetSeriesSourceChange(event.target.value)}>{(simulation?.visualization?.series ?? []).map(series => <option key={series.source} value={series.source}>{series.label} ({series.unit || "không đơn vị"})</option>)}</select></div><div className="form-group"><label htmlFor="measurement-time">Thời điểm đo (s) *</label><input id="measurement-time" type="number" min={simulation?.time.at(0) ?? 0} max={simulation?.time.at(-1) ?? 0} step="any" value={sampleTime} onChange={event => onSampleTimeChange(event.target.value)} /></div><div className="form-group"><label htmlFor="measurement-tolerance">Sai số cho phép *</label><input id="measurement-tolerance" type="number" min="0" step="any" value={measurementTolerance} onChange={event => onMeasurementToleranceChange(event.target.value)} /></div></div>
            <small>Đáp án chuẩn được backend lấy trực tiếp từ phiên mô phỏng đã giao; học sinh không nhìn thấy đáp án này.</small>
          </div>}
          {activityType === "PARAMETER_INVESTIGATION" && <div className="assignment-config-box">
            <strong>Cấu hình khảo sát</strong>
            <div className="form-row"><div className="form-group"><label htmlFor="investigation-parameter">Tham số được thay đổi *</label><select id="investigation-parameter" value={investigationParameter} onChange={event => onInvestigationParameterChange(event.target.value)}>{(simulation?.visualization?.controls ?? []).map(control => <option key={control.key} value={control.key}>{control.label} ({control.unit || "không đơn vị"})</option>)}</select></div><div className="form-group"><label htmlFor="investigation-outcome">Đại lượng cần quan sát</label><select id="investigation-outcome" value={investigationOutcome} onChange={event => onInvestigationOutcomeChange(event.target.value)}>{(simulation?.visualization?.series ?? []).map(series => <option key={series.source} value={series.source}>{series.label}</option>)}</select></div></div>
            <small>Học sinh được tự thử nhiều giá trị trong giới hạn an toàn của mô phỏng và giải thích quy luật quan sát được.</small>
          </div>}
          <div className="form-row" style={{ marginBottom: "14px" }}>
            <div className="form-group"><label htmlFor="assignment-max-score">Điểm tối đa</label><input id="assignment-max-score" type="number" min="0.001" step="0.001" value={maxScore} onChange={event => onMaxScoreChange(event.target.value)} /></div>
            <div className="assignment-grading-summary"><span>Phương thức chấm</span><strong>{activityType === "MEASUREMENT" ? "Tự động phần kết quả đo" : "Giáo viên chấm theo bài làm"}</strong></div>
          </div>
          </fieldset>
          <fieldset className="assignment-step-fields" disabled={submitting} hidden={step !== 1}>
          <div className="form-group" style={{ marginBottom: "14px" }}>
            <label htmlFor="assignment-class">Lớp nhận bài *</label>
            <select id="assignment-class" required value={selectedClassId} onChange={event => onClassChange(event.target.value)}>
              <option value="">-- Chọn lớp đang phụ trách --</option>
              {classes.map(item => <option key={item.id} value={item.id}>Khối {item.gradeLevel} · {item.name} · {item.schoolYear} ({item.students.length} học sinh)</option>)}
            </select>
            {classes.length === 0 && <small className="assignment-field-hint">Bạn chưa được nhà trường phân công vào lớp học nào.</small>}
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
          <div className="form-group"><label htmlFor="student-search">Tìm học sinh trong lớp</label><input id="student-search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Nhập tên học sinh…" disabled={!selectedClassId} /></div>
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
                  {selectedClassId ? "Lớp này chưa có học sinh hoạt động." : "Chọn lớp trước khi chọn học sinh."}
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
            <dl><div><dt>Mô phỏng</dt><dd>{selectedLibrary?.title}</dd></div><div><dt>Lớp nhận bài</dt><dd>{selectedClass ? `Khối ${selectedClass.gradeLevel} · ${selectedClass.name}` : "Chưa chọn"}</dd></div><div><dt>Loại bài</dt><dd>{{ PREDICT_OBSERVE_EXPLAIN: "Dự đoán – giải thích", MEASUREMENT: "Đo đại lượng", PARAMETER_INVESTIGATION: "Khảo sát tham số", FREE_EXPLORATION: "Khám phá tự do" }[activityType]}</dd></div><div><dt>Người nhận</dt><dd>{selectedStudents.length} học sinh</dd></div><div><dt>Hạn nộp</dt><dd>{dueAt ? new Date(dueAt).toLocaleString("vi-VN") : "Không giới hạn"}</dd></div><div><dt>Chấm điểm</dt><dd>{activityType === "MEASUREMENT" ? "Tự động kết quả đo" : "Giáo viên chấm"} · Thang {maxScore}</dd></div></dl>
            <p><strong>Học sinh nhận bài:</strong> {students.filter(student => selectedStudents.includes(student.id)).map(student => student.fullName).join(", ")}</p>
            {activityType === "MEASUREMENT" && <p><strong>Phép đo:</strong> {(simulation?.visualization?.series ?? []).find(series => series.source === targetSeriesSource)?.label} tại {sampleTime} giây · Sai số {measurementTolerance}.</p>}
            {activityType === "PARAMETER_INVESTIGATION" && <p><strong>Khảo sát:</strong> thay đổi {(simulation?.visualization?.controls ?? []).find(control => control.key === investigationParameter)?.label} và quan sát {(simulation?.visualization?.series ?? []).find(series => series.source === investigationOutcome)?.label}.</p>}
            <p>{activityType === "PREDICT_OBSERVE_EXPLAIN" ? "Học sinh dự đoán trước, sau đó chạy mô phỏng để đối chiếu và kết luận." : "Học sinh mở mô phỏng ngay, vừa thí nghiệm vừa hoàn thành bài làm trên cùng màn hình."}</p>
          </section>}
          {step === 1 && dueAt && new Date(dueAt).getTime() <= now && <p role="alert">Hãy chọn hạn nộp trong tương lai.</p>}
          {step === 0 && !contentValid && <p className="assignment-field-hint">Chọn mô phỏng, loại hoạt động, nhập tiêu đề, yêu cầu và cấu hình đầy đủ các trường của loại bài.</p>}
          {step === 1 && (!selectedClassId || selectedStudents.length === 0) && <p className="assignment-field-hint">Chọn lớp và ít nhất một học sinh để tiếp tục.</p>}
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
