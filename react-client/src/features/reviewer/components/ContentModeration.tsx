import { useCallback, useState, type FormEvent } from "react";
import { Badge, Button, Card, Heading, SegmentedControl, Text, TextArea } from "@radix-ui/themes";
import api from "../../../shared/api/client";
import type { LibraryItem } from "../../../shared/types/physlive";
import { LoadState } from "../../../shared/ui/OperationsKit";
import { useAction, useResource } from "../../../shared/hooks/operationsData";
import { matches, timeAgo } from "../model/reviewerUtils";
import { useReviewerCatalog } from "../hooks/useReviewerCatalog";
import { ReviewerIcon, ReviewerRefresh, ReviewerSearch, ReviewStatus } from "./ReviewerKit";
import { SimulationPreviewPane } from "./SimulationPreviewPane";

type ModeratedItem = Omit<LibraryItem, "moderationStatus"> & { moderationStatus?: string; moderationComment?: string | null; description?: string | null };
type Decision = "APPROVED" | "FEATURED" | "REJECTED" | "REMOVED";
type Filter = "PENDING" | "APPROVED" | "FEATURED" | "REJECTED" | "REMOVED";

const FILTERS: { value: Filter; label: string }[] = [
  { value: "PENDING", label: "Chờ duyệt" },
  { value: "APPROVED", label: "Đã duyệt" },
  { value: "FEATURED", label: "Nổi bật" },
  { value: "REJECTED", label: "Bị từ chối" },
  { value: "REMOVED", label: "Đã gỡ" },
];

const REJECT_REASONS = ["Sai kiến thức vật lý", "Mô phỏng không chạy hoặc hiển thị lỗi", "Không phù hợp chương trình THPT", "Tiêu đề / mô tả chưa rõ ràng", "Trùng với nội dung đã có"];
const REMOVE_REASONS = ["Phát hiện sai sót sau khi duyệt", "Giáo viên yêu cầu gỡ", "Nội dung đã lỗi thời"];

const CHECKLIST = ["Kiến thức vật lý chính xác, đơn vị và số liệu hợp lý", "Mô phỏng chạy được, hiển thị rõ ràng", "Phù hợp học sinh THPT, không có nội dung không phù hợp"];

export function ContentModeration() {
  const [filter, setFilter] = useState<Filter>("PENDING");
  const resource = useResource<ModeratedItem[]>(`/reviewer/library?status=${filter}`);
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState("");
  const catalog = useReviewerCatalog();
  const rows = (resource.data ?? []).filter(item => (item.moderationStatus || "PENDING") === filter && matches(query, item.title, item.topic, catalog.topicName(item.topic), item.sharedByName, item.schoolName));
  const selected = rows.find(item => item.id === selectedId) ?? rows[0];

  const afterDecision = (id: string) => {
    // Move on to the next item in the same list so the reviewer keeps a steady rhythm.
    const index = rows.findIndex(item => item.id === id);
    const next = rows[index + 1] ?? rows[index - 1];
    setSelectedId(next?.id ?? "");
    resource.refresh();
  };

  return <div className="reviewer-stack">
    <header className="reviewer-page-head">
      <div><Heading as="h1" size="6">Mô phỏng chờ duyệt</Heading><Text as="p" size="2" color="gray" mt="1">Giáo viên chia sẻ mô phỏng lên thư viện công khai. Hãy chạy thử mô phỏng trước khi quyết định.</Text></div>
      <ReviewerRefresh refresh={resource.refresh} loading={resource.loading} />
    </header>
    <div className="reviewer-toolbar">
      <SegmentedControl.Root size="2" value={filter} onValueChange={value => { setFilter(value as Filter); setSelectedId(""); }} aria-label="Lọc theo trạng thái">
        {FILTERS.map(item => <SegmentedControl.Item key={item.value} value={item.value}>{item.label}</SegmentedControl.Item>)}
      </SegmentedControl.Root>
      <ReviewerSearch aria-label="Tìm mô phỏng" placeholder="Tìm tiêu đề, chủ đề, giáo viên, trường" value={query} onChange={event => setQuery(event.target.value)} />
    </div>
    <LoadState {...resource} />
    {!resource.loading && !resource.error && rows.length === 0 && <Card size="3"><div className="reviewer-empty"><span className="reviewer-empty-icon"><ReviewerIcon name="check" size={30} /></span><Heading as="h2" size="4">{query ? "Không tìm thấy mô phỏng phù hợp" : filter === "PENDING" ? "Đã duyệt hết!" : "Chưa có mô phỏng nào"}</Heading><Text as="p" color="gray" size="2">{query ? "Thử từ khóa khác." : filter === "PENDING" ? "Mô phỏng mới do giáo viên gửi sẽ xuất hiện tại đây." : "Danh sách này đang trống."}</Text></div></Card>}
    {!resource.loading && rows.length > 0 && <div className="reviewer-split">
      <aside className="reviewer-list" aria-label="Danh sách mô phỏng">
        <Text as="div" size="1" color="gray" className="reviewer-list-count">{rows.length} mô phỏng</Text>
        {rows.map(item => <button key={item.id} type="button" className="reviewer-list-item" aria-current={selected?.id === item.id ? "true" : undefined} onClick={() => setSelectedId(item.id)}>
          <strong>{item.title}</strong>
          <span className="reviewer-list-meta">{catalog.topicName(item.topic) || "Chưa gắn chủ đề"}</span>
          <span className="reviewer-list-meta">{item.sharedByName || "Không rõ người gửi"}{item.schoolName ? ` · ${item.schoolName}` : ""}</span>
          {item.createdAt && <small>{timeAgo(item.createdAt)}</small>}
        </button>)}
      </aside>
      {selected && <ModerationDetail key={selected.id} item={selected} topic={catalog.topicName(selected.topic)} onDone={() => afterDecision(selected.id)} />}
    </div>}
  </div>;
}

function ModerationDetail({ item, topic, onDone }: Readonly<{ item: ModeratedItem; topic: string; onDone: () => void }>) {
  const action = useAction();
  const status = (item.moderationStatus || "PENDING") as Filter;
  const [previewOk, setPreviewOk] = useState<boolean | null>(null);
  const [checked, setChecked] = useState<boolean[]>(CHECKLIST.map(() => false));
  const [asking, setAsking] = useState<"REJECTED" | "REMOVED" | null>(null);
  const [reason, setReason] = useState("");
  const onLoaded = useCallback((ok: boolean) => setPreviewOk(ok), []);
  const allChecked = checked.every(Boolean);

  const decide = async (decision: Decision, comment?: string) => {
    const messages: Record<Decision, string> = { APPROVED: "Đã phê duyệt mô phỏng.", FEATURED: "Đã phê duyệt và đánh dấu nổi bật.", REJECTED: "Đã từ chối và gửi lý do cho giáo viên.", REMOVED: "Đã gỡ mô phỏng khỏi thư viện." };
    const ok = await action.run(() => api.put(`/reviewer/library/${item.id}`, { status: decision, comment: comment?.trim() || undefined }), messages[decision]);
    if (ok) onDone();
  };
  const submitReason = async (event: FormEvent) => {
    event.preventDefault();
    if (asking && reason.trim()) await decide(asking, reason);
  };
  const approveBlocked = previewOk !== true || !allChecked;

  return <Card size="3" className="reviewer-detail">
    <div className="reviewer-detail-head">
      <div><Heading as="h2" size="5">{item.title}</Heading>
        <div className="reviewer-detail-meta">
          <ReviewStatus status={status} />
          {topic && <Badge color="gray" variant="soft">{topic}</Badge>}
          <Text size="2" color="gray"><ReviewerIcon name="user" size={14} /> {item.sharedByName || "Không rõ"}{item.schoolName ? ` · ${item.schoolName}` : ""}{item.createdAt ? ` · gửi ${timeAgo(item.createdAt)}` : ""}</Text>
        </div>
      </div>
    </div>
    {item.description && <Text as="p" size="2" className="reviewer-context-text">{item.description}</Text>}
    {item.moderationComment && status !== "PENDING" && <div className="reviewer-note"><strong>Ghi chú kiểm duyệt:</strong> {item.moderationComment}</div>}
    <SimulationPreviewPane simulationId={item.simulationId} onLoaded={onLoaded} />

    {status === "PENDING" && <fieldset className="reviewer-checklist">
      <legend>Trước khi phê duyệt, xác nhận rằng:</legend>
      {CHECKLIST.map((label, index) => <label key={label}><input type="checkbox" checked={checked[index]} onChange={event => setChecked(values => values.map((value, i) => i === index ? event.target.checked : value))} />{label}</label>)}
    </fieldset>}

    {action.feedback}
    {asking ? <form className="reviewer-reason" onSubmit={submitReason}>
      <Text as="div" size="3" weight="bold">{asking === "REJECTED" ? "Lý do từ chối" : "Lý do gỡ khỏi thư viện"}</Text>
      <Text as="p" size="2" color="gray">Giáo viên sẽ nhận được lý do này. Chọn nhanh hoặc tự viết.</Text>
      <div className="reviewer-chips">{(asking === "REJECTED" ? REJECT_REASONS : REMOVE_REASONS).map(value => <button key={value} type="button" className="reviewer-chip" aria-pressed={reason === value} onClick={() => setReason(value)}>{value}</button>)}</div>
      <TextArea size="3" rows={3} required autoFocus placeholder="Ví dụ: Gia tốc trọng trường đang đặt 98 m/s², cần sửa thành 9,8 m/s²." value={reason} onChange={event => setReason(event.target.value)} />
      <div className="reviewer-form-footer"><Button type="button" variant="soft" color="gray" onClick={() => { setAsking(null); setReason(""); }}>Quay lại</Button><Button type="submit" color="red" disabled={action.busy || !reason.trim()}>{action.busy ? "Đang lưu…" : asking === "REJECTED" ? "Từ chối mô phỏng" : "Gỡ mô phỏng"}</Button></div>
    </form> : <div className="reviewer-decision-bar">
      {status === "PENDING" && <>
        <Button size="3" disabled={action.busy || approveBlocked} onClick={() => void decide("APPROVED")}><ReviewerIcon name="check" size={16} />Phê duyệt</Button>
        <Button size="3" variant="soft" disabled={action.busy || approveBlocked} onClick={() => void decide("FEATURED")}><ReviewerIcon name="star" size={16} />Duyệt & nổi bật</Button>
        <span className="reviewer-spacer" />
        <Button size="3" variant="soft" color="red" disabled={action.busy} onClick={() => setAsking("REJECTED")}><ReviewerIcon name="close" size={16} />Từ chối</Button>
      </>}
      {status === "APPROVED" && <>
        <Button size="3" variant="soft" disabled={action.busy} onClick={() => void decide("FEATURED")}><ReviewerIcon name="star" size={16} />Đánh dấu nổi bật</Button>
        <span className="reviewer-spacer" />
        <Button size="3" variant="soft" color="red" disabled={action.busy} onClick={() => setAsking("REMOVED")}><ReviewerIcon name="trash" size={16} />Gỡ khỏi thư viện</Button>
      </>}
      {status === "FEATURED" && <>
        <Button size="3" variant="soft" color="gray" disabled={action.busy} onClick={() => void decide("APPROVED")}>Bỏ nổi bật</Button>
        <span className="reviewer-spacer" />
        <Button size="3" variant="soft" color="red" disabled={action.busy} onClick={() => setAsking("REMOVED")}><ReviewerIcon name="trash" size={16} />Gỡ khỏi thư viện</Button>
      </>}
      {(status === "REJECTED" || status === "REMOVED") && <Button size="3" variant="soft" disabled={action.busy || previewOk !== true} onClick={() => void decide("APPROVED")}><ReviewerIcon name="check" size={16} />Duyệt lại</Button>}
      {status === "PENDING" && approveBlocked && !action.busy && <Text as="p" size="1" color="gray" className="reviewer-decision-hint">{previewOk === null ? "Đang mở mô phỏng…" : previewOk === false ? "Mô phỏng lỗi nên không thể phê duyệt." : "Đánh dấu đủ 3 tiêu chí để phê duyệt."}</Text>}
    </div>}
  </Card>;
}
