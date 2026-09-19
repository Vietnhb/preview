import PhysicsScene from "../../simulation/PhysicsScene";
import LearningIcon from "../../common/LearningIcon";
import type { Assignment, AssignmentActivityType, Simulation } from "../../../types/physlive";
import { interpolateAtTime, learningSeries, lessonKind, numberLabel, type LearningControl } from "../../../utils/learningModel";
import { StudentParameterPanel } from "./StudentParameterPanel";
import type { VectorVisibility } from "./studentTypes";

type AssignmentWorkbenchProps = {
  assignment: Assignment;
  activityType: AssignmentActivityType;
  estimatedValue: string;
  onEstimatedValueChange: (value: string) => void;
  onRetrySimulation: () => void;
  predictionSubmitted: boolean;
  submittedPredictionText: string;
  predictionInput: string;
  reasoningInput: string;
  isSubmittingPrediction: boolean;
  predictionError: string;
  assignmentSubmitted: boolean;
  conclusionInput: string;
  isSubmittingAssignment: boolean;
  submissionError: string;
  simulation: Simulation | null;
  time: number;
  seekRevision: number;
  simLoading: boolean;
  simError: string;
  frame: number;
  playing: boolean;
  vectors: VectorVisibility;
  parameterControls: LearningControl[];
  parameterInitialValues: Record<string, number>;
  parameterDraft: Record<string, string>;
  parameterError: string;
  teacherPrompt: string;
  onBack: () => void;
  onSubmitPrediction: (event: React.FormEvent<HTMLFormElement>) => void;
  onSubmitAssignment: (event: React.FormEvent<HTMLFormElement>) => void;
  onConclusionChange: (value: string) => void;
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
  activityType,
  estimatedValue,
  onEstimatedValueChange,
  onRetrySimulation,
  predictionSubmitted,
  submittedPredictionText,
  predictionInput,
  reasoningInput,
  isSubmittingPrediction,
  predictionError,
  assignmentSubmitted,
  conclusionInput,
  isSubmittingAssignment,
  submissionError,
  simulation,
  time,
  seekRevision,
  simLoading,
  simError,
  frame,
  playing,
  vectors,
  parameterControls,
  parameterInitialValues,
  parameterDraft,
  parameterError,
  teacherPrompt,
  onBack,
  onSubmitPrediction,
  onSubmitAssignment,
  onConclusionChange,
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
  const kind = lessonKind(simulation?.schemaId ?? "");
  const questions = typeof assignment.questions === "object" ? assignment.questions : null;
  const measurement = questions?.measurement;
  const investigation = questions?.investigation;
  const displayedSeries = simulation
    ? learningSeries(simulation).map((series) => ({
        series,
        value: interpolateAtTime(simulation.time, series.data, time),
      }))
    : [];

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
        <ol className="assignment-steps"><li className="is-current"><span>1</span>{activityType === "PREDICT_OBSERVE_EXPLAIN" ? "Dự đoán" : "Đọc nhiệm vụ"}</li><li className={predictionSubmitted ? "is-current" : ""}><span>2</span>{activityType === "MEASUREMENT" ? "Đo & giải thích" : activityType === "PARAMETER_INVESTIGATION" ? "Khảo sát & kết luận" : "Thí nghiệm & kết luận"}</li><li className={assignmentSubmitted ? "is-current" : ""}><span>3</span>Nộp bài</li></ol>
        {assignment.retryAllowed && <p className="assignment-notice">Giáo viên đã trả bài. Hãy xem góp ý và gửi lại dự đoán của bạn.</p>}
        {assignment.feedback && <p className="assignment-notice"><strong>Nhận xét của giáo viên:</strong> {assignment.feedback}</p>}
        {assignment.score != null && !assignment.retryAllowed && <p className="assignment-notice">Điểm: <strong>{assignment.score}/{assignment.maxScore ?? 10}</strong> · {assignment.gradingStatus === "TEACHER_CONFIRMED" ? "Giáo viên đã xác nhận" : "Điểm tự động, chờ giáo viên xác nhận"}</p>}
        {predictionSubmitted === false ? (
          <div className="assignment-experiment-grid student-live-workspace">
            <section className="modern-card student-simulation-locked" aria-label="Mô phỏng đang khóa">
              <header><h3>Mô phỏng trực quan</h3><span className="status-pill info">Đang khóa</span></header>
              <div className="student-simulation-locked-stage">
                <LearningIcon name="shield" />
                <strong>Mô phỏng sẽ mở ngay tại đây</strong>
                <p>Hoàn thành dự đoán ở bên phải để bắt đầu thí nghiệm. Bạn không cần chuyển sang trang khác.</p>
              </div>
            </section>
            <div className="prediction-gate-card">
            <span className="prediction-gate-badge">
              <LearningIcon name="shield" /> Bước 1 · Dự đoán của bạn
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
              {assignment.autoGrade && <div className="form-group"><label htmlFor="student-estimate">Kết quả dự đoán bằng số *</label><input id="student-estimate" type="number" step="any" required value={estimatedValue} disabled={isSubmittingPrediction} onChange={event => onEstimatedValueChange(event.target.value)} /><small>Nhập số theo đơn vị trong đề bài. Phần này được dùng để chấm tự động.</small></div>}
              <small>Dự đoán này mở khóa mô phỏng. Bài chỉ được tính là đã nộp sau khi bạn thí nghiệm và gửi kết luận.</small>
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
                  : "Gửi dự đoán & mở mô phỏng"}
              </button>
            </form>
            </div>
          </div>
        ) : (
          <div>
            {activityType === "PREDICT_OBSERVE_EXPLAIN" && <div className="simulation-unlocked-banner">
              <div>
                <strong>Dự đoán đã được gửi:</strong> "{submittedPredictionText}
                "
              </div>
              <span className="status-pill pass">Đã mở khóa mô phỏng</span>
            </div>}
            <p className="assignment-review-prompt">{teacherPrompt}</p>
            <div className="assignment-experiment-grid">
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
                </div>
                <div className="student-overlay-controls" role="group" aria-label="Thành phần hiển thị">
                  {([
                    ["grid", "Lưới"],
                    ["trajectory", kind === "circuit" ? "Tín hiệu" : "Quỹ đạo"],
                    ["velocity", kind === "circuit" ? "Dòng điện" : "Vận tốc"],
                    ...(["circuit", "collision"].includes(kind) ? [] : [["acceleration", "Gia tốc"]]),
                  ] as [keyof VectorVisibility, string][]).map(([key, label]) => (
                    <button key={key} type="button" aria-pressed={vectors[key]} onClick={() => onToggleVector(key)}>
                      <span className={`student-overlay-dot ${key}`} aria-hidden="true" />{label}
                    </button>
                  ))}
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
                    {simError}<button type="button" className="modern-tab-btn" onClick={onRetrySimulation}>Thử lại</button>
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
                        time={time}
                        seekRevision={seekRevision}
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
                        aria-label="Thời điểm mô phỏng"
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
                        {time.toFixed(2)}s
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
                  error={parameterError}
                  onChange={onParameterChange}
                  onReset={onParameterReset}
                />
                <h3 style={{ margin: "0 0 12px 0", fontSize: "15px" }}>{activityType === "MEASUREMENT" ? "Bài làm & số liệu đo" : activityType === "PARAMETER_INVESTIGATION" ? "Nhật ký khảo sát" : "Đối chiếu kết quả"}</h3>
                {activityType === "MEASUREMENT" && measurement && <div className="assignment-live-task"><strong>Nhiệm vụ đo</strong><span>{measurement.seriesLabel} tại t = {measurement.sampleTime} s</span><small>Đơn vị: {measurement.unit || "không đơn vị"} · Sai số cho phép: ±{measurement.tolerance}</small></div>}
                {activityType === "PARAMETER_INVESTIGATION" && investigation && <div className="assignment-live-task"><strong>Nhiệm vụ khảo sát</strong><span>Thay đổi {investigation.parameterLabel}</span>{investigation.outcomeLabel && <small>Quan sát ảnh hưởng lên {investigation.outcomeLabel}</small>}</div>}
                {activityType === "PREDICT_OBSERVE_EXPLAIN" && <div
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
                </div>}
                {simulation && (
                  <section className="student-simulation-readouts" aria-label="Đại lượng tại thời điểm đang xem">
                    <h4>Đại lượng tức thời</h4>
                    <p>Tại thời điểm {numberLabel(time, 2)} s</p>
                    <dl>{displayedSeries.map(({ series, value }) => (
                      <div key={series.key}>
                        <dt><span style={{ background: series.color }} aria-hidden="true" />{series.label}</dt>
                        <dd>{numberLabel(value, kind === "circuit" ? 4 : 2)} <small>{series.unit}</small></dd>
                      </div>
                    ))}</dl>
                    {displayedSeries.length === 0 && <p>Chưa có dữ liệu tại thời điểm này.</p>}
                  </section>
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
                    gian để quan sát các đại lượng mô phỏng và đối chiếu với dự
                    đoán ban đầu của bạn.
                  </small>
                </div>
                {assignmentSubmitted ? (
                  <section className="assignment-submit-panel is-complete" aria-live="polite">
                    <div>
                      <span className="status-pill pass">Đã nộp bài</span>
                      <h3>Bài làm đã được gửi cho giáo viên</h3>
                      <p><strong>Kết luận:</strong> {conclusionInput}</p>
                      {assignment.completedAt && <small>Nộp lúc {new Date(assignment.completedAt).toLocaleString("vi-VN")}</small>}
                    </div>
                  </section>
                ) : (
                  <form className="assignment-submit-panel" onSubmit={onSubmitAssignment}>
                    <div>
                      <span className="assignment-eyebrow">BƯỚC CUỐI</span>
                      <h3>Kết luận sau khi thí nghiệm</h3>
                      <p>So sánh kết quả mô phỏng với dự đoán ban đầu, sau đó nộp bài cho giáo viên.</p>
                    </div>
                    {activityType === "MEASUREMENT" && <div className="form-group"><label htmlFor="student-final-measurement">Kết quả đo ({measurement?.unit || "giá trị số"}) *</label><input id="student-final-measurement" type="number" step="any" required value={estimatedValue} onChange={event => onEstimatedValueChange(event.target.value)} disabled={isSubmittingAssignment} /></div>}
                    <label htmlFor="student-conclusion">Điều em rút ra sau mô phỏng *</label>
                    <textarea
                      id="student-conclusion"
                      rows={4}
                      required
                      maxLength={4000}
                      value={conclusionInput}
                      onChange={event => onConclusionChange(event.target.value)}
                      disabled={isSubmittingAssignment}
                      placeholder="Ví dụ: Kết quả mô phỏng cho thấy vận tốc tăng đều theo thời gian và phù hợp với công thức v = v₀ + at..."
                    />
                    {submissionError && <p className="assignment-submit-error" role="alert">{submissionError}</p>}
                    <button type="submit" className="prediction-submit-btn" disabled={isSubmittingAssignment || !conclusionInput.trim() || (activityType === "MEASUREMENT" && !estimatedValue.trim())}>
                      {isSubmittingAssignment ? "Đang nộp bài…" : "Nộp bài cho giáo viên"}
                    </button>
                  </form>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
