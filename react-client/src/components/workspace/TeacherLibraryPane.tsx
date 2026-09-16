import { useMemo, useState, type FormEvent } from "react";
import type { LibraryFolder, LibraryItem } from "../../types/physlive";
import Icon from "../common/LearningIcon";
import LibraryItemActions from "./LibraryItemActions";

type Props = {
  folders: LibraryFolder[];
  items: LibraryItem[];
  currentSimulationId: string;
  loading: boolean;
  error: string;
  openingId: string | null;
  onCreateFolder: (name: string) => Promise<boolean>;
  onOpen: (item: LibraryItem) => Promise<void>;
  onNewSimulation?: () => void;
  onRetry?: () => void;
};

export default function TeacherLibraryPane({ folders, items, currentSimulationId, loading, error, openingId, onCreateFolder, onOpen, onNewSimulation, onRetry }: Readonly<Props>) {
  const [creating, setCreating] = useState(false);
  const [creatorOpen, setCreatorOpen] = useState(false);
  const [name, setName] = useState("");
  const [query, setQuery] = useState("");
  const renderItem = (item: LibraryItem) => <div key={item.id} className="learn-library-item">
    <button type="button" className={item.simulationId === currentSimulationId ? "active" : ""}
      disabled={openingId === item.id} onClick={() => void onOpen(item)}>
      <span>{openingId === item.id ? "Đang mở…" : item.title}</span>
      <small>{item.topic ?? "Physics"} · {item.validationStatus}</small>
    </button>
    <LibraryItemActions item={item} folders={folders} />
  </div>;
  const normalizedQuery = query.trim().toLocaleLowerCase("vi");
  const visibleItems = useMemo(() => normalizedQuery
    ? items.filter(item => `${item.title} ${item.topic ?? ""}`.toLocaleLowerCase("vi").includes(normalizedQuery))
    : items, [items, normalizedQuery]);
  const grouped = useMemo(() => new Map(folders.map(folder => [folder.id, visibleItems.filter(item => item.folderId === folder.id)])), [folders, visibleItems]);
  const visibleFolders = useMemo(() => normalizedQuery
    ? folders.filter(folder => folder.name.toLocaleLowerCase("vi").includes(normalizedQuery) || (grouped.get(folder.id)?.length ?? 0) > 0)
    : folders, [folders, grouped, normalizedQuery]);

  const create = async (event: FormEvent) => {
    event.preventDefault();
    if (!name.trim() || creating) return;
    setCreating(true);
    try {
      if (await onCreateFolder(name.trim())) {
        setName("");
        setCreatorOpen(false);
      }
    } finally {
      setCreating(false);
    }
  };

  return <aside className="learn-library-pane" aria-label="Thư mục mô phỏng cá nhân">
    <div className="learn-library-pane-header">
      <div className="learn-library-heading"><div><span>THƯ VIỆN CỦA TÔI</span><strong>{folders.length} thư mục · {items.length} mô phỏng</strong></div></div>
      <div className="learn-library-actions">
        {onNewSimulation && <button type="button" className="learn-new-simulation" onClick={onNewSimulation}><Icon name="plus" />Mô phỏng mới</button>}
        <button className="learn-new-folder-trigger" type="button" aria-label="Thư mục mới" title="Thư mục mới" aria-expanded={creatorOpen} aria-controls="new-library-folder-form" onClick={() => setCreatorOpen(value => !value)}><Icon name="folderPlus" /></button>
      </div>
      <label className="learn-library-search"><Icon name="search" /><span className="learn-sr-only">Tìm trong thư viện</span><input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Tìm trong thư viện" /></label>
      {creatorOpen && <form id="new-library-folder-form" className="learn-folder-popover" onSubmit={create}>
        <label htmlFor="new-library-folder">Tạo thư mục mới</label>
        <input id="new-library-folder" autoFocus value={name} maxLength={120} onChange={event => setName(event.target.value)} placeholder="Ví dụ: Chuyển động lớp 10" disabled={creating} />
        <div><button type="button" onClick={() => { setCreatorOpen(false); setName(""); }} disabled={creating}>Hủy</button><button className="primary" type="submit" disabled={!name.trim() || creating}>{creating ? "Đang tạo…" : "Tạo thư mục"}</button></div>
      </form>}
    </div>
    <div className="learn-library-tree">
      {loading && <p className="learn-library-state">Đang tải thư viện…</p>}
      {!loading && error && <div className="learn-library-state error" role="alert">{error} {onRetry && <button type="button" onClick={onRetry}>Thử lại</button>}</div>}
      {!loading && !error && folders.length === 0 && <div className="learn-library-empty"><strong>Chưa có thư mục</strong><p>Tạo thư mục để lưu và tổ chức các mô phỏng đã kiểm chứng.</p></div>}
      {visibleFolders.map(folder => <details className="learn-library-folder" key={folder.id} open>
        <summary className="learn-library-folder-name"><Icon name="folder" /><strong title={folder.name}>{folder.name}</strong><small>{grouped.get(folder.id)?.length ?? 0}</small></summary>
        <div className="learn-library-items">
          {(grouped.get(folder.id) ?? []).map(renderItem)}
          {(grouped.get(folder.id)?.length ?? 0) === 0 && <p>Chưa có mô phỏng</p>}
        </div>
      </details>)}
      {visibleItems.some(item => !item.folderId) && <details className="learn-library-folder legacy" open>
        <summary className="learn-library-folder-name"><Icon name="folder" /><strong>Chưa phân loại</strong><small>{visibleItems.filter(item => !item.folderId).length}</small></summary>
        <div className="learn-library-items">{visibleItems.filter(item => !item.folderId).map(renderItem)}</div>
      </details>}
      {!loading && !error && normalizedQuery && visibleFolders.length === 0 && !visibleItems.some(item => !item.folderId) && <p className="learn-library-state">Không tìm thấy thư mục hoặc mô phỏng phù hợp.</p>}
    </div>
  </aside>;
}
