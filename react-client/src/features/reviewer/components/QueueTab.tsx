import { useState, type FormEvent } from "react";
import { Badge, Button, Card, Heading, SegmentedControl, Text, TextArea, TextField } from "@radix-ui/themes";
import api from "../../../shared/api/client";
import { LoadState } from "../../../shared/ui/OperationsKit";
import { useAction, useResource } from "../../../shared/hooks/operationsData";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import type { Specification } from "../../../shared/types/physlive";
import type { ReviewItem } from "../model/reviewerTypes";
import { matches } from "../model/reviewerUtils";
import { useReviewerCatalog, type ReviewerCatalog } from "../model/useReviewerCatalog";
import { ReviewerIcon, ReviewerRefresh, ReviewerSearch } from "./ReviewerKit";

type Row = Record<string, unknown>;
const asRows = (value: unknown): Row[] => Array.isArray(value) ? value.filter((row): row is Row => Boolean(row) && typeof row === "object") : [];
const text = (row: Row, ...keys: string[]) => { for (const key of keys) { const value = row[key]; if (value !== undefined && value !== null && value !== "") return String(value); } return ""; };

/** Shows what the AI extracted as a readable table instead of raw JSON. */
export function ExtractedQuantities({ value, catalog }: Readonly<{ value: unknown; catalog?: ReviewerCatalog }>) {
  const rows = asRows(value);
  if (!rows.length) return <Text as="p" size="2" color="gray">AI chưa trích xuất được đại lượng nào.</Text>;
  return <table className="reviewer-mini-table"><thead><tr><th>Đại lượng</th><th>Kí hiệu</th><th>Giá trị</th><th>Đơn vị</th></tr></thead><tbody>
    {rows.map((row, index) => <tr key={index}><td>{catalog?.quantityLabel(text(row, "name", "key")) || text(row, "label") || "—"}{text(row, "sourceText") && <small className="reviewer-source">“{text(row, "sourceText")}”</small>}</td><td>{text(row, "symbol") || "—"}</td><td>{text(row, "originalValue", "value", "normalizedValue", "valueSI") || "—"}</td><td>{text(row, "originalUnit", "unit", "normalizedUnit", "unitSI") || "—"}</td></tr>)}
  </tbody></table>;
}

export function QueueTab() {
  const resource = useResource<ReviewItem[]>("/reviewer/ambiguities");
  const userId = useSessionStore(state => state.user?.id);
  const catalog = useReviewerCatalog();
  const [selectedId, setSelectedId] = useState("");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("AVAILABLE");
  const all = resource.data ?? [];
  const mine = (item: ReviewItem) => item.claimedBy != null && item.claimedBy === userId;
  const items = all.filter(item => (filter === "ALL" || (filter === "MINE" ? mine(item) : !item.claimedBy || mine(item))) && matches(query, item.topic, catalog.topicName(item.topic), item.question, catalog.humanize(item.question), item.problemText));
  const selected = items.find(item => item.id === selectedId) ?? items[0];
  const after = (id: string) => { const index = items.findIndex(item => item.id === id); setSelectedId(items[index + 1]?.id ?? items[index - 1]?.id ?? ""); resource.refresh(); };

  return <div className="reviewer-stack">
    <header className="reviewer-page-head">
      <div><Heading as="h1" size="6">Câu hỏi từ AI</Heading><Text as="p" size="2" color="gray" mt="1">Khi AI đọc đề bài mà chưa chắc một dữ kiện (giá trị, đơn vị, hướng…), nó hỏi chuyên gia. Câu trả lời của bạn giúp AI dựng mô phỏng đúng.</Text></div>
      <ReviewerRefresh refresh={resource.refresh} loading={resource.loading} />
    </header>
    <div className="reviewer-toolbar">
      <SegmentedControl.Root size="2" value={filter} onValueChange={setFilter} aria-label="Lọc câu hỏi">
        <SegmentedControl.Item value="AVAILABLE">Có thể nhận ({all.filter(item => !item.claimedBy || mine(item)).length})</SegmentedControl.Item>
        <SegmentedControl.Item value="MINE">Tôi đang xử lý ({all.filter(mine).length})</SegmentedControl.Item>
        <SegmentedControl.Item value="ALL">Tất cả ({all.length})</SegmentedControl.Item>
      </SegmentedControl.Root>
      <ReviewerSearch aria-label="Tìm câu hỏi" placeholder="Tìm theo đề bài hoặc chủ đề" value={query} onChange={event => setQuery(event.target.value)} />
    </div>
    <LoadState {...resource} />
    {!resource.loading && !resource.error && items.length === 0 && <Card size="3"><div className="reviewer-empty"><span className="reviewer-empty-icon"><ReviewerIcon name="check" size={30} /></span><Heading as="h2" size="4">{query ? "Không tìm thấy câu hỏi" : "Không còn câu hỏi nào"}</Heading><Text as="p" color="gray" size="2">{query ? "Thử từ khóa khác." : "Câu hỏi mới của AI sẽ xuất hiện tại đây."}</Text></div></Card>}
    {!resource.loading && items.length > 0 && <div className="reviewer-split">
      <aside className="reviewer-list" aria-label="Danh sách câu hỏi">
        <Text as="div" size="1" color="gray" className="reviewer-list-count">{items.length} câu hỏi</Text>
        {items.map(item => <button key={item.id} type="button" className="reviewer-list-item" aria-current={selected?.id === item.id ? "true" : undefined} onClick={() => setSelectedId(item.id)}>
          <span className="reviewer-list-tags"><Badge size="1" color="gray" variant="soft">{catalog.topicName(item.topic) || "Vật lý"}</Badge>{mine(item) ? <Badge size="1" color="indigo">Bạn đang xử lý</Badge> : item.claimedBy ? <Badge size="1" color="amber" variant="soft">Người khác đang xử lý</Badge> : null}</span>
          <strong>{catalog.humanize(item.question)}</strong>
          <span className="reviewer-list-meta reviewer-clamp">{item.problemText}</span>
        </button>)}
      </aside>
      {selected && <ResolutionForm key={selected.id} item={selected} catalog={catalog} mine={mine(selected)} onResolved={() => after(selected.id)} onChanged={resource.refresh} />}
    </div>}
  </div>;
}

function ResolutionForm({ item, catalog, mine, onResolved, onChanged }: Readonly<{ item: ReviewItem; catalog: ReviewerCatalog; mine: boolean; onResolved: () => void; onChanged: () => void }>) {
  const options = Array.isArray(item.options) ? item.options : [];
  const [choice, setChoice] = useState<string>("");
  const [custom, setCustom] = useState("");
  const [comment, setComment] = useState("");
  const [holding, setHolding] = useState(mine);
  const action = useAction();
  const lockedByOther = Boolean(item.claimedBy) && !mine;
  const answer = (choice === "__custom" || !options.length ? custom : choice).trim();
  const offSuggestion = choice === "__custom";

  // Claim silently the first time the reviewer starts answering, so two experts don't answer the same question.
  const ensureClaim = async () => {
    if (holding || lockedByOther) return;
    setHolding(true);
    try { await api.post(`/reviewer/ambiguities/${item.id}/claim`); onChanged(); }
    catch { setHolding(false); }
  };
  const release = async () => {
    const ok = await action.run(() => api.post(`/reviewer/ambiguities/${item.id}/release`), "Đã trả câu hỏi về danh sách chung.");
    if (ok) { setHolding(false); onChanged(); }
  };
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const ok = await action.run(async () => {
      if (!holding) await api.post(`/reviewer/ambiguities/${item.id}/claim`);
      const response = await api.post<Specification>(`/reviewer/ambiguities/${item.id}/resolve`, { answer, comment: comment.trim() });
      const open = response.data.ambiguityCases ?? response.data.ambiguities ?? [];
      if (open.some(ambiguity => ambiguity.code === item.code && ambiguity.status === "OPEN")) throw new Error("Câu trả lời chưa đủ để AI xác định dữ kiện. Hãy ghi rõ giá trị kèm đơn vị hoặc hướng.");
    }, "Đã lưu câu trả lời. AI sẽ dùng thông tin này khi dựng mô phỏng.");
    if (ok) onResolved();
  };

  return <Card size="3" className="reviewer-detail">
    <div className="reviewer-detail-head">
      <Heading as="h2" size="3" color="gray" weight="medium">Đề bài</Heading>
      {holding && !lockedByOther && <div className="reviewer-claim"><Badge color="indigo"><ReviewerIcon name="lock" size={12} />Bạn đang giữ câu hỏi này{item.claimExpiresAt ? ` đến ${new Date(item.claimExpiresAt).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })}` : ""}</Badge><Button size="1" variant="ghost" color="gray" disabled={action.busy} onClick={() => void release()}>Trả lại</Button></div>}
    </div>
    <blockquote className="reviewer-problem">{item.problemText || "(Không có nội dung đề bài)"}</blockquote>

    <details className="reviewer-disclosure"><summary>AI đã hiểu được gì từ đề bài?</summary><ExtractedQuantities value={item.quantities} catalog={catalog} />
      <details className="reviewer-disclosure reviewer-disclosure-nested"><summary>Dữ liệu kỹ thuật (dành cho kỹ thuật viên)</summary><pre className="reviewer-json">{JSON.stringify({ field: item.fieldPath, code: item.code, quantities: item.quantities, relations: item.relations }, null, 2)}</pre></details>
    </details>

    {lockedByOther ? <div className="reviewer-note"><ReviewerIcon name="lock" size={16} /> Một chuyên gia khác đang trả lời câu hỏi này{item.claimExpiresAt ? ` (đến ${new Date(item.claimExpiresAt).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })})` : ""}. Hãy chọn câu khác.</div> :
    <form onSubmit={submit} className="reviewer-answer">
      <div className="reviewer-question"><span className="reviewer-question-mark">AI hỏi</span><Text as="p" size="4" weight="medium">{catalog.humanize(item.question)}</Text></div>
      {options.length > 0 && <div className="reviewer-options" role="radiogroup" aria-label="Chọn câu trả lời">
        {options.map(option => <label key={option} className="reviewer-option"><input type="radio" name={`answer-${item.id}`} value={option} checked={choice === option} onChange={() => { setChoice(option); void ensureClaim(); }} /><span>{option}</span></label>)}
        <label className="reviewer-option"><input type="radio" name={`answer-${item.id}`} value="__custom" checked={choice === "__custom"} onChange={() => { setChoice("__custom"); void ensureClaim(); }} /><span>Câu trả lời khác…</span></label>
      </div>}
      {(choice === "__custom" || !options.length) && <label className="reviewer-field"><span>Câu trả lời của bạn *</span><TextArea size="3" rows={3} autoFocus={options.length > 0} placeholder="Ví dụ: 20 m/s, hướng lên trên" value={custom} onFocus={() => void ensureClaim()} onChange={event => setCustom(event.target.value)} /></label>}
      <label className="reviewer-field"><span>Căn cứ {offSuggestion ? "*" : "(không bắt buộc)"}</span><TextField.Root size="3" required={offSuggestion} placeholder="Vì sao bạn chọn như vậy? (giả định, tài liệu tham khảo…)" value={comment} onChange={event => setComment(event.target.value)} /></label>
      {action.feedback}
      <div className="reviewer-form-footer"><Button type="submit" size="3" disabled={action.busy || !answer || (offSuggestion && !comment.trim())}><ReviewerIcon name="check" size={16} />{action.busy ? "Đang lưu…" : "Gửi câu trả lời"}</Button></div>
    </form>}
  </Card>;
}
