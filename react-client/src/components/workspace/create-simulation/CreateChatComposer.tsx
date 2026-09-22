import type { ChangeEvent, KeyboardEvent as ReactKeyboardEvent, RefObject } from "react";
import type { Ambiguity, Problem } from "../../../types/physlive";
import Icon from "../../common/LearningIcon";

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

export function CreateChatComposer({ fileInputRef, attachmentControlRef, onFileInput, onFilePicker, onOpenCamera, attachmentMenuOpen, onToggleAttachmentMenu, pendingProblem, composerTextareaRef, composerValue, onComposerChange, onComposerKeyDown, loading, questionTyping, ocrReviewRequired, composerPlaceholder, composerAriaLabel, token, sendLabel, sendAriaLabel, activeAmbiguity, description, sourceFile }: Readonly<ChatComposerProps>) {
  const sendDisabled = pendingProblem
    ? loading || questionTyping || !activeAmbiguity || !composerValue.trim()
    : (!description.trim() && !sourceFile) || loading;
  return <div className="create-chat-composer"><input ref={fileInputRef} type="file" accept="image/png,image/jpeg,image/webp,image/gif,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,.pdf,.docx,.txt" hidden onChange={onFileInput} />
    {!pendingProblem && <div className="create-chat-attachment-control" ref={attachmentControlRef}><button type="button" className="create-chat-plus-button" aria-label="Thêm ảnh hoặc tệp" aria-expanded={attachmentMenuOpen} disabled={loading} onClick={onToggleAttachmentMenu}><Icon name="plus" /></button>{attachmentMenuOpen && <div className="create-chat-attachment-menu" role="menu"><button type="button" role="menuitem" onClick={onFilePicker}><Icon name="upload" />Tải tệp</button><button type="button" role="menuitem" onClick={onOpenCamera}><Icon name="camera" />Chụp ảnh</button></div>}</div>}
    <textarea ref={composerTextareaRef} id={pendingProblem ? "ambiguity-answer" : "workspace-description"} className="create-chat-textarea" rows={1} autoFocus value={composerValue} disabled={loading || Boolean(pendingProblem && questionTyping)} onChange={event => onComposerChange(event.target.value)} onKeyDown={onComposerKeyDown} placeholder={composerPlaceholder} aria-label={composerAriaLabel} />
    <span className="create-chat-composer-note">{token ? "PhysLive AI" : "Đăng nhập để dùng AI"}</span><button className={`create-chat-send-button${pendingProblem ? " create-chat-send-button-wide" : ""}`} type="submit" aria-label={sendAriaLabel} disabled={sendDisabled}>{loading ? <span className="create-chat-spinner">⟳</span> : <Icon name="arrow" />}{(pendingProblem || ocrReviewRequired) && <span>{sendLabel}</span>}</button>
  </div>;
}
