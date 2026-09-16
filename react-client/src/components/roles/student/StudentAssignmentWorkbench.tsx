import PhysicsScene from "../../simulation/PhysicsScene";
import LearningIcon from "../../common/LearningIcon";
import type { Assignment, Simulation } from "../../../types/physlive";
import type { LearningControl } from "../../../utils/learningModel";
import { StudentParameterPanel } from "./StudentParameterPanel";
import type { VectorVisibility } from "./studentTypes";

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
  onTimeChange: (time: number) => void;
  onPlaybackEnd: () => void;
  onParameterChange: (key: string, value: string) => void;
  onParameterReset: () => void;
};

export function AssignmentWorkbench({
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
  onTimeChange,
  onPlaybackEnd,
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
                        time={simulation.time[frame] ?? 0}
                        playing={playing}
                        onTimeChange={onTimeChange}
                        onPlaybackEnd={onPlaybackEnd}
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
