import { useEffect, useRef, useState, type ChangeEvent, type FormEvent, type KeyboardEvent as ReactKeyboardEvent } from "react";
import type { Ambiguity, ConversationMessage, Problem } from "../../types/physlive";
import "../../styles/workspace-modal.css";
import { useCameraCapture } from "./create-simulation/useCameraCapture";
import { CreateChatMessages } from "./create-simulation/CreateChatMessages";
import { CreateChatSupport } from "./create-simulation/CreateChatSupport";
import { CreateChatComposer } from "./create-simulation/CreateChatComposer";
import { AssetSelectionReview } from "./create-simulation/AssetSelectionReview";

type Props = {
  token: string | null;
  description: string;
  onDescriptionChange: (value: string) => void;
  pendingProblem: Problem | null;
  assetProblem: Problem | null;
  onAssetDecision: (accepted: boolean) => void;
  onRetrySimulation: () => void;
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
  activeQuestionRecorded: boolean;
  conversation: ConversationMessage[];
  canDismiss: boolean;
  onClose: () => void;
  onCreate: (event: FormEvent) => void;
  onConfirmOcrReview: (event: FormEvent) => void;
  onConfirmAmbiguities: (event: FormEvent) => void;
  onBackAmbiguity: () => void;
  onResetComposer: () => void;
  sourceFile: File | null;
  sourceFileError: string;
  onSourceFileChange: (file: File | null) => void;
  inline?: boolean;
};

function getPanelCopy(ocrReviewRequired: boolean, pendingProblem: Problem | null) {
  if (pendingProblem) return { title: "AI đang làm rõ đề bài", subtitle: "Trả lời từng điểm chưa rõ. Solver chỉ chạy sau khi đủ dữ kiện." };
  if (ocrReviewRequired) return { title: "Kiểm tra nội dung từ ảnh", subtitle: "Rà soát nội dung nhận dạng trước khi AI tạo specification." };
  return { title: "Tạo mô phỏng mới", subtitle: "Trao đổi với PhysLive AI để xây dựng mô phỏng." };
}

function getComposerCopy(ocrReviewRequired: boolean, pendingProblem: Problem | null) {
  if (pendingProblem) return { placeholder: "Nhập câu trả lời cho PhysLive AI…", ariaLabel: "Câu trả lời cho PhysLive AI" };
  if (ocrReviewRequired) return { placeholder: "Kiểm tra và chỉnh nội dung nhận dạng…", ariaLabel: "Nội dung nhận dạng cần rà soát" };
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






export default function CreateSimulationModal({
  token,
  description,
  onDescriptionChange,
  pendingProblem,
  assetProblem,
  onAssetDecision,
  onRetrySimulation,
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
  activeQuestionRecorded,
  conversation,
  canDismiss,
  onClose,
  onCreate,
  onConfirmOcrReview,
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
  const hasChatContent = conversation.length > 0 || Boolean(stage || error || pendingProblem);
  const panelCopy = assetProblem
    ? { title: "Hình minh họa mô phỏng", subtitle: "Chọn hình có sẵn phù hợp với các vật trong đề bài." }
    : getPanelCopy(ocrReviewRequired, pendingProblem);
  const composerCopy = getComposerCopy(ocrReviewRequired, pendingProblem);
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
          activeQuestionRecorded={activeQuestionRecorded}
          ambiguityStep={ambiguityStep}
          loading={loading}
          questionTyping={questionTyping}
          typedQuestion={typedQuestion}
          stage={stage}
          error={error}
        />
        {assetProblem ? (
          <div className="create-chat-composer-wrap">
            <AssetSelectionReview
              selection={assetProblem.currentSpecification?.assetSelection}
              loading={loading}
              onDecision={onAssetDecision}
              onRetry={onRetrySimulation}
              onReset={onResetComposer}
            />
          </div>
        ) : <form id="create-sim-form" className="create-chat-composer-wrap" onSubmit={submitHandler}>
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
        </form>}
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
