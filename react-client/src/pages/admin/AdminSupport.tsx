import { useCallback, useEffect, useState } from "react";
import { adminSupportItems, updateSupportItem, type SupportItem, type SupportKind, type SupportStatus } from "../../api/supportApi";

export default function AdminSupport({ kind }: Readonly<{ kind: SupportKind }>) {
  const [items, setItems] = useState<SupportItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<SupportItem | null>(null);
  const [response, setResponse] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setItems(await adminSupportItems(kind));
    } catch {
      setError("Không thể tải dữ liệu hỗ trợ.");
    } finally {
      setLoading(false);
    }
  }, [kind]);

  useEffect(() => {
    const timer = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const save = async (status: SupportStatus) => {
    if (!selected) return;
    try {
      const updated = await updateSupportItem(selected.id, status, response);
      setItems((current) => current.map((item) => item.id === updated.id ? updated : item));
      setSelected(updated);
      setResponse(updated.adminResponse ?? "");
    } catch {
      setError("Không thể cập nhật yêu cầu.");
    }
  };

  return <div className="admin-content">
    <section className="admin-panel">
      <div className="admin-panel-heading"><div><h2>{kind === "FEEDBACK" ? "Feedback" : "Messages"}</h2><p className="admin-panel-description">Theo dõi, phản hồi và đóng yêu cầu từ người dùng.</p></div><button className="admin-secondary-button" type="button" onClick={() => void load()}>Làm mới</button></div>
      {error && <p className="admin-error-banner" role="alert">{error}</p>}
      {loading ? <p role="status">Đang tải…</p> : <div className="admin-table-scroll"><table className="admin-table">
        <thead><tr><th>Người gửi</th><th>Tiêu đề</th><th>Trạng thái</th><th>Ngày tạo</th><th /></tr></thead>
        <tbody>{items.length === 0 ? <tr><td colSpan={5}>Chưa có yêu cầu.</td></tr> : items.map((item) => <tr key={item.id}>
          <td><strong>{item.senderName}</strong><small>{item.senderEmail}</small></td><td>{item.subject}<p className="admin-cell-muted">{item.content}</p></td>
          <td><span className={`status-pill ${item.status === "RESOLVED" ? "pass" : item.status === "READ" ? "info" : "warning"}`}>{item.status}</span></td><td>{new Date(item.createdAt).toLocaleString("vi-VN")}</td>
          <td><button className="admin-inline-button" type="button" onClick={() => { setSelected(item); setResponse(item.adminResponse ?? ""); }}>Xem</button></td>
        </tr>)}</tbody>
      </table></div>}
    </section>
    {selected && <div className="admin-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setSelected(null); }}>
      <section className="admin-modal" role="dialog" aria-modal="true" aria-labelledby="support-title">
        <div className="admin-modal-header"><div><h2 id="support-title">{selected.subject}</h2><p>{selected.senderName} · {selected.senderEmail}</p></div><button type="button" className="admin-modal-close" onClick={() => setSelected(null)}>×</button></div>
        <p className="admin-support-content">{selected.content}</p>
        <label>Phản hồi<textarea rows={4} value={response} onChange={(event) => setResponse(event.target.value)} placeholder="Nhập phản hồi cho người dùng…" /></label>
        <div className="admin-form-actions"><button type="button" className="admin-secondary-button" onClick={() => void save("READ")}>Đánh dấu đã đọc</button><button type="button" className="admin-primary-button" onClick={() => void save("RESOLVED")}>Lưu & đóng</button></div>
      </section>
    </div>}
  </div>;
}
