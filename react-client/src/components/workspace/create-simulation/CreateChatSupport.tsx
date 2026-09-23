import type { RefObject } from "react";
import type { Ambiguity, Problem } from "../../../types/physlive";
import Icon from "../../common/LearningIcon";
import { formatFileSize } from "./createSimulationUtils";

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
  onBeginRevision: () => void;
  revisingProblem: boolean;
  onBackAmbiguity: () => void;
  ambiguityStep: number;
};

export function CreateChatSupport({ pendingProblem, activeAmbiguity, questionTyping, answers, onAnswerOption, sourceFile, loading, cameraOpen, cameraVideoRef, cameraCanvasRef, cameraError, sourceFileError, onCapturePhoto, onStopCamera, onRemoveFile, onResetComposer, onBeginRevision, revisingProblem, onBackAmbiguity, ambiguityStep }: Readonly<ChatSupportProps>) {
  return <div className="create-chat-composer-support">
    {pendingProblem && activeAmbiguity && !questionTyping && activeAmbiguity.options && activeAmbiguity.options.length > 0 && <div className="create-chat-options" aria-label="Gợi ý trả lời">{activeAmbiguity.options.map(option => <button key={option} type="button" className={answers[activeAmbiguity.code] === option ? "selected" : ""} onClick={() => onAnswerOption(option)}>{option}</button>)}</div>}
    {!pendingProblem && sourceFile && <div className="source-file-chip"><span className="source-file-icon"><Icon name="file" /></span><span className="source-file-meta"><strong>{sourceFile.name}</strong><small>{formatFileSize(sourceFile.size)}</small></span><button type="button" aria-label="Xóa tệp đính kèm" disabled={loading} onClick={onRemoveFile}><Icon name="close" /></button></div>}
    {!pendingProblem && cameraOpen && <div className="camera-capture-panel"><video ref={cameraVideoRef} className="camera-capture-preview" autoPlay muted playsInline /><canvas ref={cameraCanvasRef} hidden /><div className="camera-capture-actions"><button type="button" className="source-picker-button" disabled={loading} onClick={onCapturePhoto}><Icon name="camera" />Chụp ảnh</button><button type="button" className="source-picker-button secondary" disabled={loading} onClick={onStopCamera}><Icon name="close" />Đóng camera</button></div></div>}
    {!pendingProblem && cameraError && <p className="source-file-error" role="alert">{cameraError}</p>}
    {!pendingProblem && sourceFileError && <p className="source-file-error" role="alert">{sourceFileError}</p>}
    {pendingProblem && <div className="create-chat-composer-actions"><button type="button" className="create-chat-secondary-action" disabled={loading} onClick={onBeginRevision}>Sửa đề bài</button><button type="button" className="create-chat-secondary-action" disabled={loading} onClick={onResetComposer}>Nhập đề khác</button><button type="button" className="create-chat-secondary-action" disabled={loading || ambiguityStep === 0} onClick={onBackAmbiguity}>Quay lại</button></div>}
    {revisingProblem && <div className="create-chat-composer-actions"><button type="button" className="create-chat-secondary-action" disabled={loading} onClick={onResetComposer}>Nhập đề khác</button></div>}
  </div>;
}
