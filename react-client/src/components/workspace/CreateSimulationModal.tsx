import { type FormEvent } from "react";
import type { Ambiguity, Problem, Simulation } from "../../types/physlive";
import "../../styles/workspace-modal.css";

const EXAMPLES = [
  "Một vật chuyển động thẳng với vận tốc đầu 10 m/s và gia tốc 2 m/s². Hãy mô phỏng trong 8 giây.",
  "Ném một vật với vận tốc đầu 20 m/s, góc ném 45 độ, bỏ qua sức cản không khí.",
  "Hai vật có khối lượng 2 kg và 3 kg chuyển động ngược chiều rồi va chạm đàn hồi.",
];

type Props = {
  token: string | null;
  description: string;
  onDescriptionChange: (value: string) => void;
  pendingProblem: Problem | null;
  answers: Record<string, string>;
  onAnswersChange: (value: Record<string, string>) => void;
  loading: boolean;
  stage: string;
  error: string;
  ambiguityStep: number;
  typedQuestion: string;
  questionTyping: boolean;
  ambiguities: Ambiguity[];
  activeAmbiguity: Ambiguity | undefined;
  recent: Simulation[];
  historyLoading: boolean;
  historyError: boolean;
  canDismiss: boolean;
  onClose: () => void;
  onCreate: (event: FormEvent) => void;
  onConfirmAmbiguities: (event: FormEvent) => void;
  onBackAmbiguity: () => void;
  onResetComposer: () => void;
  onOpenRecent: (item: Simulation) => void;
  onRetryHistory: () => void;
  inline?: boolean;
};

export default function CreateSimulationModal({
  token,
  description,
  onDescriptionChange,
  pendingProblem,
  answers,
  onAnswersChange,
  loading,
  stage,
  error,
  ambiguityStep,
  typedQuestion,
  questionTyping,
  ambiguities,
  activeAmbiguity,
  recent,
  historyLoading,
  historyError,
  canDismiss,
  onClose,
  onCreate,
  onConfirmAmbiguities,
  onBackAmbiguity,
  onResetComposer,
  onOpenRecent,
  onRetryHistory,
  inline = false,
}: Props) {
  return (
    <div
      className={inline ? "inline-create-host" : "modal-overlay"}
      role={inline ? undefined : "presentation"}
      onClick={inline ? undefined : () => { if (canDismiss && !loading) onClose(); }}
    >
      <div
        className={inline ? "modal-container inline-create-panel" : "modal-container"}
        role="dialog"
        aria-modal={inline ? undefined : "true"}
        aria-labelledby="create-sim-title"
        onClick={event => { if (!inline) event.stopPropagation(); }}
      >
        <div className="modal-header">
          <div className="modal-title-group">
            <h2 className="modal-title" id="create-sim-title">
              {pendingProblem ? "AI đang làm rõ đề bài" : "Tạo mô phỏng mới"}
            </h2>
            <p className="modal-subtitle">
              {pendingProblem
                ? "Trả lời từng điểm chưa rõ. Solver chỉ chạy sau khi đủ dữ kiện."
                : "AI đọc đề bài, tạo specification và hỏi lại khi dữ kiện chưa rõ."}
            </p>
          </div>
          {canDismiss && (
            <button type="button" className="modal-close-btn" aria-label="Đóng" disabled={loading} onClick={onClose}>
              ×
            </button>
          )}
        </div>

        <div className="modal-body">
          {pendingProblem ? (
            <form id="create-sim-form" onSubmit={onConfirmAmbiguities}>
              <div className="modal-info-banner">
                <span className="modal-info-icon" aria-hidden="true">✨</span>
                <div className="modal-info-text">
                  <strong>{questionTyping ? "AI đang viết…" : `Câu ${ambiguityStep + 1}/${ambiguities.length}`}</strong>
                  {" "}AI hỏi từng điểm chưa rõ thay vì tự điền giá trị.
                </div>
              </div>
              {activeAmbiguity && (
                <div className="modal-input-section" key={activeAmbiguity.id ?? activeAmbiguity.code}>
                  <label className="modal-label" htmlFor="ambiguity-answer">
                    <span className="workspace-ai-mark">AI</span> {typedQuestion}
                    {questionTyping && <i aria-hidden="true" />}
                  </label>
                  {!questionTyping && activeAmbiguity.options && activeAmbiguity.options.length > 0 && (
                    <div className="modal-examples-grid" style={{ marginBottom: 12 }}>
                      {activeAmbiguity.options.map(option => (
                        <button
                          key={option}
                          type="button"
                          className={`modal-example-card${answers[activeAmbiguity.code] === option ? " selected" : ""}`}
                          onClick={() => onAnswersChange({ ...answers, [activeAmbiguity.code]: option })}
                        >
                          <span className="modal-example-text">{option}</span>
                        </button>
                      ))}
                    </div>
                  )}
                  <input
                    id="ambiguity-answer"
                    className="modal-select"
                    autoFocus
                    value={answers[activeAmbiguity.code] ?? ""}
                    disabled={loading || questionTyping}
                    onChange={event => onAnswersChange({ ...answers, [activeAmbiguity.code]: event.target.value })}
                    placeholder="Nhập giá trị và đơn vị nếu có"
                  />
                  <small className="modal-input-hint">{activeAmbiguity.code} · {activeAmbiguity.fieldPath ?? activeAmbiguity.field}</small>
                </div>
              )}
              {error && <div className="modal-info-banner" role="alert"><div className="modal-info-text"><strong>Chưa thể xác nhận</strong> {error}</div></div>}
              {stage && <div className="modal-info-banner" role="status"><div className="modal-info-text">{stage}</div></div>}
            </form>
          ) : (
            <form id="create-sim-form" onSubmit={onCreate}>
              <div className="modal-input-section">
                <label className="modal-label" htmlFor="workspace-description">Mô tả đầy đủ dữ kiện và yêu cầu</label>
                <textarea
                  id="workspace-description"
                  className="modal-textarea"
                  rows={7}
                  value={description}
                  disabled={loading}
                  onChange={event => onDescriptionChange(event.target.value)}
                  placeholder="Ví dụ: Một ô tô bắt đầu từ trạng thái nghỉ, tăng tốc đều 2 m/s² trong 8 giây. Hãy mô phỏng vị trí và vận tốc."
                />
                <div className="modal-input-hint">{token ? "AI sẵn sàng xử lý đề bài" : "Đăng nhập để dùng AI Problem Understanding"}</div>
              </div>
              <div className="modal-examples-section">
                <div className="modal-examples-title">Điền nhanh</div>
                <div className="modal-examples-grid">
                  {EXAMPLES.map((example, index) => (
                    <button
                      type="button"
                      className="modal-example-card"
                      disabled={loading}
                      key={example}
                      onClick={() => onDescriptionChange(example)}
                    >
                      <span className="modal-example-icon" aria-hidden="true">{index + 1}</span>
                      <span className="modal-example-text">{example}</span>
                    </button>
                  ))}
                </div>
              </div>
              {error && <div className="modal-info-banner" role="alert"><div className="modal-info-text"><strong>Chưa thể tạo mô phỏng</strong> {error}</div></div>}
              {stage && <div className="modal-info-banner" role="status"><div className="modal-info-text">{stage}</div></div>}
              {!historyLoading && historyError && (
                <div className="modal-info-banner" role="alert">
                  <div className="modal-info-text">
                    Không tải được các mô phỏng gần đây.{" "}
                    <button type="button" className="modal-btn modal-btn-cancel" style={{ display: "inline-flex", padding: "4px 10px" }} onClick={onRetryHistory}>Thử lại</button>
                  </div>
                </div>
              )}
              {!historyLoading && recent.length > 0 && (
                <div className="modal-examples-section">
                  <div className="modal-examples-title">Mô phỏng gần đây</div>
                  <div className="modal-examples-grid">
                    {recent.slice(0, 4).map(item => (
                      <button type="button" className="modal-example-card" key={item.simulationId} onClick={() => onOpenRecent(item)}>
                        <span className="modal-example-text"><strong>{item.schemaId}</strong><br />{item.time.length} mốc dữ liệu</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </form>
          )}
        </div>

        <div className="modal-footer">
          {pendingProblem ? (
            <>
              <button type="button" className="modal-btn modal-btn-cancel" disabled={loading} onClick={onResetComposer}>Nhập đề khác</button>
              <button type="button" className="modal-btn modal-btn-cancel" disabled={loading || ambiguityStep === 0} onClick={onBackAmbiguity}>Quay lại</button>
              <button
                className="modal-btn modal-btn-primary"
                type="submit"
                form="create-sim-form"
                disabled={loading || questionTyping || !activeAmbiguity || !answers[activeAmbiguity.code]?.trim()}
              >
                {loading ? <span className="modal-btn-spinner">⟳</span> : null}
                {ambiguityStep < ambiguities.length - 1 ? "Tiếp tục" : "Gửi cho AI kiểm tra"}
              </button>
            </>
          ) : (
            <>
              {canDismiss && <button type="button" className="modal-btn modal-btn-cancel" disabled={loading} onClick={onClose}>Hủy</button>}
              <button
                className="modal-btn modal-btn-primary"
                type="submit"
                form="create-sim-form"
                disabled={!description.trim() || loading}
              >
                {loading ? <span className="modal-btn-spinner">⟳</span> : null}
                {loading ? "Đang xử lý…" : "Đưa đề bài cho AI"}
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
