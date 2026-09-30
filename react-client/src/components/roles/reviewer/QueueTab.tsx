import { useState, type FormEvent } from "react";
import { Badge, Button, Card, Heading, SegmentedControl, Text, TextArea, TextField } from "@radix-ui/themes";
import { motion, useReducedMotion } from "motion/react";
import api from "../../../api/axios";
import { LoadState } from "../../operations/OperationsKit";
import { useAction, useResource } from "../../operations/operationsData";
import type { Specification } from "../../../types/physlive";
import type { ReviewItem } from "./reviewerTypes";
import { ReviewerHeader, ReviewerIcon, ReviewerMetric, ReviewerRefresh, ReviewerSearch } from "./ReviewerKit";

export function QueueTab() {
  const resource = useResource<ReviewItem[]>("/reviewer/ambiguities");
  const [selectedId, setSelectedId] = useState("");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("ALL");
  const reduceMotion = useReducedMotion();
  const allItems = resource.data ?? [];
  const items = allItems.filter((item) => (filter === "ALL" || (filter === "CLAIMED" ? Boolean(item.claimedBy) : !item.claimedBy)) && `${item.topic} ${item.question} ${item.problemText}`.toLowerCase().includes(query.toLowerCase()));
  const selected = items.find((item) => item.id === selectedId) ?? items[0];

  return (
    <div className="reviewer-stack">
      <div className="reviewer-metrics"><ReviewerMetric label="Cần xác minh" value={resource.loading ? "—" : allItems.length} icon="queue" tone="indigo" /><ReviewerMetric label="Chưa nhận xử lý" value={resource.loading ? "—" : allItems.filter((item) => !item.claimedBy).length} icon="clock" tone="amber" /><ReviewerMetric label="Đang xử lý" value={resource.loading ? "—" : allItems.filter((item) => item.claimedBy).length} icon="check" tone="cyan" /></div>
      <Card size="3" className="reviewer-panel">
      <ReviewerHeader title="Dữ kiện cần xác minh" icon="queue" actions={<ReviewerRefresh refresh={resource.refresh} loading={resource.loading} />} />
      <LoadState {...resource} />
      <div className="reviewer-toolbar"><ReviewerSearch aria-label="Tìm trong hàng đợi" placeholder="Tìm đề bài hoặc chủ đề" value={query} onChange={(event) => setQuery(event.target.value)} /><SegmentedControl.Root size="2" value={filter} onValueChange={setFilter} aria-label="Lọc dữ kiện"><SegmentedControl.Item value="ALL">Tất cả</SegmentedControl.Item><SegmentedControl.Item value="AVAILABLE">Chưa nhận</SegmentedControl.Item><SegmentedControl.Item value="CLAIMED">Đang xử lý</SegmentedControl.Item></SegmentedControl.Root></div>
      {!resource.loading && !resource.error && items.length === 0 && <div className="reviewer-empty"><span className="reviewer-empty-icon"><ReviewerIcon name="check" size={30} /></span><Heading as="h2" size="4">{query || filter !== "ALL" ? "Không tìm thấy dữ kiện" : "Đã xử lý hết dữ kiện"}</Heading><Text as="p" color="gray" size="2">{query || filter !== "ALL" ? "Thử từ khóa hoặc bộ lọc khác." : "Các dữ kiện cần xác minh sẽ xuất hiện tại đây."}</Text></div>}
      {!resource.loading && !resource.error && items.length > 0 && <div className="reviewer-case-layout">
        <aside className="reviewer-case-list" aria-label="Danh sách dữ kiện cần xác minh">
          <Text as="div" size="2" weight="medium" color="gray" mb="3">{items.length} dữ kiện</Text>
          {items.map((item) => <Button key={item.id} asChild variant={selected?.id === item.id ? "soft" : "surface"} color={selected?.id === item.id ? "indigo" : "gray"} size="3"><motion.button type="button" className="reviewer-case-button" aria-pressed={selected?.id === item.id} onClick={() => setSelectedId(item.id)} whileHover={reduceMotion ? undefined : { y: -2 }} transition={{ duration: .18 }}>
            <div className="reviewer-case-meta"><Badge color="cyan" size="1">{item.topic || "Vật lý"}</Badge><Badge color={item.claimedBy ? "indigo" : "amber"} size="1" variant="soft">{item.claimedBy ? "Đang xử lý" : "Chờ xử lý"}</Badge></div>
            <Text as="div" size="3" weight="medium">{item.question}</Text><Text as="p" size="2" color="gray" className="reviewer-case-preview">{item.problemText?.slice(0, 100)}{item.problemText?.length > 100 ? "…" : ""}</Text>
            {item.claimedBy && item.claimExpiresAt && <Text as="div" size="1" color="gray">Nhận xử lý đến {new Date(item.claimExpiresAt).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })}</Text>}
          </motion.button></Button>)}
        </aside>
        <div className="reviewer-case-detail">{selected && <ResolutionForm key={selected.id} item={selected} onResolved={resource.refresh} />}</div>
      </div>
      }
      </Card>
    </div>
  );
}

function ResolutionForm({ item, onResolved }: Readonly<{ item: ReviewItem; onResolved: () => void }>) {
  const [answer, setAnswer] = useState("");
  const [comment, setComment] = useState("");
  const [claimed, setClaimed] = useState(false);
  const action = useAction();
  const reduceMotion = useReducedMotion();

  const claim = async () => {
    await action.run(async () => {
      await api.post(`/reviewer/ambiguities/${item.id}/claim`);
      setClaimed(true);
    }, "Đã nhận xử lý trong 30 phút.");
  };

  const release = async () => {
    const ok = await action.run(() => api.post(`/reviewer/ambiguities/${item.id}/release`), "Đã trả lại hàng đợi.");
    if (ok) setClaimed(false);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const ok = await action.run(async () => {
      if (!claimed) await api.post(`/reviewer/ambiguities/${item.id}/claim`);
      const response = await api.post<Specification>(`/reviewer/ambiguities/${item.id}/resolve`, { answer: answer.trim(), comment: comment.trim() });
      const open = response.data.ambiguityCases ?? response.data.ambiguities ?? [];
      if (open.some((ambiguity) => ambiguity.code === item.code && ambiguity.status === "OPEN")) throw new Error("Dữ kiện chưa được xác minh; hãy bổ sung giá trị hoặc hướng chuẩn.");
    }, "Đã lưu kết luận chuyên môn.");
    if (ok) onResolved();
  };

  return <motion.article className="reviewer-resolution-form" initial={reduceMotion ? false : { opacity: 0, x: 6 }} animate={{ opacity: 1, x: 0 }} transition={{ duration: reduceMotion ? 0 : .18 }}>
    <div className="reviewer-detail-heading"><Heading as="h2" size="4">Đề bài gốc</Heading><div className="reviewer-actions">{!claimed && <Button type="button" variant="soft" onClick={() => void claim()} disabled={action.busy}><ReviewerIcon name="arrow" size={16} />Nhận xử lý</Button>}{claimed && <><Badge color="cyan"><ReviewerIcon name="check" size={13} />Đã nhận</Badge><Button type="button" color="gray" variant="soft" onClick={() => void release()} disabled={action.busy}>Trả lại hàng đợi</Button></>}</div></div>
    <Card variant="surface" size="2"><Text as="p" size="3" className="reviewer-problem-text">{item.problemText || "(Chưa có văn bản đề bài)"}</Text></Card>
    <details className="reviewer-extraction-details"><summary>Dữ liệu đã trích xuất</summary><pre>{JSON.stringify({ quantities: item.quantities, relations: item.relations }, null, 2)}</pre></details>
    <form onSubmit={submit}>
      <label className="reviewer-field" htmlFor="review-answer"><Text size="3" weight="medium">{item.question}</Text><TextArea size="3" id="review-answer" rows={4} required placeholder="Kết luận, đại lượng, đơn vị hoặc hướng" value={answer} onChange={(event) => setAnswer(event.target.value)} /></label>
      {Array.isArray(item.options) && item.options.length > 0 && <div className="reviewer-suggestions"><Text as="div" size="2" color="gray" mb="2">Giá trị gợi ý</Text><div className="reviewer-actions">{item.options.map((option) => <Button key={option} type="button" variant="soft" color="cyan" size="2" onClick={() => setAnswer(option)}>{option}</Button>)}</div></div>}
      <label className="reviewer-field" htmlFor="review-comment"><Text size="2" weight="medium">Căn cứ chuyên môn</Text><TextField.Root size="3" id="review-comment" placeholder="Giả định hoặc tài liệu tham chiếu" value={comment} onChange={(event) => setComment(event.target.value)} /></label>
      {action.feedback}<div className="reviewer-form-footer"><Button type="submit" size="3" disabled={action.busy || !answer.trim()}><ReviewerIcon name="check" size={18} />{action.busy ? "Đang cập nhật…" : "Lưu kết luận"}</Button></div>
    </form>
  </motion.article>;
}
