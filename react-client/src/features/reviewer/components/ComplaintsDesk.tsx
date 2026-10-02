import { useCallback, useEffect, useMemo, useState } from "react";
import axios from "axios";
import { Button, Card, Heading, SegmentedControl, Text, TextArea } from "@radix-ui/themes";
import { complaintsForReview, resolveComplaint, type SimulationComplaint, type SupportStatus } from "../../support/api/supportApi";
import { COMPLAINT_STATUS } from "../../support/components/SimulationComplaintDialog";
import "../../support/styles/complaints.css";

type Filter = "todo" | "done";

/** Reviewer side: answer teachers' complaints about simulations and mark them resolved. */
export function ComplaintsDesk() {
  const [items, setItems] = useState<SimulationComplaint[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [filter, setFilter] = useState<Filter>("todo");
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [busyId, setBusyId] = useState("");
  const [actionError, setActionError] = useState<{ id: string; message: string } | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError("");
    try { setItems(await complaintsForReview()); }
    catch { setError("Chưa tải được danh sách khiếu nại. Vui lòng thử lại."); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const visible = useMemo(() => items.filter(item => (item.status === "RESOLVED") === (filter === "done")), [items, filter]);
  const todo = items.filter(item => item.status !== "RESOLVED").length;

  const act = async (item: SimulationComplaint, status: SupportStatus) => {
    const response = (drafts[item.id] ?? "").trim();
    if (status === "RESOLVED" && !response && !item.adminResponse) {
      setActionError({ id: item.id, message: "Hãy ghi kết quả xử lý trước khi đánh dấu đã giải quyết." });
      return;
    }
    setBusyId(item.id); setActionError(null);
    try {
      const updated = await resolveComplaint(item.id, status, response || undefined);
      setItems(current => current.map(value => value.id === updated.id ? updated : value));
      setDrafts(current => ({ ...current, [item.id]: "" }));
    } catch (cause) {
      const message = axios.isAxiosError<{ message?: string }>(cause) ? cause.response?.data?.message : undefined;
      setActionError({ id: item.id, message: message || "Chưa lưu được. Vui lòng thử lại." });
    } finally { setBusyId(""); }
  };

  return <div className="reviewer-stack">
    <div className="reviewer-toolbar">
      <div style={{ flex: 1, minWidth: 0 }}>
        <Heading as="h1" size="6">Khiếu nại mô phỏng</Heading>
        <Text as="p" size="2" color="gray" mt="1">Giáo viên báo mô phỏng tính sai hoặc không đúng đề bài. Hãy kiểm tra, trả lời và đánh dấu khi đã giải quyết.</Text>
      </div>
      <Button variant="soft" color="gray" onClick={() => void load()} disabled={loading}>Làm mới</Button>
    </div>
    <div className="reviewer-toolbar">
      <SegmentedControl.Root value={filter} onValueChange={value => setFilter(value as Filter)}>
        <SegmentedControl.Item value="todo">Cần xử lý{todo ? ` · ${todo}` : ""}</SegmentedControl.Item>
        <SegmentedControl.Item value="done">Đã giải quyết</SegmentedControl.Item>
      </SegmentedControl.Root>
    </div>
    {error && <Card><Text color="red" role="alert">{error}</Text></Card>}
    {loading && items.length === 0 && <Card><Text color="gray" role="status">Đang tải khiếu nại…</Text></Card>}
    {!loading && !error && visible.length === 0 && <Card size="3"><Text color="gray">{filter === "todo" ? "Không có khiếu nại nào đang chờ. " : "Chưa có khiếu nại nào được giải quyết."}</Text></Card>}
    <div className="complaint-desk">
      {visible.map(item => {
        const [detail, context] = item.content.split("\n— Đề bài —");
        return <Card size="3" key={item.id}>
          <div className="complaint-item__top"><Heading as="h2" size="4">{item.subject}</Heading><span className="complaint-status" data-status={item.status}>{COMPLAINT_STATUS[item.status]}</span></div>
          <p className="complaint-item__meta">{item.senderName} · {item.senderEmail} · {new Date(item.createdAt).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" })}</p>
          <p className="complaint-item__body">{detail.trim()}</p>
          {context && <p className="complaint-context" style={{ whiteSpace: "pre-wrap", marginTop: 10 }}><strong>Đề bài và thông số giáo viên đang dùng</strong>{"\n"}{context.replace("— Thông số đang dùng —", "Thông số:").trim()}</p>}
          {item.adminResponse && <p className="complaint-item__answer"><b>Đã phản hồi{item.responderName ? ` (${item.responderName})` : ""}</b>{item.adminResponse}</p>}
          <div className="complaint-desk__reply">
            <TextArea aria-label={`Phản hồi cho ${item.senderName}`} rows={3} maxLength={4000} value={drafts[item.id] ?? ""} disabled={busyId === item.id}
              onChange={event => setDrafts(current => ({ ...current, [item.id]: event.target.value }))}
              placeholder={item.adminResponse ? "Viết phản hồi mới (thay cho phản hồi trước)…" : "Kết quả kiểm tra và hướng xử lý cho giáo viên…"} />
            {actionError?.id === item.id && <Text size="2" color="red" role="alert">{actionError.message}</Text>}
            <div className="complaint-desk__actions">
              {item.status === "RESOLVED"
                ? <Button variant="soft" color="gray" disabled={busyId === item.id} onClick={() => void act(item, "READ")}>Mở lại để xử lý tiếp</Button>
                : <>
                  <Button variant="soft" disabled={busyId === item.id || !(drafts[item.id] ?? "").trim()} onClick={() => void act(item, "READ")}>Gửi phản hồi, tiếp tục xem xét</Button>
                  <Button disabled={busyId === item.id} onClick={() => void act(item, "RESOLVED")}>Đánh dấu đã giải quyết</Button>
                </>}
            </div>
          </div>
        </Card>;
      })}
    </div>
  </div>;
}
