import type { FolderItem, SimulationItem } from "./WorkspaceLayout";

interface WorkspaceSidebarProps {
  folders: FolderItem[];
  selectedItem: SimulationItem | null;
  activeFolderId: string;
  onSelectItem: (item: SimulationItem) => void;
  onSelectFolder: (folderId: string) => void;
  onEditFolder: (folderId: string) => void;
  onCreateNew: () => void;
  collapsed: boolean;
  onToggleCollapse: () => void;
}

export default function WorkspaceSidebar({
  folders,
  selectedItem,
  activeFolderId,
  onSelectItem,
  onSelectFolder,
  onEditFolder,
  onCreateNew,
  collapsed,
  onToggleCollapse,
}: WorkspaceSidebarProps) {
  if (collapsed) {
    return (
      <aside className="workspace-sidebar collapsed">
        <button 
          className="sidebar-collapse-btn"
          onClick={onToggleCollapse}
          title="Mở sidebar"
        >
          ▶
        </button>
      </aside>
    );
  }

  return (
    <aside className="workspace-sidebar">
      <div className="sidebar-header">
        <button className="sidebar-new-btn" onClick={onCreateNew}>
          <span className="sidebar-new-icon">+</span>
          <span>Tạo topic mới</span>
        </button>
        <button 
          className="sidebar-collapse-btn"
          onClick={onToggleCollapse}
          title="Thu gọn sidebar"
        >
          ◀
        </button>
      </div>

      <div className="sidebar-content">
        <div className="sidebar-section">
          <div className="sidebar-section-header">
            <span className="sidebar-section-title">THƯ VIỆN MÔ PHỎNG</span>
            <span className="sidebar-section-count">
              {folders.reduce((sum, f) => sum + f.items.length, 0)} phần
            </span>
          </div>
        </div>

        <div className="sidebar-tree">
          {folders.map((folder) => (
            <div key={folder.id} className="sidebar-folder">
              <div className={`sidebar-folder-row ${activeFolderId === folder.id ? 'active' : ''}`}>
                <button
                  className="sidebar-folder-toggle"
                  onClick={() => onSelectFolder(folder.id)}
                >
                  <span className="folder-icon">{activeFolderId === folder.id ? '📂' : '📁'}</span>
                  <span className="folder-name">{folder.name}</span>
                  <span className="folder-grade">Lớp {folder.grade}</span>
                  <span className="folder-count">{folder.items.length}</span>
                </button>
                <button
                  className="folder-edit-btn"
                  onClick={() => onEditFolder(folder.id)}
                  title="Đổi tên topic"
                  aria-label={`Đổi tên ${folder.name}`}
                >
                  ✎
                </button>
              </div>

              {folder.expanded && (
                <div className="sidebar-folder-items">
                  {folder.items.length === 0 ? (
                    <div className="sidebar-empty">Chưa có mô phỏng</div>
                  ) : (
                    folder.items.map((item) => (
                      <button
                        key={item.id}
                        className={`sidebar-item ${selectedItem?.id === item.id ? 'active' : ''}`}
                        onClick={() => onSelectItem(item)}
                      >
                        <span className="item-icon">📊</span>
                        <span className="item-title">{item.title}</span>
                        <span className="item-arrow">➜</span>
                      </button>
                    ))
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </aside>
  );
}
