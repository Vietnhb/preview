import { useState, type FormEvent } from "react";
import api from "../../../api/axios";
import { LoadState } from "../../operations/OperationsKit";
import { useAction, useResource } from "../../operations/operationsData";
import type { Specification } from "../../../types/physlive";
import type { ReviewItem } from "./reviewerTypes";

export function QueueTab() {
  const resource = useResource<ReviewItem[]>("/reviewer/ambiguities");
  const [selectedId, setSelectedId] = useState("");
  const [query, setQuery] = useState("");
  const items = (resource.data ?? []).filter((item) => `${item.topic} ${item.question} ${item.problemText}`.toLowerCase().includes(query.toLowerCase()));
  const selected = items.find((item) => item.id === selectedId) ?? items[0];

  return (
    <section className="modern-card reviewer-queue-card">
      <div className="modern-card-header">
        <div><h2>Hàng đợi xử lý ambiguity</h2><p>Mỗi ca được claim trước khi resolve để tránh hai reviewer sửa cùng một bản ghi.</p></div>
        <button type="button" className="role-switch-pill" onClick={resource.refresh} disabled={resource.loading}>Làm mới</button>
      </div>
      <LoadState {...resource} />
      <div className="reviewer-queue-search"><input aria-label="Tìm trong hàng đợi" placeholder="Tìm đề bài, câu hỏi hoặc chủ đề…" value={query} onChange={(event) => setQuery(event.target.value)} /></div>
      {!resource.loading && !resource.error && items.length === 0 ? <div style={{ padding: "40px", textAlign: "center" }}><strong>Hàng đợi trống</strong><p>Không có ambiguity đang chờ xử lý.</p></div> : <div className="reviewer-queue-layout">
        <aside className="reviewer-queue-list" aria-label="Danh sách ca cần review">
          {items.map((item) => <button key={item.id} type="button" className={`reviewer-queue-item${selected?.id === item.id ? " active" : ""}`} onClick={() => setSelectedId(item.id)}>
            <div className="reviewer-queue-item-meta"><span className="status-pill draft">{item.topic || "Vật lý"}</span><small>{item.fieldPath}</small></div>
            <strong>{item.question}</strong><p>{item.problemText?.slice(0, 100)}…</p>
            {item.claimedBy && <small>Đã được claim; hết hạn {item.claimExpiresAt ? new Date(item.claimExpiresAt).toLocaleTimeString("vi-VN") : "chưa rõ"}</small>}
          </button>)}
        </aside>
        <main className="reviewer-queue-detail">{selected && <ResolutionForm key={selected.id} item={selected} onResolved={resource.refresh} />}</main>
      </div>}
    </section>
  );
}

function ResolutionForm({ item, onResolved }: Readonly<{ item: ReviewItem; onResolved: () => void }>) {
  const [answer, setAnswer] = useState("");
  const [comment, setComment] = useState("");
  const [claimed, setClaimed] = useState(false);
  const action = useAction();

  const claim = async () => {
    await action.run(async () => {
      await api.post(`/reviewer/ambiguities/${item.id}/claim`);
      setClaimed(true);
    }, "Đã claim ca xử lý trong 30 phút.");
  };

  const release = async () => {
    const ok = await action.run(() => api.post(`/reviewer/ambiguities/${item.id}/release`), "Đã release ca xử lý.");
    if (ok) setClaimed(false);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const ok = await action.run(async () => {
      if (!claimed) await api.post(`/reviewer/ambiguities/${item.id}/claim`);
      const response = await api.post<Specification>(`/reviewer/ambiguities/${item.id}/resolve`, { answer: answer.trim(), comment: comment.trim() });
      const open = response.data.ambiguityCases ?? response.data.ambiguities ?? [];
      if (open.some((ambiguity) => ambiguity.code === item.code && ambiguity.status === "OPEN")) throw new Error("Ca vẫn còn ambiguity mở; hãy bổ sung giá trị hoặc hướng chuẩn.");
    }, "Đã lưu kết luận chuyên môn.");
    if (ok) onResolved();
  };

  return <article className="reviewer-resolution-form">
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "10px" }}><h3 className="reviewer-detail-title">Đề bài gốc</h3><div style={{ display: "flex", gap: "6px" }}>{!claimed && <button type="button" className="role-switch-pill" onClick={() => void claim()} disabled={action.busy}>Claim</button>}{claimed && <button type="button" className="role-switch-pill" onClick={() => void release()} disabled={action.busy}>Release</button>}</div></div>
    <div className="reviewer-original-problem">{item.problemText || "(Chưa có văn bản đề bài)"}</div>
    <details className="reviewer-extraction-details"><summary>Xem thực thể, đại lượng và quan hệ đã bóc tách</summary><pre>{JSON.stringify({ quantities: item.quantities, relations: item.relations }, null, 2)}</pre></details>
    <form onSubmit={submit}>
      <div className="form-group reviewer-question-group"><p className="reviewer-review-question">? {item.question}</p><textarea rows={3} required placeholder="Nhập câu trả lời kèm đại lượng, đơn vị hoặc hướng…" value={answer} onChange={(event) => setAnswer(event.target.value)} /></div>
      {Array.isArray(item.options) && item.options.length > 0 && <div className="reviewer-suggestions"><small className="reviewer-suggestion-label">Gợi ý từ pipeline:</small><div className="reviewer-suggestion-list">{item.options.map((option) => <button key={option} type="button" className="role-switch-pill" onClick={() => setAnswer(option)}>{option}</button>)}</div></div>}
      <div className="form-group reviewer-comment-group"><label htmlFor="review-comment">Căn cứ chuyên môn / ghi chú</label><input id="review-comment" placeholder="Ví dụ: dùng g = 9.8 m/s² theo giả định sách giáo khoa" value={comment} onChange={(event) => setComment(event.target.value)} /></div>
      {action.feedback}<button type="submit" className="prediction-submit-btn reviewer-resolution-submit" disabled={action.busy || !answer.trim()}>{action.busy ? "Đang cập nhật…" : "Gửi kết luận"}</button>
    </form>
  </article>;
}
