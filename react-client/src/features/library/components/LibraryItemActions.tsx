import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import type { LibraryFolder, LibraryItem } from "../../../shared/types/physlive";
import axios from "axios";
import { deleteLibraryItem, moveLibraryItem, renameLibraryItem, shareLibraryItem } from "../api/libraryApi";
import { teacherLibraryStore } from "../hooks/useTeacherLibrary";
import { getToken } from "../../../shared/lib/token";
import Icon from "../../../shared/ui/LearningIcon";
import "../styles/library-actions.css";

const SHARE_OPTIONS: { value: LibraryItem["visibility"]; label: string; help: string }[] = [
  { value: "PERSONAL", label: "Chỉ mình tôi", help: "Không chia sẻ với ai." },
  { value: "SHARED", label: "Trong trường", help: "Tổ trưởng bộ môn Vật lý của trường duyệt trước khi hiển thị." },
  { value: "PUBLIC", label: "Kho cộng đồng", help: "Người kiểm duyệt của PhysLive duyệt trước khi hiển thị." },
];

/** Short review state of a shared item, shown in the share menu and on the library row. */
export function shareStatus(item: LibraryItem) {
  if (item.visibility === "PERSONAL") return "Đang chọn";
  if (item.moderationStatus === "PENDING") return "Chờ duyệt";
  if (item.moderationStatus === "REJECTED") return "Bị từ chối";
  return "Đã duyệt";
}

export default function LibraryItemActions({ item, folders }: Readonly<{ item: LibraryItem; folders: LibraryFolder[] }>) {
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDialogElement>(null);
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null);
  const [mode, setMode] = useState<"actions" | "info" | "move" | "share" | "rename" | "delete">("actions");
  const [folderQuery, setFolderQuery] = useState("");
  const [title, setTitle] = useState(item.title);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const close = () => { setPosition(null); trigger.current?.focus(); };
  useEffect(() => {
    if (!position) return;
    panel.current?.querySelector<HTMLElement>("input, button")?.focus();
    const outside = (event: PointerEvent) => {
      if (!panel.current?.contains(event.target as Node) && !trigger.current?.contains(event.target as Node)) setPosition(null);
    };
    const escape = (event: KeyboardEvent) => { if (event.key === "Escape") { setPosition(null); trigger.current?.focus(); } };
    const dismiss = (event: Event) => { if (!panel.current?.contains(event.target as Node)) setPosition(null); };
    document.addEventListener("pointerdown", outside);
    document.addEventListener("keydown", escape);
    globalThis.addEventListener("resize", dismiss);
    globalThis.addEventListener("scroll", dismiss, true);
    return () => {
      document.removeEventListener("pointerdown", outside);
      document.removeEventListener("keydown", escape);
      globalThis.removeEventListener("resize", dismiss);
      globalThis.removeEventListener("scroll", dismiss, true);
    };
  }, [position, mode]);
  const run = async (operation: () => Promise<LibraryItem | null>) => {
    if (busy) return;
    const session = getToken();
    setBusy(true); setError("");
    try {
      const updated = await operation();
      if (getToken() === session && teacherLibraryStore.getState().session === session) {
        teacherLibraryStore.setState(state => ({ items: updated
          ? state.items.map(value => value.id === item.id ? updated : value)
          : state.items.filter(value => value.id !== item.id) }));
      }
      close();
    } catch (cause) {
      const message = axios.isAxiosError<{ message?: string }>(cause) ? cause.response?.data?.message : undefined;
      setError(message || "Không lưu được thay đổi. Vui lòng thử lại.");
    }
    finally { setBusy(false); }
  };
  const normalizedFolderQuery = folderQuery.trim().toLocaleLowerCase("vi");
  const matchingFolders = folders.filter(folder => folder.name.toLocaleLowerCase("vi").includes(normalizedFolderQuery));
  return <>
    <button ref={trigger} type="button" className="library-more" aria-label={`Tùy chọn: ${item.title}`}
      aria-expanded={Boolean(position)} aria-controls={position ? `library-actions-${item.id}` : undefined} aria-haspopup="dialog"
      onClick={() => {
        if (position) { close(); return; }
        const rect = trigger.current!.getBoundingClientRect();
        setMode("actions"); setTitle(item.title); setError("");
        setPosition({ left: Math.max(8, Math.min(rect.right - 232, globalThis.innerWidth - 240)), top: Math.max(8, Math.min(rect.bottom + 6, globalThis.innerHeight - 320)) });
      }}>
      <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="1" /><circle cx="19" cy="12" r="1" /><circle cx="5" cy="12" r="1" /></svg>
    </button>
    {position && createPortal(<dialog ref={panel} open id={`library-actions-${item.id}`} className="library-action-popover" aria-label={`Tùy chọn: ${item.title}`} style={position}
      onBlur={event => { if (event.relatedTarget && !event.currentTarget.contains(event.relatedTarget as Node) && event.relatedTarget !== trigger.current) setPosition(null); }}>
      {mode === "actions" && <>
        <button type="button" disabled={busy} onClick={() => setMode("info")}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 11v5.5M12 7.8v.2" /></svg>Thông tin</button>
        <button type="button" disabled={busy} onClick={() => setMode("rename")}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="m16 3 5 5L8 21H3v-5ZM14 5l5 5" /></svg>Đổi tên</button>
        <button type="button" disabled={busy} onClick={() => { setFolderQuery(""); setMode("move"); }}><Icon name="folder" /><span>Chuyển vào thư mục…</span><svg className="library-menu-chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m9 6 6 6-6 6" /></svg></button>
        <button type="button" disabled={busy} onClick={() => setMode("share")}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="6" cy="12" r="2.5" /><circle cx="18" cy="6" r="2.5" /><circle cx="18" cy="18" r="2.5" /><path d="m8.2 10.9 7.6-3.8M8.2 13.1l7.6 3.8" /></svg><span>Chia sẻ…</span><svg className="library-menu-chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m9 6 6 6-6 6" /></svg></button>
        <div className="library-menu-divider" />
        <button type="button" className="library-menu-danger" disabled={busy} onClick={() => setMode("delete")}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7M14 10v7" /></svg>Xóa khỏi thư viện</button>
      </>}
      {mode === "move" && <>
        <button type="button" className="library-menu-back" disabled={busy} onClick={() => setMode("actions")}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m15 6-6 6 6 6" /></svg>Chuyển vào thư mục</button>
        {folders.length > 6 && <input className="library-menu-search" type="search" value={folderQuery} onChange={event => setFolderQuery(event.target.value)} placeholder="Tìm thư mục" aria-label="Tìm thư mục" />}
        <div className="library-menu-folders">
          {matchingFolders.map(folder => {
            const current = folder.id === item.folderId;
            return <button type="button" key={folder.id} disabled={busy || current} aria-current={current ? "true" : undefined} onClick={() => void run(() => moveLibraryItem(item.id, folder.id))}>
              <Icon name="folder" /><span>{folder.name}</span>{current && <small>Đang ở đây</small>}
            </button>;
          })}
          {matchingFolders.length === 0 && <p className="library-menu-label">{folders.length === 0 ? "Chưa có thư mục nào" : "Không tìm thấy thư mục"}</p>}
        </div>
      </>}
      {mode === "info" && <>
        <button type="button" className="library-menu-back" onClick={() => setMode("actions")}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m15 6-6 6 6 6" /></svg>Thông tin mô phỏng</button>
        <dl className="library-info">
          <div><dt>Tên</dt><dd>{item.title}</dd></div>
          <div><dt>Chủ đề</dt><dd>{item.topic || "Chưa xác định"}</dd></div>
          <div><dt>Thư mục</dt><dd>{folders.find(folder => folder.id === item.folderId)?.name ?? "Chưa phân loại"}</dd></div>
          <div><dt>Ngày lưu</dt><dd>{new Date(item.createdAt).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" })}</dd></div>
          <div><dt>Kiểm tra kết quả</dt><dd>{item.validationStatus === "PASSED" ? "Đã đạt, giao bài được" : "Chưa đạt, chưa giao bài được"}</dd></div>
          <div><dt>Chia sẻ</dt><dd>{item.visibility === "PERSONAL" ? "Chỉ mình tôi" : `${item.visibility === "PUBLIC" ? "Kho cộng đồng" : "Trong trường"} · ${shareStatus(item).toLocaleLowerCase("vi")}`}</dd></div>
          {item.visibility !== "PERSONAL" && item.moderationComment && <div><dt>Ghi chú của người duyệt</dt><dd>{item.moderationComment}</dd></div>}
        </dl>
      </>}
      {mode === "share" && <>
        <button type="button" className="library-menu-back" disabled={busy} onClick={() => setMode("actions")}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m15 6-6 6 6 6" /></svg>Chia sẻ mô phỏng</button>
        {SHARE_OPTIONS.map(option => {
          const current = item.visibility === option.value;
          // A rejected request can be sent again from the same option.
          const resend = current && item.moderationStatus === "REJECTED";
          return <button type="button" key={option.value} className="library-share-option" disabled={busy || (current && !resend)} aria-current={current ? "true" : undefined}
            onClick={() => void run(() => shareLibraryItem(item.id, option.value))}>
            <span><strong>{option.label}</strong><small>{option.help}</small></span>
            {current && <em>{resend ? "Gửi lại" : shareStatus(item)}</em>}
          </button>;
        })}
        {item.visibility !== "PERSONAL" && item.moderationStatus === "REJECTED" && item.moderationComment && <p className="library-menu-error">Lý do từ chối: {item.moderationComment}</p>}
      </>}
      {mode === "rename" && <form onSubmit={event => { event.preventDefault(); if (title.trim()) void run(() => renameLibraryItem(item.id, title.trim())); }}>
        <label htmlFor={`rename-${item.id}`}>Đổi tên mô phỏng</label>
        <input id={`rename-${item.id}`} value={title} maxLength={160} disabled={busy} onChange={event => setTitle(event.target.value)} />
        <div className="library-menu-footer"><button type="button" disabled={busy} onClick={() => setMode("actions")}>Hủy</button><button type="submit" disabled={busy || !title.trim()}>Lưu</button></div>
      </form>}
      {mode === "delete" && <>
        <p className="library-delete-copy">Xóa “{item.title}” khỏi thư viện? Mô phỏng vẫn còn trong lịch sử.</p>
        <div className="library-menu-footer"><button type="button" disabled={busy} onClick={() => setMode("actions")}>Hủy</button><button type="button" className="library-menu-danger" disabled={busy} onClick={() => void run(async () => { await deleteLibraryItem(item.id); return null; })}>Xóa</button></div>
      </>}
      {busy && <output className="library-menu-label">Đang lưu…</output>}
      {error && <p className="library-menu-error" role="alert">{error}</p>}
    </dialog>, document.body)}
  </>;
}