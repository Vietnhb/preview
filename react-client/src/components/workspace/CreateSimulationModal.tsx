import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from "react";
import type { Ambiguity, Problem } from "../../types/physlive";
import Icon from "../common/LearningIcon";
import "../../styles/workspace-modal.css";

function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

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
  canDismiss: boolean;
  onClose: () => void;
  onCreate: (event: FormEvent) => void;
  onConfirmAmbiguities: (event: FormEvent) => void;
  onBackAmbiguity: () => void;
  onResetComposer: () => void;
  sourceFile: File | null;
  sourceFileError: string;
  onSourceFileChange: (file: File | null) => void;
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
  canDismiss,
  onClose,
  onCreate,
  onConfirmAmbiguities,
  onBackAmbiguity,
  onResetComposer,
  sourceFile,
  sourceFileError,
  onSourceFileChange,
  inline = false,
}: Props) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const cameraVideoRef = useRef<HTMLVideoElement>(null);
  const cameraCanvasRef = useRef<HTMLCanvasElement>(null);
  const cameraStreamRef = useRef<MediaStream | null>(null);
  const [cameraOpen, setCameraOpen] = useState(false);
  const [cameraError, setCameraError] = useState("");

  const stopCamera = () => {
    cameraStreamRef.current?.getTracks().forEach(track => track.stop());
    cameraStreamRef.current = null;
    setCameraOpen(false);
  };

  useEffect(() => () => {
    cameraStreamRef.current?.getTracks().forEach(track => track.stop());
  }, []);

  useEffect(() => {
    if (!cameraOpen || !cameraVideoRef.current || !cameraStreamRef.current) return;
    const video = cameraVideoRef.current;
    video.srcObject = cameraStreamRef.current;
    void video.play().catch(() => undefined);

    return () => {
      video.srcObject = null;
    };
  }, [cameraOpen]);

  const handleFileInput = (event: ChangeEvent<HTMLInputElement>) => {
    stopCamera();
    onSourceFileChange(event.target.files?.[0] ?? null);
    event.target.value = "";
  };

  const openCamera = async () => {
    setCameraError("");
    if (!navigator.mediaDevices?.getUserMedia) {
      setCameraError("Trình duyệt không hỗ trợ camera. Bạn có thể dùng Tải tệp.");
      return;
    }

    try {
      stopCamera();
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: "environment" } },
        audio: false,
      });
      cameraStreamRef.current = stream;
      setCameraOpen(true);
    } catch {
      setCameraError("Không thể mở camera. Hãy cấp quyền camera hoặc dùng Tải tệp.");
    }
  };

  const capturePhoto = () => {
    const video = cameraVideoRef.current;
    const canvas = cameraCanvasRef.current;
    if (!video || !canvas || !video.videoWidth || !video.videoHeight) {
      setCameraError("Camera chưa sẵn sàng, hãy thử lại.");
      return;
    }

    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const context = canvas.getContext("2d");
    if (!context) {
      setCameraError("Không thể chụp ảnh. Bạn có thể dùng Tải tệp.");
      return;
    }

    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    canvas.toBlob(blob => {
      if (!blob) {
        setCameraError("Không thể chụp ảnh. Bạn có thể dùng Tải tệp.");
        return;
      }

      onSourceFileChange(new File([blob], `physlive-photo-${Date.now()}.jpg`, { type: "image/jpeg" }));
      stopCamera();
    }, "image/jpeg", 0.92);
  };

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
                <div className="source-picker">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/png,image/jpeg,image/webp,image/gif,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,.pdf,.docx,.txt"
                    hidden
                    onChange={handleFileInput}
                  />
                  <div className="source-picker-copy">
                    <strong>Thêm nguồn đề bài</strong>
                    <span>Ảnh, PDF, DOCX hoặc TXT</span>
                  </div>
                  <div className="source-picker-actions">
                    <button type="button" className="source-picker-button" disabled={loading} onClick={() => fileInputRef.current?.click()}>
                      <Icon name="upload" />Tải tệp
                    </button>
                    <button type="button" className="source-picker-button secondary" disabled={loading} onClick={() => void openCamera()}>
                      <Icon name="camera" />Chụp ảnh
                    </button>
                  </div>
                  {cameraOpen && (
                    <div className="camera-capture-panel">
                      <video ref={cameraVideoRef} className="camera-capture-preview" autoPlay muted playsInline />
                      <canvas ref={cameraCanvasRef} hidden />
                      <div className="camera-capture-actions">
                        <button type="button" className="source-picker-button" disabled={loading} onClick={capturePhoto}>
                          <Icon name="camera" />Chụp ảnh
                        </button>
                        <button type="button" className="source-picker-button secondary" disabled={loading} onClick={stopCamera}>
                          <Icon name="close" />Đóng camera
                        </button>
                      </div>
                    </div>
                  )}
                  {cameraError && <p className="source-file-error" role="alert">{cameraError}</p>}
                  {sourceFile && (
                    <div className="source-file-chip">
                      <span className="source-file-icon"><Icon name="file" /></span>
                      <span className="source-file-meta"><strong>{sourceFile.name}</strong><small>{formatFileSize(sourceFile.size)}</small></span>
                      <button type="button" aria-label="Xóa tệp đính kèm" disabled={loading} onClick={() => onSourceFileChange(null)}><Icon name="close" /></button>
                    </div>
                  )}
                  {sourceFileError && <p className="source-file-error" role="alert">{sourceFileError}</p>}
                </div>
                <div className="modal-input-hint">{token ? "AI sẵn sàng xử lý đề bài" : "Đăng nhập để dùng AI Problem Understanding"}</div>
              </div>
              {error && <div className="modal-info-banner" role="alert"><div className="modal-info-text"><strong>Chưa thể tạo mô phỏng</strong> {error}</div></div>}
              {stage && <div className="modal-info-banner" role="status"><div className="modal-info-text">{stage}</div></div>}
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
                disabled={(!description.trim() && !sourceFile) || loading}
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
