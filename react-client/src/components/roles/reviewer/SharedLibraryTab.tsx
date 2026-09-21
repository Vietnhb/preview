import { useCallback, useEffect, useState } from "react";
import axios from "../../../api/axios";
import type { LibraryItem } from "../../../types/physlive";

type ModeratedItem = LibraryItem & { moderationStatus?: string; moderationComment?: string | null };
type ModerationStatus = "APPROVED" | "FEATURED" | "REJECTED" | "REMOVED";

export function SharedLibraryTab() {
  const [items, setItems] = useState<ModeratedItem[]>([]);
  const [error, setError] = useState("");
  const load = useCallback(async () => {
    try { const response = await axios.get<ModeratedItem[]>("/reviewer/library"); setItems(response.data); setError(""); }
    catch { setError("Không thể tải hàng đợi Shared Library."); }
  }, []);
  useEffect(() => { const timer = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(timer); }, [load]);

  const decide = async (id: string, status: ModerationStatus) => {
    const comment = status === "REJECTED" || status === "REMOVED" ? window.prompt("Nhập lý do bắt buộc:", "") : undefined;
    if ((status === "REJECTED" || status === "REMOVED") && !comment?.trim()) return;
    try { await axios.put(`/reviewer/library/${id}`, { status, comment: comment?.trim() }); await load(); }
    catch { setError("Không thể cập nhật trạng thái; bản ghi có thể đã được reviewer khác thay đổi."); }
  };

  return <section className="reviewer-panel"><header className="reviewer-panel-header"><div><span className="reviewer-kicker">SHARED LIBRARY</span><h1>Duyệt nội dung giáo viên</h1><p>Kiểm tra simulation, specification và validation trước khi chia sẻ.</p></div><button type="button" onClick={() => void load()}>Làm mới</button></header>
    {error && <p className="status-pill fail" role="alert">{error}</p>}
    <div className="reviewer-table-wrap"><table className="reviewer-table"><thead><tr><th>Tiêu đề</th><th>Chủ đề</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{items.length === 0 ? <tr><td colSpan={4}>Không có nội dung chờ duyệt.</td></tr> : items.map((item) => <tr key={item.id}><td>{item.title}</td><td>{item.topic || "—"}</td><td>{item.moderationStatus || "PENDING"}</td><td className="reviewer-action-group"><button type="button" onClick={() => void decide(item.id, "APPROVED")}>Duyệt</button><button type="button" onClick={() => void decide(item.id, "FEATURED")}>Nổi bật</button><button type="button" onClick={() => void decide(item.id, "REJECTED")}>Từ chối</button><button type="button" onClick={() => void decide(item.id, "REMOVED")}>Gỡ</button></td></tr>)}</tbody></table></div>
  </section>;
}
