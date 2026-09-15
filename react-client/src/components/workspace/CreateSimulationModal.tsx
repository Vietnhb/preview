import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from "react";
import type { Ambiguity, ConversationMessage, Problem } from "../../types/physlive";
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
  conversation: ConversationMessage[];
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
  conversation,
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
  const messagesRef = useRef<HTMLDivElement>(null);
  const composerTextareaRef = useRef<HTMLTextAreaElement>(null);
  const attachmentControlRef = useRef<HTMLDivElement>(null);
  const [cameraOpen, setCameraOpen] = useState(false);
  const [cameraError, setCameraError] = useState("");
  const [attachmentMenuOpen, setAttachmentMenuOpen] = useState(false);

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
    setAttachmentMenuOpen(false);
    onSourceFileChange(event.target.files?.[0] ?? null);
    event.target.value = "";
  };

  const openCamera = async () => {
    setCameraError("");
    setAttachmentMenuOpen(false);
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

  const composerValue = pendingProblem && activeAmbiguity
    ? answers[activeAmbiguity.code] ?? ""
    : description;
  const hasChatContent = conversation.length > 0 || Boolean(stage || error || pendingProblem);

  useEffect(() => {
    const textarea = composerTextareaRef.current;
    if (!textarea) return;
    textarea.style.height = "auto";
    textarea.style.height = `${Math.min(Math.max(textarea.scrollHeight, 24), 120)}px`;
  }, [composerValue, pendingProblem?.id, activeAmbiguity?.code]);

  useEffect(() => {
    const messages = messagesRef.current;
    if (messages) messages.scrollTop = messages.scrollHeight;
  }, [conversation, stage, error, activeAmbiguity?.code, questionTyping]);

  useEffect(() => {
    if (!attachmentMenuOpen) return;
    const closeOnOutsideClick = (event: PointerEvent) => {
      const target = event.target as Node;
      if (!attachmentControlRef.current?.contains(target)) setAttachmentMenuOpen(false);
    };
    document.addEventListener("pointerdown", closeOnOutsideClick);
    return () => document.removeEventListener("pointerdown", closeOnOutsideClick);
  }, [attachmentMenuOpen]);

  const panel = (
    <div
      className={inline ? "inline-create-panel" : "modal-container"}
      role="dialog"
      aria-modal={inline ? undefined : "true"}
      aria-labelledby="create-sim-title"
      onClick={event => { if (!inline) event.stopPropagation(); }}
    >
      <header className="create-chat-header">
        <div className="create-chat-title-group">
          <h2 id="create-sim-title">
            {pendingProblem ? "AI đang làm rõ đề bài" : "Tạo mô phỏng mới"}
          </h2>
          <p>
            {pendingProblem
              ? "Trả lời từng điểm chưa rõ. Solver chỉ chạy sau khi đủ dữ kiện."
              : "Trao đổi với PhysLive AI để xây dựng mô phỏng."}
          </p>
        </div>
        {canDismiss && (
          <button type="button" className="create-chat-close" aria-label="Đóng" disabled={loading} onClick={onClose}>
            ×
          </button>
        )}
      </header>

      <div className="create-chat">
        <div className="create-chat-messages" ref={messagesRef} aria-live="polite">
          {!hasChatContent ? (
            <div className="create-chat-empty-state">
              <h3>Bạn muốn mô phỏng hiện tượng gì?</h3>
              <p>Nhập đề bài, mô tả hiện tượng hoặc tải ảnh/PDF.<br />PhysLive AI sẽ phân tích và hỏi thêm nếu cần.</p>
            </div>
          ) : (
            <>
              {conversation.map(message => (
                <div className={`create-chat-message ${message.role}`} key={message.id}>
                  <span className="create-chat-avatar">{message.role === "assistant" ? "AI" : "Bạn"}</span>
                  <div className="create-chat-bubble"><p>{message.text}</p></div>
                </div>
              ))}
              {pendingProblem && activeAmbiguity && !loading && (
                <div className="create-chat-message assistant create-chat-question" key={`${activeAmbiguity.code}-${ambiguityStep}`}>
                  <span className="create-chat-avatar">AI</span>
                  <div className="create-chat-bubble">
                    <p>{questionTyping ? (typedQuestion || "AI đang viết…") : activeAmbiguity.question}</p>
                  </div>
                </div>
              )}
              {stage && (
                <div className="create-chat-message assistant create-chat-status">
                  <span className="create-chat-avatar">AI</span>
                  <div className="create-chat-bubble"><span className="create-chat-status-dot" aria-hidden="true" />{stage}</div>
                </div>
              )}
              {error && (
                <div className="create-chat-message assistant create-chat-error" role="alert">
                  <span className="create-chat-avatar">AI</span>
                  <div className="create-chat-bubble">Mình chưa thể tiếp tục: {error}</div>
                </div>
              )}
            </>
          )}
        </div>

        <form
          id="create-sim-form"
          className="create-chat-composer-wrap"
          onSubmit={pendingProblem ? onConfirmAmbiguities : onCreate}
        >
          <div className="create-chat-composer-support">
            {pendingProblem && activeAmbiguity && !questionTyping && activeAmbiguity.options && activeAmbiguity.options.length > 0 && (
              <div className="create-chat-options" aria-label="Gợi ý trả lời">
                {activeAmbiguity.options.map(option => (
                  <button
                    key={option}
                    type="button"
                    className={answers[activeAmbiguity.code] === option ? "selected" : ""}
                    onClick={() => onAnswersChange({ ...answers, [activeAmbiguity.code]: option })}
                  >
                    {option}
                  </button>
                ))}
              </div>
            )}

            {!pendingProblem && sourceFile && (
              <div className="source-file-chip">
                <span className="source-file-icon"><Icon name="file" /></span>
                <span className="source-file-meta"><strong>{sourceFile.name}</strong><small>{formatFileSize(sourceFile.size)}</small></span>
                <button type="button" aria-label="Xóa tệp đính kèm" disabled={loading} onClick={() => onSourceFileChange(null)}><Icon name="close" /></button>
              </div>
            )}

            {!pendingProblem && cameraOpen && (
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
            {!pendingProblem && cameraError && <p className="source-file-error" role="alert">{cameraError}</p>}
            {!pendingProblem && sourceFileError && <p className="source-file-error" role="alert">{sourceFileError}</p>}
            {pendingProblem && activeAmbiguity && (
              <small className="create-chat-input-hint">{activeAmbiguity.code} · {activeAmbiguity.fieldPath ?? activeAmbiguity.field}</small>
            )}

            {pendingProblem && (
              <div className="create-chat-composer-actions">
                <button type="button" className="create-chat-secondary-action" disabled={loading} onClick={onResetComposer}>Nhập đề khác</button>
                <button type="button" className="create-chat-secondary-action" disabled={loading || ambiguityStep === 0} onClick={onBackAmbiguity}>Quay lại</button>
              </div>
            )}
          </div>

          <div className="create-chat-composer">
            <input
              ref={fileInputRef}
              type="file"
              accept="image/png,image/jpeg,image/webp,image/gif,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,.pdf,.docx,.txt"
              hidden
              onChange={handleFileInput}
            />

            {!pendingProblem && (
              <div className="create-chat-attachment-control" ref={attachmentControlRef}>
                <button
                  type="button"
                  className="create-chat-plus-button"
                  aria-label="Thêm ảnh hoặc tệp"
                  aria-expanded={attachmentMenuOpen}
                  disabled={loading}
                  onClick={() => setAttachmentMenuOpen(open => !open)}
                >
                  <Icon name="plus" />
                </button>
                {attachmentMenuOpen && (
                  <div className="create-chat-attachment-menu" role="menu">
                    <button type="button" role="menuitem" onClick={() => { setAttachmentMenuOpen(false); fileInputRef.current?.click(); }}>
                      <Icon name="upload" />Tải tệp
                    </button>
                    <button type="button" role="menuitem" onClick={() => void openCamera()}>
                      <Icon name="camera" />Chụp ảnh
                    </button>
                  </div>
                )}
              </div>
            )}

            <textarea
              ref={composerTextareaRef}
              id={pendingProblem ? "ambiguity-answer" : "workspace-description"}
              className="create-chat-textarea"
              rows={1}
              autoFocus
              value={composerValue}
              disabled={loading || Boolean(pendingProblem && questionTyping)}
              onChange={event => {
                if (pendingProblem && activeAmbiguity) {
                  onAnswersChange({ ...answers, [activeAmbiguity.code]: event.target.value });
                } else {
                  onDescriptionChange(event.target.value);
                }
              }}
              onKeyDown={event => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  event.currentTarget.form?.requestSubmit();
                }
              }}
              placeholder={pendingProblem ? "Nhập câu trả lời cho PhysLive AI…" : "Mô tả đề bài cần mô phỏng…"}
              aria-label={pendingProblem ? "Câu trả lời cho PhysLive AI" : "Mô tả đề bài cần mô phỏng"}
            />

            <span className="create-chat-composer-note">{token ? "PhysLive AI" : "Đăng nhập để dùng AI"}</span>
            <button
              className={`create-chat-send-button${pendingProblem ? " create-chat-send-button-wide" : ""}`}
              type="submit"
              aria-label={pendingProblem ? "Gửi câu trả lời cho AI" : "Gửi đề bài cho AI"}
              disabled={pendingProblem
                ? loading || questionTyping || !activeAmbiguity || !composerValue.trim()
                : (!description.trim() && !sourceFile) || loading}
            >
              {loading ? <span className="create-chat-spinner">⟳</span> : <Icon name="arrow" />}
              {pendingProblem && <span>{ambiguityStep < ambiguities.length - 1 ? "Tiếp tục" : "Gửi cho AI"}</span>}
            </button>
          </div>
        </form>
      </div>
    </div>
  );

  return inline ? panel : (
    <div
      className="modal-overlay"
      role="presentation"
      onClick={() => { if (canDismiss && !loading) onClose(); }}
    >
      {panel}
    </div>
  );
}
