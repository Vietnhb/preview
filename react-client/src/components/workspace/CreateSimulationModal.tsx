import { useEffect, useRef, useState, type ChangeEvent, type FormEvent, type KeyboardEvent as ReactKeyboardEvent, type RefObject } from "react";
import type { Ambiguity, ConversationMessage, Problem, Specification } from "../../types/physlive";
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
  specificationReview: Specification | null;
  ocrReviewRequired: boolean;
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
  onConfirmOcrReview: (event: FormEvent) => void;
  onSaveSpecification: (specification: Pick<Specification, "objects" | "quantities" | "relations">) => void;
  onConfirmSpecification: (specification: Pick<Specification, "objects" | "quantities" | "relations">) => void;
  onConfirmAmbiguities: (event: FormEvent) => void;
  onBackAmbiguity: () => void;
  onResetComposer: () => void;
  sourceFile: File | null;
  sourceFileError: string;
  onSourceFileChange: (file: File | null) => void;
  inline?: boolean;
};

type SpecificationDraft = { objects: string; quantities: string; relations: string };

function getPanelCopy(ocrReviewRequired: boolean, pendingProblem: Problem | null) {
  if (pendingProblem) return { title: "AI đang làm rõ đề bài", subtitle: "Trả lời từng điểm chưa rõ. Solver chỉ chạy sau khi đủ dữ kiện." };
  if (ocrReviewRequired) return { title: "Kiểm tra nội dung từ ảnh", subtitle: "Rà soát nội dung nhận dạng trước khi AI tạo specification." };
  return { title: "Tạo mô phỏng mới", subtitle: "Trao đổi với PhysLive AI để xây dựng mô phỏng." };
}

function getComposerCopy(specificationReview: Specification | null, ocrReviewRequired: boolean, pendingProblem: Problem | null) {
  if (pendingProblem) return { placeholder: "Nhập câu trả lời cho PhysLive AI…", ariaLabel: "Câu trả lời cho PhysLive AI" };
  if (ocrReviewRequired) return { placeholder: "Kiểm tra và chỉnh nội dung nhận dạng…", ariaLabel: "Nội dung nhận dạng cần rà soát" };
  if (specificationReview) return { placeholder: "Specification đã hiển thị ở trên…", ariaLabel: "Specification đang được rà soát" };
  return { placeholder: "Mô tả đề bài cần mô phỏng…", ariaLabel: "Mô tả đề bài cần mô phỏng" };
}

function getSubmitHandler(ocrReviewRequired: boolean, pendingProblem: Problem | null, onCreate: Props["onCreate"], onConfirmOcrReview: Props["onConfirmOcrReview"], onConfirmAmbiguities: Props["onConfirmAmbiguities"]) {
  if (pendingProblem) return onConfirmAmbiguities;
  if (ocrReviewRequired) return onConfirmOcrReview;
  return onCreate;
}

function getSendLabel(pendingProblem: Problem | null, ambiguityStep: number, ambiguityCount: number) {
  if (!pendingProblem) return "Xác nhận & phân tích";
  return ambiguityStep < ambiguityCount - 1 ? "Tiếp tục" : "Gửi cho AI";
}

function getSendAriaLabel(ocrReviewRequired: boolean, pendingProblem: Problem | null) {
  if (pendingProblem) return "Gửi câu trả lời cho AI";
  if (ocrReviewRequired) return "Xác nhận nội dung và gửi cho AI";
  return "Gửi đề bài cho AI";
}

function parseSpecificationDraft(draft: SpecificationDraft): Pick<Specification, "objects" | "quantities" | "relations"> {
  const objects = JSON.parse(draft.objects) as Specification["objects"];
  const quantities = JSON.parse(draft.quantities) as Specification["quantities"];
  const relations = JSON.parse(draft.relations) as Specification["relations"];
  if (!Array.isArray(objects) || !Array.isArray(quantities) || !Array.isArray(relations)) {
    throw new TypeError("Ba trường specification phải là mảng JSON.");
  }
  return { objects, quantities, relations };
}

function applySpecificationDraft(draft: SpecificationDraft, onError: (message: string) => void, onApply: (value: Pick<Specification, "objects" | "quantities" | "relations">) => void) {
  try {
    onApply(parseSpecificationDraft(draft));
    onError("");
  } catch (error) {
    onError(error instanceof Error ? error.message : "JSON specification chưa hợp lệ.");
  }
}

type SpecificationReviewEditorProps = {
  specification: Specification;
  loading: boolean;
  ambiguities: Ambiguity[];
  onSave: (value: Pick<Specification, "objects" | "quantities" | "relations">) => void;
  onConfirm: (value: Pick<Specification, "objects" | "quantities" | "relations">) => void;
};

function SpecificationReviewEditor({ specification, loading, ambiguities, onSave, onConfirm }: Readonly<SpecificationReviewEditorProps>) {
  const [draft, setDraft] = useState<SpecificationDraft>(() => ({
    objects: JSON.stringify(specification.objects ?? [], null, 2),
    quantities: JSON.stringify(specification.quantities ?? [], null, 2),
    relations: JSON.stringify(specification.relations ?? [], null, 2),
  }));
  const [error, setError] = useState("");
  const handleChange = (field: keyof SpecificationDraft, value: string) => setDraft(current => ({ ...current, [field]: value }));
  const handleSave = () => applySpecificationDraft(draft, setError, onSave);
  const handleConfirm = () => applySpecificationDraft(draft, setError, onConfirm);

  return <section className="create-chat-specification-review" aria-label="Rà soát specification">
    <div className="create-chat-specification-heading"><div><strong>Rà soát specification</strong><span>Giáo viên có thể sửa trực tiếp objects, quantities và relations trước khi chạy.</span></div><span className={`create-chat-specification-status ${specification.confirmationState === "UNRESOLVED" ? "warning" : "ready"}`}>{specification.confirmationState === "UNRESOLVED" ? "Cần bổ sung" : "Đã đọc"}</span></div>
    <div className="create-chat-specification-grid">{(["objects", "quantities", "relations"] as const).map(field => <label key={field}><span>{field}</span><textarea rows={5} value={draft[field]} onChange={event => handleChange(field, event.target.value)} spellCheck={false} aria-label={`Chỉnh ${field} specification`} /></label>)}</div>
    {error && <p className="create-chat-specification-error" role="alert">{error}</p>}
    <div className="create-chat-specification-actions"><button type="button" className="create-chat-secondary-action" onClick={handleSave} disabled={loading}>Lưu thay đổi</button>{!ambiguities.length && <button type="button" className="create-chat-specification-confirm" onClick={handleConfirm} disabled={loading}>Xác nhận &amp; chạy mô phỏng</button>}</div>
  </section>;
}

type CameraCapture = {
  cameraOpen: boolean;
  cameraVideoRef: RefObject<HTMLVideoElement | null>;
  cameraCanvasRef: RefObject<HTMLCanvasElement | null>;
  cameraError: string;
  openCamera: () => Promise<void>;
  capturePhoto: () => void;
  stopCamera: () => void;
};

function useCameraCapture(onSourceFileChange: (file: File) => void): CameraCapture {
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

  const openCamera = async () => {
    setCameraError("");
    if (!globalThis.navigator.mediaDevices?.getUserMedia) {
      setCameraError("Trình duyệt không hỗ trợ camera. Bạn có thể dùng Tải tệp.");
      return;
    }

    try {
      stopCamera();
      const stream = await globalThis.navigator.mediaDevices.getUserMedia({
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

  return { cameraOpen, cameraVideoRef, cameraCanvasRef, cameraError, openCamera, capturePhoto, stopCamera };
}

type ChatMessagesProps = {
  messagesRef: RefObject<HTMLDivElement | null>;
  conversation: ConversationMessage[];
  hasChatContent: boolean;
  pendingProblem: Problem | null;
  activeAmbiguity: Ambiguity | undefined;
  ambiguityStep: number;
  loading: boolean;
  questionTyping: boolean;
  typedQuestion: string;
  stage: string;
  error: string;
  specificationReview: Specification | null;
  ambiguities: Ambiguity[];
  onSaveSpecification: (value: Pick<Specification, "objects" | "quantities" | "relations">) => void;
  onConfirmSpecification: (value: Pick<Specification, "objects" | "quantities" | "relations">) => void;
};

function CreateChatMessages({ messagesRef, conversation, hasChatContent, pendingProblem, activeAmbiguity, ambiguityStep, loading, questionTyping, typedQuestion, stage, error, specificationReview, onSaveSpecification, ambiguities, onConfirmSpecification }: Readonly<ChatMessagesProps>) {
  return <div className="create-chat-messages" ref={messagesRef} aria-live="polite">
    {hasChatContent === false ? <div className="create-chat-empty-state"><h3>Bạn muốn mô phỏng hiện tượng gì?</h3><p>Nhập đề bài, mô tả hiện tượng hoặc tải ảnh/PDF.<br />PhysLive AI sẽ phân tích và hỏi thêm nếu cần.</p></div> : <>
      {conversation.map(message => <div className={`create-chat-message ${message.role}`} key={message.id}><span className="create-chat-avatar">{message.role === "assistant" ? "AI" : "Bạn"}</span><div className="create-chat-bubble"><p>{message.text}</p></div></div>)}
      {pendingProblem && activeAmbiguity && !loading && <div className="create-chat-message assistant create-chat-question" key={`${activeAmbiguity.code}-${ambiguityStep}`}><span className="create-chat-avatar">AI</span><div className="create-chat-bubble"><p>{questionTyping ? (typedQuestion || "AI đang viết…") : activeAmbiguity.question}</p></div></div>}
      {stage && <div className="create-chat-message assistant create-chat-status"><span className="create-chat-avatar">AI</span><div className="create-chat-bubble"><span className="create-chat-status-dot" aria-hidden="true" />{stage}</div></div>}
      {error && <div className="create-chat-message assistant create-chat-error" role="alert"><span className="create-chat-avatar">AI</span><div className="create-chat-bubble">Mình chưa thể tiếp tục: {error}</div></div>}
    </>}
    {specificationReview && <SpecificationReviewEditor key={JSON.stringify(specificationReview)} specification={specificationReview} loading={loading} ambiguities={ambiguities} onSave={onSaveSpecification} onConfirm={onConfirmSpecification} />}
  </div>;
}

type ChatSupportProps = {
  pendingProblem: Problem | null;
  activeAmbiguity: Ambiguity | undefined;
  questionTyping: boolean;
  answers: Record<string, string>;
  onAnswerOption: (option: string) => void;
  sourceFile: File | null;
  loading: boolean;
  cameraOpen: boolean;
  cameraVideoRef: RefObject<HTMLVideoElement | null>;
  cameraCanvasRef: RefObject<HTMLCanvasElement | null>;
  cameraError: string;
  sourceFileError: string;
  onCapturePhoto: () => void;
  onStopCamera: () => void;
  onRemoveFile: () => void;
  onResetComposer: () => void;
  onBackAmbiguity: () => void;
  ambiguityStep: number;
};

function CreateChatSupport({ pendingProblem, activeAmbiguity, questionTyping, answers, onAnswerOption, sourceFile, loading, cameraOpen, cameraVideoRef, cameraCanvasRef, cameraError, sourceFileError, onCapturePhoto, onStopCamera, onRemoveFile, onResetComposer, onBackAmbiguity, ambiguityStep }: Readonly<ChatSupportProps>) {
  return <div className="create-chat-composer-support">
    {pendingProblem && activeAmbiguity && !questionTyping && activeAmbiguity.options && activeAmbiguity.options.length > 0 && <div className="create-chat-options" aria-label="Gợi ý trả lời">{activeAmbiguity.options.map(option => <button key={option} type="button" className={answers[activeAmbiguity.code] === option ? "selected" : ""} onClick={() => onAnswerOption(option)}>{option}</button>)}</div>}
    {!pendingProblem && sourceFile && <div className="source-file-chip"><span className="source-file-icon"><Icon name="file" /></span><span className="source-file-meta"><strong>{sourceFile.name}</strong><small>{formatFileSize(sourceFile.size)}</small></span><button type="button" aria-label="Xóa tệp đính kèm" disabled={loading} onClick={onRemoveFile}><Icon name="close" /></button></div>}
    {!pendingProblem && cameraOpen && <div className="camera-capture-panel"><video ref={cameraVideoRef} className="camera-capture-preview" autoPlay muted playsInline /><canvas ref={cameraCanvasRef} hidden /><div className="camera-capture-actions"><button type="button" className="source-picker-button" disabled={loading} onClick={onCapturePhoto}><Icon name="camera" />Chụp ảnh</button><button type="button" className="source-picker-button secondary" disabled={loading} onClick={onStopCamera}><Icon name="close" />Đóng camera</button></div></div>}
    {!pendingProblem && cameraError && <p className="source-file-error" role="alert">{cameraError}</p>}
    {!pendingProblem && sourceFileError && <p className="source-file-error" role="alert">{sourceFileError}</p>}
    {pendingProblem && activeAmbiguity && <small className="create-chat-input-hint">{activeAmbiguity.code} · {activeAmbiguity.fieldPath ?? activeAmbiguity.field}</small>}
    {pendingProblem && <div className="create-chat-composer-actions"><button type="button" className="create-chat-secondary-action" disabled={loading} onClick={onResetComposer}>Nhập đề khác</button><button type="button" className="create-chat-secondary-action" disabled={loading || ambiguityStep === 0} onClick={onBackAmbiguity}>Quay lại</button></div>}
  </div>;
}

type ChatComposerProps = {
  fileInputRef: RefObject<HTMLInputElement | null>;
  attachmentControlRef: RefObject<HTMLDivElement | null>;
  onFileInput: (event: ChangeEvent<HTMLInputElement>) => void;
  onFilePicker: () => void;
  onOpenCamera: () => void;
  attachmentMenuOpen: boolean;
  onToggleAttachmentMenu: () => void;
  pendingProblem: Problem | null;
  composerTextareaRef: RefObject<HTMLTextAreaElement | null>;
  composerValue: string;
  onComposerChange: (value: string) => void;
  onComposerKeyDown: (event: ReactKeyboardEvent<HTMLTextAreaElement>) => void;
  loading: boolean;
  questionTyping: boolean;
  specificationReview: Specification | null;
  ocrReviewRequired: boolean;
  composerPlaceholder: string;
  composerAriaLabel: string;
  token: string | null;
  sendLabel: string;
  sendAriaLabel: string;
  activeAmbiguity: Ambiguity | undefined;
  description: string;
  sourceFile: File | null;
};

function CreateChatComposer({ fileInputRef, attachmentControlRef, onFileInput, onFilePicker, onOpenCamera, attachmentMenuOpen, onToggleAttachmentMenu, pendingProblem, composerTextareaRef, composerValue, onComposerChange, onComposerKeyDown, loading, questionTyping, specificationReview, ocrReviewRequired, composerPlaceholder, composerAriaLabel, token, sendLabel, sendAriaLabel, activeAmbiguity, description, sourceFile }: Readonly<ChatComposerProps>) {
  const sendDisabled = pendingProblem
    ? loading || questionTyping || !activeAmbiguity || !composerValue.trim()
    : Boolean(specificationReview) || (!description.trim() && !sourceFile) || loading;
  return <div className="create-chat-composer"><input ref={fileInputRef} type="file" accept="image/png,image/jpeg,image/webp,image/gif,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,.pdf,.docx,.txt" hidden onChange={onFileInput} />
    {!pendingProblem && <div className="create-chat-attachment-control" ref={attachmentControlRef}><button type="button" className="create-chat-plus-button" aria-label="Thêm ảnh hoặc tệp" aria-expanded={attachmentMenuOpen} disabled={loading} onClick={onToggleAttachmentMenu}><Icon name="plus" /></button>{attachmentMenuOpen && <div className="create-chat-attachment-menu" role="menu"><button type="button" role="menuitem" onClick={onFilePicker}><Icon name="upload" />Tải tệp</button><button type="button" role="menuitem" onClick={onOpenCamera}><Icon name="camera" />Chụp ảnh</button></div>}</div>}
    <textarea ref={composerTextareaRef} id={pendingProblem ? "ambiguity-answer" : "workspace-description"} className="create-chat-textarea" rows={1} autoFocus value={composerValue} disabled={loading || Boolean(pendingProblem && questionTyping) || Boolean(specificationReview && !pendingProblem && !ocrReviewRequired)} onChange={event => onComposerChange(event.target.value)} onKeyDown={onComposerKeyDown} placeholder={composerPlaceholder} aria-label={composerAriaLabel} />
    <span className="create-chat-composer-note">{token ? "PhysLive AI" : "Đăng nhập để dùng AI"}</span><button className={`create-chat-send-button${pendingProblem ? " create-chat-send-button-wide" : ""}`} type="submit" aria-label={sendAriaLabel} disabled={sendDisabled}>{loading ? <span className="create-chat-spinner">⟳</span> : <Icon name="arrow" />}{(pendingProblem || ocrReviewRequired) && <span>{sendLabel}</span>}</button>
  </div>;
}

export default function CreateSimulationModal({
  token,
  description,
  onDescriptionChange,
  pendingProblem,
  specificationReview,
  ocrReviewRequired,
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
  onConfirmOcrReview,
  onSaveSpecification,
  onConfirmSpecification,
  onConfirmAmbiguities,
  onBackAmbiguity,
  onResetComposer,
  sourceFile,
  sourceFileError,
  onSourceFileChange,
  inline = false,
}: Readonly<Props>) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const messagesRef = useRef<HTMLDivElement>(null);
  const composerTextareaRef = useRef<HTMLTextAreaElement>(null);
  const attachmentControlRef = useRef<HTMLDivElement>(null);
  const [attachmentMenuOpen, setAttachmentMenuOpen] = useState(false);
  const camera = useCameraCapture(onSourceFileChange);

  const handleFileInput = (event: ChangeEvent<HTMLInputElement>) => {
    camera.stopCamera();
    setAttachmentMenuOpen(false);
    onSourceFileChange(event.target.files?.[0] ?? null);
    event.target.value = "";
  };

  const composerValue = pendingProblem && activeAmbiguity
    ? answers[activeAmbiguity.code] ?? ""
    : description;
  const hasChatContent = conversation.length > 0 || Boolean(stage || error || pendingProblem || specificationReview);
  const panelCopy = getPanelCopy(ocrReviewRequired, pendingProblem);
  const composerCopy = getComposerCopy(specificationReview, ocrReviewRequired, pendingProblem);
  const submitHandler = getSubmitHandler(ocrReviewRequired, pendingProblem, onCreate, onConfirmOcrReview, onConfirmAmbiguities);
  const sendLabel = getSendLabel(pendingProblem, ambiguityStep, ambiguities.length);
  const sendAriaLabel = getSendAriaLabel(ocrReviewRequired, pendingProblem);

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

  const handleAnswerOption = (option: string) => {
    if (activeAmbiguity) onAnswersChange({ ...answers, [activeAmbiguity.code]: option });
  };
  const handleComposerChange = (value: string) => {
    if (pendingProblem && activeAmbiguity) onAnswersChange({ ...answers, [activeAmbiguity.code]: value });
    else onDescriptionChange(value);
  };
  const handleComposerKeyDown = (event: ReactKeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key !== "Enter" || event.shiftKey) return;
    event.preventDefault();
    event.currentTarget.form?.requestSubmit();
  };
  const handleFilePicker = () => {
    setAttachmentMenuOpen(false);
    fileInputRef.current?.click();
  };

  const panel = (
    <div className={inline ? "inline-create-panel" : "modal-container"} aria-labelledby="create-sim-title">
      <header className="create-chat-header">
        <div className="create-chat-title-group"><h2 id="create-sim-title">{panelCopy.title}</h2><p>{panelCopy.subtitle}</p></div>
        {canDismiss && <button type="button" className="create-chat-close" aria-label="Đóng" disabled={loading} onClick={onClose}>×</button>}
      </header>
      <div className="create-chat">
        <CreateChatMessages
          messagesRef={messagesRef}
          conversation={conversation}
          hasChatContent={hasChatContent}
          pendingProblem={pendingProblem}
          activeAmbiguity={activeAmbiguity}
          ambiguityStep={ambiguityStep}
          loading={loading}
          questionTyping={questionTyping}
          typedQuestion={typedQuestion}
          stage={stage}
          error={error}
          specificationReview={specificationReview}
          onSaveSpecification={onSaveSpecification}
          ambiguities={ambiguities}
          onConfirmSpecification={onConfirmSpecification}
        />
        <form id="create-sim-form" className="create-chat-composer-wrap" onSubmit={submitHandler}>
          <CreateChatSupport
            pendingProblem={pendingProblem}
            activeAmbiguity={activeAmbiguity}
            questionTyping={questionTyping}
            answers={answers}
            onAnswerOption={handleAnswerOption}
            sourceFile={sourceFile}
            loading={loading}
            cameraOpen={camera.cameraOpen}
            cameraVideoRef={camera.cameraVideoRef}
            cameraCanvasRef={camera.cameraCanvasRef}
            cameraError={camera.cameraError}
            sourceFileError={sourceFileError}
            onCapturePhoto={camera.capturePhoto}
            onStopCamera={camera.stopCamera}
            onRemoveFile={() => onSourceFileChange(null)}
            onResetComposer={onResetComposer}
            onBackAmbiguity={onBackAmbiguity}
            ambiguityStep={ambiguityStep}
          />
          <CreateChatComposer
            fileInputRef={fileInputRef}
            attachmentControlRef={attachmentControlRef}
            onFileInput={handleFileInput}
            onFilePicker={handleFilePicker}
            onOpenCamera={() => { setAttachmentMenuOpen(false); void camera.openCamera(); }}
            attachmentMenuOpen={attachmentMenuOpen}
            onToggleAttachmentMenu={() => setAttachmentMenuOpen(open => !open)}
            pendingProblem={pendingProblem}
            composerTextareaRef={composerTextareaRef}
            composerValue={composerValue}
            onComposerChange={handleComposerChange}
            onComposerKeyDown={handleComposerKeyDown}
            loading={loading}
            questionTyping={questionTyping}
            specificationReview={specificationReview}
            ocrReviewRequired={ocrReviewRequired}
            composerPlaceholder={composerCopy.placeholder}
            composerAriaLabel={composerCopy.ariaLabel}
            token={token}
            sendLabel={sendLabel}
            sendAriaLabel={sendAriaLabel}
            activeAmbiguity={activeAmbiguity}
            description={description}
            sourceFile={sourceFile}
          />
        </form>
      </div>
    </div>
  );

  return inline ? panel : (
    <dialog
      open
      className="modal-overlay"
      onPointerDown={event => { if (event.target === event.currentTarget && canDismiss && !loading) onClose(); }}
    >
      {panel}
    </dialog>
  );
}
