import type { FormEvent } from "react";

/**
 * A clarifying question from the planner, with the options it offers (one per row) and a free answer.
 * The chosen option is sent as its full text, so the planner reads a complete answer.
 * While the answer is analysed the card says so and locks.
 */
export default function ClarifyQuestion({ question, choices, answer, onAnswerChange, onSubmit, busy }: {
  question: string; choices: readonly string[]; answer: string; onAnswerChange: (value: string) => void;
  onSubmit: (event: FormEvent) => void; busy: boolean;
}) {
  const picked = choices.indexOf(answer);
  const own = picked < 0 ? answer : "";

  return <form className="clarify" onSubmit={onSubmit} aria-busy={busy}>
    <p className="clarify__lead">{question}</p>
    {choices.length > 0 && <fieldset className="clarify__choices" disabled={busy}>
      <legend className="clarify__label">Chọn một phương án</legend>
      {choices.map((choice, index) => <label key={index} className="clarify__choice" data-picked={picked === index}>
        <input type="radio" name="clarify-choice" checked={picked === index} onChange={() => onAnswerChange(choice)} />
        <span className="clarify__index">{index + 1}</span>
        <span>{choice}</span>
      </label>)}
    </fieldset>}
    <label className="clarify__label" htmlFor="simulation-answer">{choices.length ? "Hoặc tự trả lời" : "Câu trả lời của bạn"}</label>
    <textarea id="simulation-answer" rows={choices.length ? 2 : 3} value={own} disabled={busy}
      onChange={event => onAnswerChange(event.target.value)} />
    <div className="clarify__actions">
      <button className="simulation-primary-button" disabled={busy || !answer.trim()}>
        {busy ? <><span className="sim-spinner" aria-hidden="true" /> Đang phân tích câu trả lời…</> : "Trả lời"}
      </button>
      {busy && <span className="clarify__wait" role="status">AI đang đọc câu trả lời và dựng lại tình huống, thường mất 10–40 giây.</span>}
    </div>
  </form>;
}
