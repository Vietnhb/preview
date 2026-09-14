import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import type { LibraryFolder, LibraryItem } from "../types/physlive";
import { deleteLibraryItem, moveLibraryItem, renameLibraryItem } from "../api/physliveApi";
import { teacherLibraryStore } from "../store/useTeacherLibrary";
import { getToken } from "../utils/token";
import Icon from "./LearningIcon";
import "../styles/library-actions.css";

export default function LibraryItemActions({ item, folders }: { item: LibraryItem; folders: LibraryFolder[] }) {
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null);
  const [mode, setMode] = useState<"actions" | "rename" | "delete">("actions");
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
    window.addEventListener("resize", dismiss);
    window.addEventListener("scroll", dismiss, true);
    return () => {
      document.removeEventListener("pointerdown", outside);
      document.removeEventListener("keydown", escape);
      window.removeEventListener("resize", dismiss);
      window.removeEventListener("scroll", dismiss, true);
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
    } catch { setError("Không lưu được thay đổi. Vui lòng thử lại."); }
    finally { setBusy(false); }
  };
  return <>
    <button ref={trigger} type="button" className="library-more" aria-label={`Tùy chọn: ${item.title}`}
      aria-expanded={Boolean(position)} aria-controls={position ? `library-actions-${item.id}` : undefined} aria-haspopup="dialog"
      onClick={() => {
        if (position) { close(); return; }
        const rect = trigger.current!.getBoundingClientRect();
        setMode("actions"); setTitle(item.title); setError("");
        setPosition({ left: Math.max(8, Math.min(rect.right - 232, window.innerWidth - 240)), top: Math.max(8, Math.min(rect.bottom + 6, window.innerHeight - 320)) });
      }}>
      <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="1" /><circle cx="19" cy="12" r="1" /><circle cx="5" cy="12" r="1" /></svg>
    </button>
    {position && createPortal(<div ref={panel} id={`library-actions-${item.id}`} className="library-action-popover" role="dialog" aria-label={`Tùy chọn: ${item.title}`} style={position}
      onBlur={event => { if (event.relatedTarget && !event.currentTarget.contains(event.relatedTarget as Node) && event.relatedTarget !== trigger.current) setPosition(null); }}>
      {mode === "actions" ? <>
        <button type="button" disabled={busy} onClick={() => setMode("rename")}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="m16 3 5 5L8 21H3v-5ZM14 5l5 5" /></svg>Đổi tên</button>
        <div className="library-menu-divider" />
        <p className="library-menu-label">Chuyển vào thư mục</p>
        <div className="library-menu-folders">{folders.filter(folder => folder.id !== item.folderId).map(folder =>
          <button type="button" key={folder.id} disabled={busy} onClick={() => void run(() => moveLibraryItem(item.id, folder.id))}><Icon name="folder" /><span>{folder.name}</span></button>)}
          {!folders.some(folder => folder.id !== item.folderId) && <p className="library-menu-label">Chưa có thư mục khác</p>}
        </div>
        <div className="library-menu-divider" />
        <button type="button" className="library-menu-danger" disabled={busy} onClick={() => setMode("delete")}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7M14 10v7" /></svg>Xóa khỏi thư viện</button>
      </> : mode === "rename" ? <form onSubmit={event => { event.preventDefault(); if (title.trim()) void run(() => renameLibraryItem(item.id, title.trim())); }}>
        <label htmlFor={`rename-${item.id}`}>Đổi tên mô phỏng</label>
        <input id={`rename-${item.id}`} value={title} maxLength={255} disabled={busy} onChange={event => setTitle(event.target.value)} />
        <div className="library-menu-footer"><button type="button" disabled={busy} onClick={() => setMode("actions")}>Hủy</button><button type="submit" disabled={busy || !title.trim()}>Lưu</button></div>
      </form> : <>
        <p className="library-delete-copy">Xóa “{item.title}” khỏi thư viện? Mô phỏng vẫn còn trong lịch sử.</p>
        <div className="library-menu-footer"><button type="button" disabled={busy} onClick={() => setMode("actions")}>Hủy</button><button type="button" className="library-menu-danger" disabled={busy} onClick={() => void run(async () => { await deleteLibraryItem(item.id); return null; })}>Xóa</button></div>
      </>}
      {busy && <p className="library-menu-label" role="status">Đang lưu…</p>}
      {error && <p className="library-menu-error" role="alert">{error}</p>}
    </div>, document.body)}
  </>;
}
