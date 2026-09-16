import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import PhysicsScene from "../../components/simulation/PhysicsScene";
import {
  adjustAssignedSimulation,
  assignedSimulation,
  studentAssignments,
  submitAssignmentPrediction,
} from "../../api/assignmentApi";
import { getSharedSimulation } from "../../api/simulationApi";
import { library } from "../../api/libraryApi";
import type { Assignment, LibraryItem, Simulation } from "../../types/physlive";
import LearningIcon from "../../components/common/LearningIcon";
import { controlValue, type LearningControl } from "../../utils/learningModel";
import "../../styles/modern-roles.css";

interface PredictionPayload {
  answerText: string;
  estimatedValue?: number;
  reasoning?: string;
}

type AssignmentListProps = {
  assignments: Assignment[];
  loading: boolean;
  error: string;
  onRefresh: () => void;
  onSelect: (item: Assignment) => void;
};

function AssignmentList({
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

type SharedLibraryProps = {
  items: LibraryItem[];
  selectedTopic: string;
  loading: boolean;
  selectedItem: LibraryItem | null;
  simulation: Simulation | null;
  simulationLoading: boolean;
  simulationError: string;
  frame: number;
  playing: boolean;
  vectors: {
    grid: boolean;
    trajectory: boolean;
    velocity: boolean;
    acceleration: boolean;
  };
  onTopicChange: (topic: string) => void;
  onOpen: (item: LibraryItem) => void;
  onClose: () => void;
  onTogglePlaying: () => void;
  onReset: () => void;
  onFrameChange: (frame: number) => void;
};

function SharedLibrary({
  items,
  selectedTopic,
  loading,
  selectedItem,
  simulation,
  simulationLoading,
  simulationError,
  frame,
  playing,
  vectors,
  onTopicChange,
  onOpen,
  onClose,
  onTogglePlaying,
  onReset,
  onFrameChange,
}: Readonly<SharedLibraryProps>) {
  const topics = ["", "Kinematics", "Dynamics", "Circuits"];
  return (
    <div className="modern-card">
      <div className="modern-card-header">
        <div>
          <h2>Tài nguyên lớp học</h2>
          <p>Xem và chạy các mô phỏng đã được giáo viên chia sẻ.</p>
        </div>
        <div style={{ display: "flex", gap: "8px" }}>
          {topics.map((topic) => (
            <button
              key={topic}
              type="button"
              className={`role-switch-pill ${selectedTopic === topic ? "active" : ""}`}
              style={{ border: "1px solid var(--border-subtle)" }}
              onClick={() => onTopicChange(topic)}
            >
              {topic || "Tất cả chủ đề"}
            </button>
          ))}
        </div>
      </div>
      {loading && (
        <p style={{ color: "var(--text-muted)", padding: "20px" }}>
          Đang tải thư viện…
        </p>
      )}
      {!loading && items.length === 0 && (
        <div
          style={{
            padding: "40px",
            textAlign: "center",
            color: "var(--text-muted)",
          }}
        >
          Chưa có mô hình nào trong danh mục này.
        </div>
      )}
      {!loading && items.length > 0 && (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
            gap: "16px",
          }}
        >
          {items.map((item) => (
            <div
              key={item.id}
              className="modern-card"
              style={{ margin: 0, padding: "16px" }}
            >
              <span
                className="status-pill pass"
                style={{ marginBottom: "8px" }}
              >
                {item.topic || "Vật lý"}
              </span>
              <h3 style={{ fontSize: "15px", margin: "0 0 8px 0" }}>
                {item.title}
              </h3>
              <small
                style={{
                  color: "var(--text-muted)",
                  display: "block",
                  marginBottom: "14px",
                }}
              >
                Đã kiểm chứng: {item.validationStatus}
              </small>
              <button
                type="button"
                className="prediction-submit-btn"
                style={{ padding: "6px 12px", fontSize: "12px" }}
                onClick={() => onOpen(item)}
              >
                Xem tài nguyên
              </button>
            </div>
          ))}
        </div>
      )}
      {selectedItem && (
        <section className="student-resource-player">
          <div className="student-resource-player-header">
            <div>
              <span className="status-pill info">Tài nguyên được chia sẻ</span>
              <h3>{selectedItem.title}</h3>
            </div>
            <button type="button" className="modern-tab-btn" onClick={onClose}>
              Đóng
            </button>
          </div>
          {simulationLoading && (
            <p className="student-player-state">Đang tải mô hình…</p>
          )}
          {!simulationLoading && simulationError !== "" && (
            <p className="student-player-state error">{simulationError}</p>
          )}
          {!simulationLoading && simulationError === "" && simulation && (
            <>
              <div className="student-resource-scene">
                <PhysicsScene
                  simulation={simulation}
                  index={frame}
                  overlays={vectors}
                />
              </div>
              <div className="student-playback">
                <button
                  type="button"
                  className="prediction-submit-btn"
                  onClick={onTogglePlaying}
                >
                  {playing ? "Tạm dừng" : "Chạy mô phỏng"}
                </button>
                <button
                  type="button"
                  className="modern-tab-btn"
                  onClick={onReset}
                >
                  Tua về đầu
                </button>
                <input
                  type="range"
                  min={0}
                  max={Math.max(0, simulation.time.length - 1)}
                  value={frame}
                  onChange={(event) =>
                    onFrameChange(Number(event.target.value))
                  }
                />
                <span>{(simulation.time[frame] ?? 0).toFixed(2)} s</span>
              </div>
            </>
          )}
        </section>
      )}
    </div>
  );
}

type VectorVisibility = {
  grid: boolean;
  trajectory: boolean;
  velocity: boolean;
  acceleration: boolean;
};

type StudentParameterPanelProps = {
  controls: LearningControl[];
  initialValues: Record<string, number>;
  draft: Record<string, string>;
  adjusting: boolean;
  error: string;
  onChange: (key: string, value: string) => void;
  onReset: () => void;
};

function StudentParameterPanel({
  controls,
  initialValues,
  draft,
  adjusting,
  error,
  onChange,
  onReset,
}: Readonly<StudentParameterPanelProps>) {
  if (controls.length === 0) return null;
  const dirty = controls.some(
    (control) =>
      draft[control.key] !== undefined &&
      draft[control.key].trim() !== "" &&
      Number(draft[control.key]) !== initialValues[control.key],
  );

  return (
    <section
      className="student-parameter-panel"
      aria-label="Điều chỉnh thông số mô phỏng"
    >
      <div className="student-parameter-heading">
        <div>
          <span className="student-parameter-kicker">THỬ NGHIỆM</span>
          <h3>Điều chỉnh thông số</h3>
        </div>
        <button
          type="button"
          className="student-parameter-reset"
          aria-label="Hoàn tác thông số"
          title="Đặt lại thông số ban đầu"
          disabled={!dirty || adjusting}
          onClick={onReset}
        >
          <LearningIcon name="reset" />
        </button>
      </div>
      <p className="student-parameter-note">
        Kéo thanh trượt hoặc nhập số để xem mô phỏng thay đổi ngay.
      </p>
      <div className="student-parameters">
        {controls.map((control) => {
          const draftValue = draft[control.key] ?? "";
          const numericValue = Number(draftValue);
          const initialValue = Number.isFinite(initialValues[control.key])
            ? initialValues[control.key]
            : control.min;
          const validValue = Number.isFinite(numericValue);
          const min = Math.min(
            control.min,
            initialValue,
            validValue ? numericValue : control.min,
          );
          const max = Math.max(
            control.max,
            initialValue,
            validValue ? numericValue : control.max,
          );
          const invalid =
            draftValue.trim() === "" ||
            !validValue ||
            (control.min >= 0 && numericValue < control.min);
          return (
            <div className="student-parameter" key={control.key}>
              <div className="student-parameter-label">
                <span className="student-variable">{control.symbol}</span>
                <label htmlFor={`student-parameter-${control.key}`}>
                  {control.label}
                </label>
              </div>
              <div className="student-parameter-value">
                <input
                  id={`student-parameter-${control.key}`}
                  aria-invalid={invalid}
                  type="number"
                  inputMode="decimal"
                  step="any"
                  min={control.min >= 0 ? control.min : undefined}
                  value={draftValue}
                  onChange={(event) =>
                    onChange(control.key, event.target.value)
                  }
                />
                <span>{control.unit}</span>
              </div>
              <input
                className="student-parameter-range"
                aria-label={`Điều chỉnh ${control.label.toLowerCase()}`}
                type="range"
                min={min}
                max={max}
                step={control.step}
                value={validValue ? numericValue : initialValue}
                onChange={(event) => onChange(control.key, event.target.value)}
              />
              <div className="student-parameter-range-labels">
                <span>{min}</span>
                <span>
                  {max} {control.unit}
                </span>
              </div>
            </div>
          );
        })}
      </div>
      {adjusting && (
        <p className="student-parameter-status" aria-live="polite">
          Đang cập nhật mô phỏng…
        </p>
      )}
      {error && (
        <p className="student-parameter-error" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}

type AssignmentWorkbenchProps = {
  assignment: Assignment;
  predictionSubmitted: boolean;
  submittedPredictionText: string;
  predictionInput: string;
  reasoningInput: string;
  isSubmittingPrediction: boolean;
  predictionError: string;
  simulation: Simulation | null;
  simLoading: boolean;
  simError: string;
  frame: number;
  playing: boolean;
  vectors: VectorVisibility;
  parameterControls: LearningControl[];
  parameterInitialValues: Record<string, number>;
  parameterDraft: Record<string, string>;
  parameterAdjusting: boolean;
  parameterError: string;
  teacherPrompt: string;
  onBack: () => void;
  onSubmitPrediction: (event: React.FormEvent<HTMLFormElement>) => void;
  onPredictionChange: (value: string) => void;
  onReasoningChange: (value: string) => void;
  onToggleVector: (key: keyof VectorVisibility) => void;
  onTogglePlaying: () => void;
  onReset: () => void;
  onFrameChange: (frame: number) => void;
  onParameterChange: (key: string, value: string) => void;
  onParameterReset: () => void;
};

function AssignmentWorkbench({
  assignment,
  predictionSubmitted,
  submittedPredictionText,
  predictionInput,
  reasoningInput,
  isSubmittingPrediction,
  predictionError,
  simulation,
  simLoading,
  simError,
  frame,
  playing,
  vectors,
  parameterControls,
  parameterInitialValues,
  parameterDraft,
  parameterAdjusting,
  parameterError,
  teacherPrompt,
  onBack,
  onSubmitPrediction,
  onPredictionChange,
  onReasoningChange,
  onToggleVector,
  onTogglePlaying,
  onReset,
  onFrameChange,
  onParameterChange,
  onParameterReset,
}: Readonly<AssignmentWorkbenchProps>) {
  return (
    <div>
      <div style={{ marginBottom: "16px" }}>
        <button
          type="button"
          className="modern-tab-btn"
          style={{
            background: "#ffffff",
            border: "1px solid var(--border-subtle)",
          }}
          onClick={onBack}
        >
          ← Trở về danh sách bài tập
        </button>
      </div>
      <div className="modern-card">
        <div className="modern-card-header">
          <div>
            <span className="status-pill info" style={{ marginBottom: "6px" }}>
              Bài tập mô phỏng
            </span>
            <h2>{assignment.title}</h2>
            {assignment.description && (
              <p style={{ marginTop: "6px", color: "var(--text-secondary)" }}>
                {assignment.description}
              </p>
            )}
          </div>
          {assignment.dueAt && (
            <div style={{ textAlign: "right" }}>
              <small style={{ color: "var(--text-muted)", display: "block" }}>
                Hạn nộp bài
              </small>
              <strong>
                {new Date(assignment.dueAt).toLocaleString("vi-VN")}
              </strong>
            </div>
          )}
        </div>
        {predictionSubmitted === false ? (
          <div className="prediction-gate-card">
            <span className="prediction-gate-badge">
              <LearningIcon name="shield" /> Cổng dự đoán bắt buộc
            </span>
            <h3 className="prediction-gate-title">
              Câu hỏi dự đoán trước khi xem mô phỏng
            </h3>
            <p className="prediction-gate-desc">
              Theo nguyên tắc học tập tương tác, bạn cần đưa ra giả thuyết / dự
              đoán kết quả trước. Ngay sau khi gửi câu trả lời, mô phỏng chuyển
              động thực tế sẽ được mở khóa hoàn toàn.
            </p>
            <div className="prediction-prompt-box">
              <LearningIcon name="message" /> {teacherPrompt}
            </div>
            <form
              onSubmit={onSubmitPrediction}
              className="prediction-input-area"
            >
              <label
                htmlFor="student-prediction"
                style={{
                  fontSize: "13px",
                  fontWeight: "600",
                  color: "#166534",
                }}
              >
                Câu trả lời / Dự đoán của bạn:
              </label>
              <textarea
                id="student-prediction"
                rows={3}
                required
                placeholder="Ví dụ: Vận tốc trước khi chạm đất là khoảng 14 m/s vì gia tốc trọng trường g = 9.8 m/s²..."
                value={predictionInput}
                onChange={(event) => onPredictionChange(event.target.value)}
                disabled={isSubmittingPrediction}
              />
              <label
                htmlFor="student-reasoning"
                style={{
                  fontSize: "13px",
                  fontWeight: "600",
                  color: "#166534",
                }}
              >
                Lập luận hoặc công thức bạn áp dụng (không bắt buộc):
              </label>
              <textarea
                id="student-reasoning"
                rows={2}
                placeholder="Ví dụ: Áp dụng công thức v² - v₀² = 2as hoặc định luật bảo toàn cơ năng..."
                value={reasoningInput}
                onChange={(event) => onReasoningChange(event.target.value)}
                disabled={isSubmittingPrediction}
              />
              {predictionError && (
                <p style={{ color: "#b91c1c", fontSize: "13px", margin: 0 }}>
                  {predictionError}
                </p>
              )}
              <button
                type="submit"
                className="prediction-submit-btn"
                disabled={isSubmittingPrediction || !predictionInput.trim()}
              >
                {isSubmittingPrediction
                  ? "Đang ghi nhận dự đoán…"
                  : "Xác nhận dự đoán & mở khóa"}
              </button>
            </form>
          </div>
        ) : (
          <div>
            <div className="simulation-unlocked-banner">
              <div>
                <strong>Dự đoán đã được gửi:</strong> "{submittedPredictionText}
                "
              </div>
              <span className="status-pill pass">Đã mở khóa mô phỏng</span>
            </div>
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "1fr 340px",
                gap: "20px",
                alignItems: "start",
              }}
            >
              <div
                className="modern-card"
                style={{ padding: "16px", margin: 0 }}
              >
                <div
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    marginBottom: "12px",
                  }}
                >
                  <h3 style={{ margin: 0, fontSize: "15px" }}>
                    Chạy mô phỏng trực quan
                  </h3>
                  <div style={{ display: "flex", gap: "8px" }}>
                    <button
                      type="button"
                      className="role-switch-pill"
                      style={{ border: "1px solid var(--border-subtle)" }}
                      onClick={() => onToggleVector("trajectory")}
                    >
                      {vectors.trajectory ? "Quỹ đạo ✓" : "Quỹ đạo"}
                    </button>
                    <button
                      type="button"
                      className="role-switch-pill"
                      style={{ border: "1px solid var(--border-subtle)" }}
                      onClick={() => onToggleVector("velocity")}
                    >
                      {vectors.velocity ? "Vận tốc v⃗ ✓" : "Vận tốc v⃗"}
                    </button>
                    <button
                      type="button"
                      className="role-switch-pill"
                      style={{ border: "1px solid var(--border-subtle)" }}
                      onClick={() => onToggleVector("acceleration")}
                    >
                      {vectors.acceleration ? "Gia tốc a⃗ ✓" : "Gia tốc a⃗"}
                    </button>
                  </div>
                </div>
                {simLoading && (
                  <div
                    style={{
                      height: "360px",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      color: "var(--text-muted)",
                    }}
                  >
                    Đang chuẩn bị mô hình vật lý…
                  </div>
                )}
                {!simLoading && simError !== "" && (
                  <div
                    style={{
                      height: "360px",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      color: "#b91c1c",
                    }}
                  >
                    {simError}
                  </div>
                )}
                {!simLoading && simError === "" && simulation && (
                  <>
                    <div
                      style={{
                        height: "380px",
                        background: "#f8fafc",
                        borderRadius: "8px",
                        overflow: "hidden",
                      }}
                    >
                      <PhysicsScene
                        simulation={simulation}
                        index={frame}
                        overlays={vectors}
                      />
                    </div>
                    <div
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: "12px",
                        marginTop: "14px",
                        padding: "10px",
                        background: "#f1f5f9",
                        borderRadius: "8px",
                      }}
                    >
                      <button
                        type="button"
                        className="prediction-submit-btn"
                        style={{ padding: "6px 14px", fontSize: "13px" }}
                        onClick={onTogglePlaying}
                      >
                        {playing ? "Tạm dừng" : "Chạy mô phỏng"}
                      </button>
                      <button
                        type="button"
                        className="role-switch-pill"
                        style={{
                          background: "#ffffff",
                          border: "1px solid var(--border-subtle)",
                        }}
                        onClick={onReset}
                      >
                        Tua về đầu
                      </button>
                      <input
                        type="range"
                        min={0}
                        max={Math.max(0, simulation.time.length - 1)}
                        value={frame}
                        onChange={(event) =>
                          onFrameChange(Number(event.target.value))
                        }
                        style={{ flex: 1 }}
                      />
                      <span
                        style={{
                          fontFamily: "monospace",
                          fontWeight: "700",
                          minWidth: "55px",
                        }}
                      >
                        {(simulation.time[frame] ?? 0).toFixed(2)}s
                      </span>
                    </div>
                  </>
                )}
                {!simLoading && simError === "" && simulation === null && (
                  <div
                    style={{
                      padding: "40px",
                      textAlign: "center",
                      color: "var(--text-muted)",
                    }}
                  >
                    Không có dữ liệu mô phỏng.
                  </div>
                )}
              </div>
              <div
                className="modern-card"
                style={{ padding: "18px", margin: 0 }}
              >
                <StudentParameterPanel
                  controls={parameterControls}
                  initialValues={parameterInitialValues}
                  draft={parameterDraft}
                  adjusting={parameterAdjusting}
                  error={parameterError}
                  onChange={onParameterChange}
                  onReset={onParameterReset}
                />
                <h3 style={{ margin: "0 0 12px 0", fontSize: "15px" }}>
                  Đối chiếu Kết quả
                </h3>
                <div
                  style={{
                    background: "#f8fafc",
                    padding: "12px",
                    borderRadius: "8px",
                    marginBottom: "14px",
                    border: "1px solid var(--border-subtle)",
                  }}
                >
                  <small
                    style={{
                      color: "var(--text-muted)",
                      display: "block",
                      marginBottom: "4px",
                    }}
                  >
                    Dự đoán của bạn:
                  </small>
                  <p
                    style={{
                      margin: 0,
                      fontSize: "13.5px",
                      fontWeight: "600",
                      color: "#166534",
                    }}
                  >
                    &quot;{submittedPredictionText}&quot;
                  </p>
                </div>
                {simulation && (
                  <div
                    style={{
                      background: "#eff6ff",
                      padding: "12px",
                      borderRadius: "8px",
                      border: "1px solid #bfdbfe",
                    }}
                  >
                    <small
                      style={{
                        color: "#1d4ed8",
                        display: "block",
                        marginBottom: "6px",
                        fontWeight: "700",
                      }}
                    >
                      Thông số mô phỏng thực tế:
                    </small>
                    <div
                      style={{
                        fontSize: "13px",
                        display: "flex",
                        flexDirection: "column",
                        gap: "4px",
                      }}
                    >
                      {Object.entries(simulation.parameters ?? {}).map(
                        ([key, value]) => (
                          <div key={key}>
                            <strong>{key}:</strong> {value}
                          </div>
                        ),
                      )}
                      <div>
                        <strong>Thời điểm t:</strong>{" "}
                        {(simulation.time[frame] ?? 0).toFixed(2)} s
                      </div>
                      {simulation.positions &&
                        Object.keys(simulation.positions).map((key) => (
                          <div key={key}>
                            <strong>Vị trí ({key}):</strong>{" "}
                            {(simulation.positions[key]?.[frame] ?? 0).toFixed(
                              2,
                            )}{" "}
                            m
                          </div>
                        ))}
                      {simulation.velocities &&
                        Object.keys(simulation.velocities).map((key) => (
                          <div key={key}>
                            <strong>Vận tốc ({key}):</strong>{" "}
                            {(simulation.velocities[key]?.[frame] ?? 0).toFixed(
                              2,
                            )}{" "}
                            m/s
                          </div>
                        ))}
                    </div>
                  </div>
                )}
                <div style={{ marginTop: "18px" }}>
                  <small
                    style={{
                      color: "var(--text-muted)",
                      lineHeight: "1.4",
                      display: "block",
                    }}
                  >
                    <strong>Gợi ý học tập:</strong> Di chuyển thanh trượt thời
                    gian để quan sát sự biến thiên của vận tốc và gia tốc so với
                    dự đoán ban đầu của bạn.
                  </small>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default function StudentAssignments({
  initialTab = "assigned",
}: Readonly<{ initialTab?: "assigned" | "library" }>) {
  const [activeTab, setActiveTab] = useState<"assigned" | "library">(
    initialTab,
  );

  // Assigned items
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Selected assignment for practicing
  const [selectedAssignment, setSelectedAssignment] =
    useState<Assignment | null>(null);
  const [simulation, setSimulation] = useState<Simulation | null>(null);
  const [simLoading, setSimLoading] = useState(false);
  const [simError, setSimError] = useState("");

  // Prediction Gate State
  const [predictionSubmitted, setPredictionSubmitted] = useState(false);
  const [submittedPredictionText, setSubmittedPredictionText] = useState("");
  const [predictionInput, setPredictionInput] = useState("");
  const [reasoningInput, setReasoningInput] = useState("");
  const [isSubmittingPrediction, setIsSubmittingPrediction] = useState(false);
  const [predictionError, setPredictionError] = useState("");

  // Playback state
  const [frame, setFrame] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [vectors, setVectors] = useState({
    grid: true,
    trajectory: true,
    velocity: true,
    acceleration: false,
  });
  const [parameterInitialValues, setParameterInitialValues] = useState<
    Record<string, number>
  >({});
  const [parameterDraft, setParameterDraft] = useState<Record<string, string>>(
    {},
  );
  const [parameterAdjusting, setParameterAdjusting] = useState(false);
  const [parameterError, setParameterError] = useState("");
  const parameterTimerRef = useRef<number | null>(null);
  const parameterRequestRef = useRef(0);
  const baseSimulationRef = useRef<Simulation | null>(null);

  // Class / Shared Library state
  const [sharedItems, setSharedItems] = useState<LibraryItem[]>([]);
  const [selectedTopic, setSelectedTopic] = useState("");
  const [sharedLoading, setSharedLoading] = useState(false);
  const [selectedSharedItem, setSelectedSharedItem] =
    useState<LibraryItem | null>(null);
  const [sharedSimulation, setSharedSimulation] = useState<Simulation | null>(
    null,
  );
  const [sharedSimLoading, setSharedSimLoading] = useState(false);
  const [sharedSimError, setSharedSimError] = useState("");
  const [sharedFrame, setSharedFrame] = useState(0);
  const [sharedPlaying, setSharedPlaying] = useState(false);

  const loadAssignments = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await studentAssignments();
      setAssignments(data);
    } catch {
      setError("Không thể tải danh sách bài tập được giao.");
    } finally {
      setLoading(false);
    }
  }, []);

  const loadSharedLibrary = useCallback(async (topic?: string) => {
    setSharedLoading(true);
    try {
      const data = await library(topic || undefined);
      setSharedItems(data.filter((item) => item.visibility === "SHARED"));
    } catch {
      // ignore
    } finally {
      setSharedLoading(false);
    }
  }, []);

  const clearParameterState = () => {
    parameterRequestRef.current += 1;
    if (parameterTimerRef.current !== null) {
      globalThis.clearTimeout(parameterTimerRef.current);
      parameterTimerRef.current = null;
    }
    baseSimulationRef.current = null;
    setParameterInitialValues({});
    setParameterDraft({});
    setParameterAdjusting(false);
    setParameterError("");
  };

  useEffect(() => {
    void loadAssignments();
  }, [loadAssignments]);

  useEffect(() => {
    if (activeTab === "library") {
      void loadSharedLibrary(selectedTopic);
    }
  }, [activeTab, selectedTopic, loadSharedLibrary]);

  const loadAssignedSimulation = useCallback(async (assignmentId: string) => {
    setSimLoading(true);
    setSimError("");
    try {
      const loaded = await assignedSimulation(assignmentId);
      baseSimulationRef.current = loaded;
      const controls = (loaded.visualization?.controls ??
        []) as LearningControl[];
      const values = Object.fromEntries(
        controls.map((control) => [control.key, controlValue(control, loaded)]),
      );
      setParameterInitialValues(values);
      setParameterDraft(
        Object.fromEntries(
          Object.entries(values).map(([key, value]) => [
            key,
            Number.isFinite(value) ? String(value) : "",
          ]),
        ),
      );
      setParameterError("");
      setSimulation(loaded);
    } catch {
      clearParameterState();
      setSimulation(null);
      setSimError("Chưa tải được mô hình mô phỏng của bài tập này.");
    } finally {
      setSimLoading(false);
    }
  }, []);

  // The simulation is deliberately requested only after the prediction gate is open.
  const handleSelectAssignment = async (item: Assignment) => {
    clearParameterState();
    setSelectedAssignment(item);
    const alreadySubmitted = Boolean(item.predictionSubmitted);
    setPredictionSubmitted(alreadySubmitted);
    setSubmittedPredictionText(
      alreadySubmitted ? "Dự đoán đã được gửi trước đó." : "",
    );
    setPredictionInput("");
    setReasoningInput("");
    setPredictionError("");
    setSimulation(null);
    setFrame(0);
    setPlaying(false);
    setSimError("");
    if (alreadySubmitted) await loadAssignedSimulation(item.id);
  };

  useEffect(
    () => () => {
      if (parameterTimerRef.current !== null)
        globalThis.clearTimeout(parameterTimerRef.current);
    },
    [],
  );

  useEffect(() => {
    const controls = (simulation?.visualization?.controls ??
      []) as LearningControl[];
    if (
      !selectedAssignment ||
      !predictionSubmitted ||
      !simulation ||
      controls.length === 0
    )
      return;

    const numericValues: Record<string, number> = {};
    const hasInvalidValue = controls.some((control) => {
      const rawValue = parameterDraft[control.key] ?? "";
      const value = Number(rawValue);
      const invalid =
        rawValue.trim() === "" ||
        !Number.isFinite(value) ||
        (control.min >= 0 && value < control.min);
      if (!invalid) numericValues[control.key] = value;
      return invalid;
    });

    const requestId = ++parameterRequestRef.current;
    if (parameterTimerRef.current !== null)
      globalThis.clearTimeout(parameterTimerRef.current);
    if (hasInvalidValue) {
      setParameterAdjusting(false);
      return;
    }

    const changed = controls.some(
      (control) =>
        simulation.parameters?.[control.key] !== numericValues[control.key],
    );
    if (!changed) {
      setParameterAdjusting(false);
      return;
    }

    setParameterAdjusting(true);
    setParameterError("");
    const simulationId = simulation.simulationId;
    const assignmentId = selectedAssignment.id;
    parameterTimerRef.current = globalThis.setTimeout(() => {
      void adjustAssignedSimulation(assignmentId, simulationId, numericValues)
        .then((updated) => {
          if (requestId !== parameterRequestRef.current) return;
          setSimulation(updated);
          setFrame(0);
          setPlaying(false);
        })
        .catch(() => {
          if (requestId === parameterRequestRef.current)
            setParameterError("Không thể cập nhật mô phỏng với giá trị này.");
        })
        .finally(() => {
          if (requestId === parameterRequestRef.current)
            setParameterAdjusting(false);
        });
    }, 180);

    return () => {
      if (parameterTimerRef.current !== null) {
        globalThis.clearTimeout(parameterTimerRef.current);
        parameterTimerRef.current = null;
      }
    };
  }, [parameterDraft, predictionSubmitted, selectedAssignment, simulation]);

  const handleParameterReset = () => {
    const base = baseSimulationRef.current;
    if (!base) return;
    parameterRequestRef.current += 1;
    if (parameterTimerRef.current !== null)
      globalThis.clearTimeout(parameterTimerRef.current);
    const controls = (base.visualization?.controls ?? []) as LearningControl[];
    const values = Object.fromEntries(
      controls.map((control) => [control.key, controlValue(control, base)]),
    );
    setParameterInitialValues(values);
    setParameterDraft(
      Object.fromEntries(
        Object.entries(values).map(([key, value]) => [
          key,
          Number.isFinite(value) ? String(value) : "",
        ]),
      ),
    );
    setParameterError("");
    setParameterAdjusting(false);
    setSimulation(base);
    setFrame(0);
    setPlaying(false);
  };

  const handleOpenShared = async (item: LibraryItem) => {
    setSelectedSharedItem(item);
    setSharedSimulation(null);
    setSharedFrame(0);
    setSharedPlaying(false);
    setSharedSimLoading(true);
    setSharedSimError("");
    try {
      setSharedSimulation(await getSharedSimulation(item.simulationId));
    } catch {
      setSharedSimError("Chưa tải được mô phỏng trong tài nguyên này.");
    } finally {
      setSharedSimLoading(false);
    }
  };

  // Submit Prediction Gate (FR-STU-02)
  const handleSubmitPrediction = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAssignment || !predictionInput.trim()) return;

    setIsSubmittingPrediction(true);
    setPredictionError("");

    const payload: PredictionPayload = {
      answerText: predictionInput.trim(),
      reasoning: reasoningInput.trim() || undefined,
    };

    try {
      await submitAssignmentPrediction(selectedAssignment.id, payload);
      setPredictionSubmitted(true);
      setSubmittedPredictionText(predictionInput.trim());
      await loadAssignedSimulation(selectedAssignment.id);
    } catch (err: unknown) {
      // If student has already submitted prediction previously, unlock simulation
      if (typeof err === "object" && err !== null && "response" in err) {
        const axiosErr = err as { response?: { status?: number } };
        if (axiosErr.response?.status === 409) {
          setPredictionSubmitted(true);
          setSubmittedPredictionText(
            predictionInput.trim() || "Dự đoán đã ghi nhận trước đó",
          );
          await loadAssignedSimulation(selectedAssignment.id);
          return;
        }
      }
      setPredictionError(
        "Không thể ghi nhận câu trả lời dự đoán. Vui lòng thử lại.",
      );
    } finally {
      setIsSubmittingPrediction(false);
    }
  };

  // Playback timer
  useEffect(() => {
    if (!playing || !simulation?.time.length) return;
    const interval = globalThis.setInterval(() => {
      setFrame((current) => (current + 1) % simulation.time.length);
    }, 40);
    return () => globalThis.clearInterval(interval);
  }, [playing, simulation]);

  useEffect(() => {
    if (!sharedPlaying || !sharedSimulation?.time.length) return;
    const interval = globalThis.setInterval(() => {
      setSharedFrame((current) => (current + 1) % sharedSimulation.time.length);
    }, 40);
    return () => globalThis.clearInterval(interval);
  }, [sharedPlaying, sharedSimulation]);

  const teacherPrompt = useMemo(() => {
    if (!selectedAssignment?.questions)
      return "Hãy quan sát hiện tượng và đưa ra dự đoán kết quả trước khi chạy mô phỏng.";
    if (
      typeof selectedAssignment.questions === "object" &&
      selectedAssignment.questions !== null &&
      "prompt" in selectedAssignment.questions
    ) {
      return String(
        (selectedAssignment.questions as { prompt: unknown }).prompt,
      );
    }
    return typeof selectedAssignment.questions === "string"
      ? selectedAssignment.questions
      : JSON.stringify(selectedAssignment.questions);
  }, [selectedAssignment]);

  return (
    <div className={`main student-main student-layout-${activeTab}`}>
      <div className="modern-container">
        {/* Header */}
        <header className="modern-header">
          <div className="modern-header-title">
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "10px",
                marginBottom: "6px",
              }}
            >
              <h1>Bài tập của tôi</h1>
            </div>
            <p>
              Xem bài được giao, gửi dự đoán trước khi chạy mô phỏng và học từ
              tài nguyên được chia sẻ.
            </p>
          </div>
        </header>

        {/* Tabs */}
        <div className="modern-tabs">
          <button
            className={`modern-tab-btn ${activeTab === "assigned" ? "active" : ""}`}
            onClick={() => {
              setActiveTab("assigned");
              setSelectedAssignment(null);
              setSelectedSharedItem(null);
            }}
          >
            <span>Bài tập được giao</span>
            <span className="modern-tab-badge">{assignments.length}</span>
          </button>
          <button
            className={`modern-tab-btn ${activeTab === "library" ? "active" : ""}`}
            onClick={() => {
              setActiveTab("library");
              setSelectedAssignment(null);
            }}
          >
            <span>Tài nguyên lớp học</span>
          </button>
        </div>

        {/* TAB 1: ASSIGNED SIMULATIONS */}
        {activeTab === "assigned" && (
          <>
            {selectedAssignment === null ? (
              <AssignmentList
                assignments={assignments}
                loading={loading}
                error={error}
                onRefresh={loadAssignments}
                onSelect={(item) => {
                  void handleSelectAssignment(item);
                }}
              />
            ) : (
              <AssignmentWorkbench
                assignment={selectedAssignment}
                predictionSubmitted={predictionSubmitted}
                submittedPredictionText={submittedPredictionText}
                predictionInput={predictionInput}
                reasoningInput={reasoningInput}
                isSubmittingPrediction={isSubmittingPrediction}
                predictionError={predictionError}
                simulation={simulation}
                simLoading={simLoading}
                simError={simError}
                frame={frame}
                playing={playing}
                vectors={vectors}
                parameterControls={
                  (simulation?.visualization?.controls ??
                    []) as LearningControl[]
                }
                parameterInitialValues={parameterInitialValues}
                parameterDraft={parameterDraft}
                parameterAdjusting={parameterAdjusting}
                parameterError={parameterError}
                teacherPrompt={teacherPrompt}
                onBack={() => {
                  clearParameterState();
                  setSelectedAssignment(null);
                }}
                onSubmitPrediction={handleSubmitPrediction}
                onPredictionChange={setPredictionInput}
                onReasoningChange={setReasoningInput}
                onToggleVector={(key) =>
                  setVectors((current) => ({
                    ...current,
                    [key]: !current[key],
                  }))
                }
                onTogglePlaying={() => setPlaying((current) => !current)}
                onReset={() => {
                  setPlaying(false);
                  setFrame(0);
                }}
                onFrameChange={(frameValue) => {
                  setPlaying(false);
                  setFrame(frameValue);
                }}
                onParameterChange={(key, value) =>
                  setParameterDraft((current) => ({ ...current, [key]: value }))
                }
                onParameterReset={handleParameterReset}
              />
            )}
          </>
        )}

        {/* TAB 2: CLASS & SHARED LIBRARY (FR-STU-04) */}
        {activeTab === "library" && (
          <SharedLibrary
            items={sharedItems}
            selectedTopic={selectedTopic}
            loading={sharedLoading}
            selectedItem={selectedSharedItem}
            simulation={sharedSimulation}
            simulationLoading={sharedSimLoading}
            simulationError={sharedSimError}
            frame={sharedFrame}
            playing={sharedPlaying}
            vectors={vectors}
            onTopicChange={setSelectedTopic}
            onOpen={(item) => {
              void handleOpenShared(item);
            }}
            onClose={() => setSelectedSharedItem(null)}
            onTogglePlaying={() => setSharedPlaying((value) => !value)}
            onReset={() => {
              setSharedPlaying(false);
              setSharedFrame(0);
            }}
            onFrameChange={(frameValue) => {
              setSharedPlaying(false);
              setSharedFrame(frameValue);
            }}
          />
        )}
      </div>
    </div>
  );
}
