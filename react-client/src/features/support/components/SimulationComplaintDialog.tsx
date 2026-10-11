import { useEffect, useState, type FormEvent } from "react";
import { createPortal } from "react-dom";
import axios from "axios";
import { mySupportItems, submitSimulationComplaint, type SimulationComplaint } from "../api/supportApi";
import { COMPLAINT_STATUS } from "../model/complaintStatus";
import "../styles/complaints.css";

const REASONS = [
  "Kết quả tính toán sai",
  "Chuyển động hoặc hình minh họa không đúng với đề bài",
  "Hiểu sai đề bài",
  "Thiếu hoặc sai đại lượng, đơn vị",
  "Vấn đề khác",
];

type Props = {
  /** Saved simulation the complaint is about; undefined while the simulation is not saved yet. */
  simulationId?: string;
  /** Problem statement, attached so the reviewer sees what was asked. */
  description?: string;
  /** Current parameter values, attached for the same reason. */
  parameters?: Record<string, number>;
  /** Opened from the account menu: only the list of sent complaints, no form. */
  historyOnly?: boolean;
  onClose: () => void;
};

/** Teacher side: report a wrong simulation to the reviewers and follow the answers. */
export default function SimulationComplaintDialog({ simulationId, description, parameters, historyOnly = false, onClose }: Readonly<Props>) {
  const [reason, setReason] = useState(REASONS[0]);
  const [detail, setDetail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [history, setHistory] = useState<SimulationComplaint[] | null>(null);
  const hasSimulation = Boolean(description?.trim());

  useEffect(() => {
    let active = true;
    mySupportItems().then(items => { if (active) setHistory(items.filter(item => item.kind === "COMPLAINT")); })
      .catch(() => { if (active) setHistory([]); });
    return () => { active = false; };
  }, []);
  useEffect(() => {
    const escape = (event: KeyboardEvent) => { if (event.key === "Escape") onClose(); };
    document.addEventListener("keydown", escape);
    return () => document.removeEventListener("keydown", escape);
  }, [onClose]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!hasSimulation || detail.trim().length < 10 || busy) return;
    setBusy(true); setError(""); setNotice("");
    const values = Object.entries(parameters ?? {}).map(([name, value]) => `${name} = ${value}`).join("; ");
    const content = [detail.trim(), "", "— Đề bài —", description?.trim(), values && `— Thông số đang dùng —\n${values}`].filter(Boolean).join("\n");
    try {
      const created = await submitSimulationComplaint(reason, content.slice(0, 10000), simulationId);
      setHistory(current => [created, ...(current ?? [])]);
      setDetail("");
      setNotice("Đã gửi khiếu nại. Người kiểm duyệt sẽ phản hồi tại đây.");
    } catch (cause) {
      const message = axios.isAxiosError<{ message?: string }>(cause) ? cause.response?.data?.message : undefined;
      setError(message || "Chưa gửi được khiếu nại. Vui lòng thử lại.");
    } finally { setBusy(false); }
  };

  return createPortal(<div className="complaint-overlay" onPointerDown={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="complaint-dialog" data-history-only={historyOnly || undefined} role="dialog" aria-modal="true" aria-labelledby="complaint-title">
      <header className="complaint-dialog__head">
        <div><h2 id="complaint-title">{historyOnly ? "Khiếu nại đã gửi" : "Báo lỗi mô phỏng"}</h2><p>{historyOnly ? "Trạng thái và phản hồi của người kiểm duyệt. Để gửi khiếu nại mới, mở mô phỏng rồi bấm “Báo lỗi” phía trên khung mô phỏng." : "Báo cho người kiểm duyệt khi mô phỏng tính sai hoặc không đúng với đề bài. Phản hồi sẽ hiện trong mục Khiếu nại đã gửi."}</p></div>
        <button type="button" className="complaint-close" aria-label="Đóng" onClick={onClose}>×</button>
      </header>
      {!historyOnly && <form className="complaint-form" onSubmit={submit}>
        {hasSimulation
          ? <p className="complaint-context"><strong>Mô phỏng đang mở:</strong> {description!.trim().slice(0, 220)}{description!.trim().length > 220 ? "…" : ""}</p>
          : <p className="complaint-context">Hãy mở hoặc dựng một mô phỏng trước, rồi quay lại đây để gửi khiếu nại về mô phỏng đó.</p>}
        <label>Vấn đề gặp phải
          <select value={reason} onChange={event => setReason(event.target.value)} disabled={!hasSimulation || busy}>{REASONS.map(value => <option key={value} value={value}>{value}</option>)}</select>
        </label>
        <label>Mô tả chi tiết
          <textarea value={detail} maxLength={4000} disabled={!hasSimulation || busy} onChange={event => setDetail(event.target.value)}
            placeholder="Ví dụ: Với v0 = 15 m/s và h = 20 m, tầm xa đúng phải là khoảng 30,3 m nhưng mô phỏng hiển thị 25 m." />
        </label>
        {error && <p className="complaint-error" role="alert">{error}</p>}
        {notice && <p className="complaint-notice" role="status">{notice}</p>}
        <div className="complaint-form__actions">
          <button type="submit" disabled={!hasSimulation || busy || detail.trim().length < 10}>{busy ? "Đang gửi…" : "Gửi khiếu nại"}</button>
        </div>
      </form>}
      <div className="complaint-history">
        {!historyOnly && <h3>Khiếu nại đã gửi</h3>}
        {history === null ? <p className="complaint-history__empty" role="status">Đang tải…</p>
          : history.length === 0 ? <p className="complaint-history__empty">Bạn chưa gửi khiếu nại nào.</p>
          : history.map(item => <article className="complaint-item" key={item.id}>
            <div className="complaint-item__top"><strong>{item.subject}</strong><span className="complaint-status" data-status={item.status}>{COMPLAINT_STATUS[item.status]}</span></div>
            <time dateTime={item.createdAt}>Gửi lúc {new Date(item.createdAt).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" })}</time>
            <p className="complaint-item__body">{item.content.split("\n— Đề bài —")[0].trim()}</p>
            {item.adminResponse && <p className="complaint-item__answer"><b>Phản hồi của người kiểm duyệt{item.responderName ? ` (${item.responderName})` : ""}</b>{item.adminResponse}</p>}
          </article>)}
      </div>
    </section>
  </div>, document.body);
}
