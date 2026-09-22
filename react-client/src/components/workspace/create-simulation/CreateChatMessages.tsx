import type { RefObject } from "react";
import type {
  Ambiguity,
  ConversationMessage,
  Problem,
} from "../../../types/physlive";

type ChatMessagesProps = {
  messagesRef: RefObject<HTMLDivElement | null>;
  conversation: ConversationMessage[];
  hasChatContent: boolean;
  pendingProblem: Problem | null;
  activeAmbiguity: Ambiguity | undefined;
  activeQuestionRecorded: boolean;
  ambiguityStep: number;
  loading: boolean;
  questionTyping: boolean;
  typedQuestion: string;
  stage: string;
  error: string;
};

export function CreateChatMessages({
  messagesRef,
  conversation,
  hasChatContent,
  pendingProblem,
  activeAmbiguity,
  activeQuestionRecorded,
  ambiguityStep,
  loading,
  questionTyping,
  typedQuestion,
  stage,
  error,
}: Readonly<ChatMessagesProps>) {
  return (
    <div className="create-chat-messages" ref={messagesRef} aria-live="polite">
      {hasChatContent === false ? (
        <div className="create-chat-empty-state">
          <h3>Bạn muốn mô phỏng hiện tượng gì?</h3>
          <p>
            Nhập đề bài, mô tả hiện tượng hoặc tải ảnh/PDF.
            <br />
            PhysLive AI sẽ phân tích và hỏi thêm nếu cần.
          </p>
        </div>
      ) : (
        <>
          {conversation.map((message) => (
            <div
              className={`create-chat-message ${message.role}`}
              key={message.id}
            >
              <span className="create-chat-avatar">
                {message.role === "assistant" ? "AI" : "Bạn"}
              </span>
              <div className="create-chat-bubble">
                <p>{message.text}</p>
              </div>
            </div>
          ))}
          {pendingProblem && activeAmbiguity && !activeQuestionRecorded && !loading && (
            <div
              className="create-chat-message assistant create-chat-question"
              key={`${activeAmbiguity.code}-${ambiguityStep}`}
            >
              <span className="create-chat-avatar">AI</span>
              <div className="create-chat-bubble">
                <p>
                  {questionTyping ? typedQuestion : activeAmbiguity.question}
                </p>
              </div>
            </div>
          )}
          {stage && (
            <div
              className="create-chat-message assistant create-chat-status"
              role="status"
            >
              <div className="create-chat-bubble">
                <span className="create-chat-status-dot" aria-hidden="true" />
                {stage}
              </div>
            </div>
          )}
          {error && (
            <div
              className="create-chat-message assistant create-chat-error"
              role="alert"
            >
              <div className="create-chat-bubble">{error}</div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
