import { useState, type FormEvent } from "react";
import { Button, Card, Table, TextArea } from "@radix-ui/themes";
import api from "../../../api/axios";
import type { LibraryItem } from "../../../types/physlive";
import { LoadState } from "../../operations/OperationsKit";
import { useAction, useResource } from "../../operations/operationsData";
import { ReviewerDialog } from "./ReviewerDialog";
import { ReviewerFilter, ReviewerHeader, ReviewerRefresh, ReviewerSearch, ReviewStatus } from "./ReviewerKit";
import { ModerationPreview } from "./ModerationPreview";
import { usePhysliveStore } from "../../../store/usePhysliveStore";

type ModeratedItem = Omit<LibraryItem, "moderationStatus"> & { moderationStatus?: string; moderationComment?: string | null };
type ModerationStatus = "APPROVED" | "FEATURED" | "REJECTED" | "REMOVED";

export function SharedLibraryTab({ schoolId }: { schoolId?: string } = {}) {
  const endpoint = schoolId ? `/schools/${schoolId}/library` : "/reviewer/library";
  const [filter, setFilter] = useState("PENDING");
  const resource = useResource<ModeratedItem[]>(`${endpoint}?status=${filter || "PENDING"}`);
  const user = usePhysliveStore(state => state.user);
  const action = useAction();
  const [query, setQuery] = useState("");
  const [preview, setPreview] = useState<ModeratedItem | null>(null);
  const [decision, setDecision] = useState<{ item: ModeratedItem; status: "REJECTED" | "REMOVED" } | null>(null);
  const [reason, setReason] = useState("");
  const rows = (resource.data ?? []).filter((item) => (!filter || (item.moderationStatus || "PENDING") === filter) && `${item.title} ${item.topic ?? ""} ${item.sharedByName ?? ""}`.toLowerCase().includes(query.toLowerCase()));

  const decide = async (id: string, status: ModerationStatus, comment?: string) => {
    const ok = await action.run(() => api.put(`${endpoint}/${id}`, { status, comment: comment?.trim() }), "Đã cập nhật nội dung chia sẻ.");
    if (ok) { setDecision(null); resource.refresh(); }
  };

  const submitDecision = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (decision && reason.trim()) await decide(decision.item.id, decision.status, reason);
  };

  return <Card size="3" className="reviewer-panel">
<ReviewerHeader title={schoolId ? "Nội dung nội bộ cần duyệt" : "Nội dung công khai cần duyệt"} icon="library" count={resource.data?.length} actions={<ReviewerRefresh refresh={resource.refresh} loading={resource.loading} />} />
    <LoadState {...resource} />{action.feedback}
    <div className="reviewer-toolbar"><ReviewerSearch aria-label="Tìm nội dung chia sẻ" placeholder="Tìm tiêu đề, chủ đề hoặc người chia sẻ" value={query} onChange={(event) => setQuery(event.target.value)} /><ReviewerFilter label="Lọc trạng thái nội dung" value={filter} onChange={setFilter} statuses={schoolId ? ["PENDING", "APPROVED", "REJECTED", "REMOVED"] : ["PENDING", "APPROVED", "FEATURED", "REJECTED", "REMOVED"]} /></div>
    <div className="reviewer-table-scroll"><Table.Root variant="surface" size="2"><Table.Header><Table.Row><Table.ColumnHeaderCell>Nội dung</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Chủ đề</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Người chia sẻ</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Thao tác</Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>{rows.map((item) => <Table.Row key={item.id}><Table.Cell><strong>{item.title}</strong>{item.moderationComment && <small>{item.moderationComment}</small>}</Table.Cell><Table.Cell>{item.topic || "—"}</Table.Cell><Table.Cell>{item.sharedByName || "—"}{item.schoolName && <small>{item.schoolName}</small>}</Table.Cell><Table.Cell><ReviewStatus status={item.moderationStatus || "PENDING"} /></Table.Cell><Table.Cell><div className="reviewer-actions"><Button type="button" variant="soft" color="cyan" size="2" onClick={() => setPreview(item)}>Xem mô phỏng</Button><Button type="button" variant="soft" color="gray" size="2" disabled={action.busy || item.moderationStatus === "APPROVED" || Boolean(schoolId && item.sharedById === user?.id)} onClick={() => void decide(item.id, "APPROVED")}>Phê duyệt</Button>{!schoolId && <Button type="button" variant="soft" color="gray" size="2" disabled={action.busy || item.moderationStatus === "FEATURED"} onClick={() => void decide(item.id, "FEATURED")}>Nổi bật</Button>}<Button type="button" variant="soft" color="gray" size="2" disabled={action.busy} onClick={() => { setDecision({ item, status: "REJECTED" }); setReason(""); }}>Từ chối</Button><Button type="button" variant="soft" color="gray" size="2" disabled={action.busy} onClick={() => { setDecision({ item, status: "REMOVED" }); setReason(""); }}>Gỡ</Button></div></Table.Cell></Table.Row>)}{!resource.loading && !resource.error && rows.length === 0 && <Table.Row><Table.Cell colSpan={5} className="reviewer-table-empty">{query || filter ? "Không có nội dung phù hợp." : "Chưa có nội dung chia sẻ cần duyệt."}</Table.Cell></Table.Row>}</Table.Body></Table.Root></div>
    {decision && <ReviewerDialog title={decision.status === "REJECTED" ? "Từ chối nội dung" : "Gỡ nội dung"} onClose={() => setDecision(null)}><form onSubmit={submitDecision}><p className="reviewer-context-text">{decision.item.title}</p><label className="reviewer-field"><span>Lý do *</span><TextArea size="3" rows={3} required value={reason} onChange={(event) => setReason(event.target.value)} /></label>{action.feedback}<div className="reviewer-form-footer"><Button type="button" variant="soft" color="gray" size="2" onClick={() => setDecision(null)}>Hủy</Button><Button type="submit" size="3" disabled={action.busy || !reason.trim()}>{action.busy ? "Đang cập nhật…" : "Xác nhận"}</Button></div></form></ReviewerDialog>}
    {preview && <ModerationPreview item={preview} onClose={() => setPreview(null)} />}
  </Card>;
}
