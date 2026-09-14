import { useState } from "react";
import WorkspaceSidebar from "./WorkspaceSidebar";
import WorkspaceCanvas from "./WorkspaceCanvas";
import WorkspaceInspector from "./WorkspaceInspector";
import CreateSimulationModal from "./CreateSimulationModal";
import "../../styles/workspace-modal.css";

export interface SimulationItem {
  id: string;
  title: string;
  type: "motion" | "projectile" | "circuit" | "collision";
  parameters: Record<string, number>;
  createdAt: Date;
}

export interface FolderItem {
  id: string;
  name: string;
  grade: number;
  items: SimulationItem[];
  expanded: boolean;
}

export default function WorkspaceLayout() {
  const [folders, setFolders] = useState<FolderItem[]>([
    {
      id: "folder-1",
      name: "Chuyển động thẳng",
      grade: 10,
      expanded: true,
      items: [
        {
          id: "sim-1",
          title: "Chuyển động thẳng đều",
          type: "motion",
          parameters: { v0: 10, a: 0, t: 5 },
          createdAt: new Date(),
        },
        {
          id: "sim-2",
          title: "Chuyển động biến đổi đều",
          type: "motion",
          parameters: { v0: 0, a: 2, t: 5 },
          createdAt: new Date(),
        },
      ],
    },
    {
      id: "folder-2",
      name: "Ném xiên",
      grade: 10,
      expanded: false,
      items: [
        {
          id: "sim-3",
          title: "Ném từ độ cao h",
          type: "projectile",
          parameters: { v0: 15, alpha: 45, h: 20, g: 9.8 },
          createdAt: new Date(),
        },
      ],
    },
    {
      id: "folder-3",
      name: "Va chạm",
      grade: 10,
      expanded: false,
      items: [],
    },
  ]);

  const [selectedItem, setSelectedItem] = useState<SimulationItem | null>(
    folders[0]?.items[0] || null
  );
  const [activeFolderId, setActiveFolderId] = useState(folders[0]?.id || "");
  const [refreshKey, setRefreshKey] = useState(0);

  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [inspectorCollapsed, setInspectorCollapsed] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingFolderId, setEditingFolderId] = useState<string | null>(null);

  const handleSelectItem = (item: SimulationItem) => {
    setSelectedItem(item);
    const parentFolder = folders.find((folder) =>
      folder.items.some((folderItem) => folderItem.id === item.id)
    );
    if (parentFolder) setActiveFolderId(parentFolder.id);
    setRefreshKey(k => k + 1);
  };

  const handleParameterChange = () => {
    setRefreshKey(k => k + 1);
  };

  const selectFolder = (folderId: string) => {
    setActiveFolderId(folderId);
    setSelectedItem(null);
    setFolders((currentFolders) => currentFolders.map((folder) =>
      folder.id === folderId ? { ...folder, expanded: true } : folder
    ));
  };

  const openCreateTopic = () => {
    setEditingFolderId(null);
    setShowCreateModal(true);
  };

  const openEditTopic = (folderId: string) => {
    setEditingFolderId(folderId);
    setActiveFolderId(folderId);
    setShowCreateModal(true);
  };

  const handleSaveTopic = (topic: string, grade: number) => {
    if (editingFolderId) {
      setFolders((currentFolders) => currentFolders.map((folder) =>
        folder.id === editingFolderId ? { ...folder, name: topic, grade } : folder
      ));
      return;
    }

    const id = `folder-${Date.now()}`;
    const newFolder: FolderItem = {
      id,
      name: topic,
      grade,
      items: [],
      expanded: true,
    };

    setFolders((currentFolders) => [...currentFolders, newFolder]);
    setActiveFolderId(id);
    setSelectedItem(null);
  };

  const handleCreateSimulation = async (description: string) => {
    const activeFolder = folders.find((folder) => folder.id === activeFolderId);
    if (!activeFolder) return;

    const normalizedText = `${activeFolder.name} ${description}`.toLowerCase();
    const type: SimulationItem["type"] = normalizedText.includes("ném")
      ? "projectile"
      : normalizedText.includes("va chạm")
        ? "collision"
        : normalizedText.includes("mạch") || normalizedText.includes("điện")
          ? "circuit"
          : "motion";

    const parametersByType: Record<SimulationItem["type"], Record<string, number>> = {
      motion: { v0: 10, a: 2, t: 5 },
      projectile: { v0: 20, alpha: 45, h: 0, g: 9.8 },
      circuit: { V0: 12, R: 100, C: 10 },
      collision: { m1: 2, m2: 3, v1: 5, v2: -2 },
    };

    const newSim: SimulationItem = {
      id: `sim-${Date.now()}`,
      title: description.length > 40 ? `${description.substring(0, 40)}...` : description,
      type,
      parameters: parametersByType[type],
      createdAt: new Date(),
    };

    setFolders((currentFolders) => currentFolders.map((folder) =>
      folder.id === activeFolderId
        ? { ...folder, items: [...folder.items, newSim], expanded: true }
        : folder
    ));
    setSelectedItem(newSim);
    setRefreshKey((key) => key + 1);
  };

  const activeFolder = folders.find((folder) => folder.id === activeFolderId) || null;

  return (
    <div className="workspace-container">
      {/* Header */}
      <header className="workspace-header">
        <div className="workspace-brand">
          <img className="workspace-brand-badge" src="/favicon.ico" alt="" />
          <div className="workspace-brand-text">
            <span className="workspace-brand-title">PhysLive</span>
            <span className="workspace-brand-subtitle">Workspace</span>
          </div>
        </div>
        
        <nav className="workspace-tabs">
          <button className="workspace-tab active">Workspace</button>
        </nav>

        <div className="workspace-header-actions">
          <button className="workspace-header-btn active">2D</button>
        </div>
      </header>

      {/* 3-Panel Layout */}
      <div 
        className="workspace-main"
        data-sidebar-collapsed={sidebarCollapsed}
        data-inspector-collapsed={inspectorCollapsed}
      >
        {/* Left Sidebar - Navigation Tree */}
        <WorkspaceSidebar
          folders={folders}
          selectedItem={selectedItem}
          onSelectItem={handleSelectItem}
          activeFolderId={activeFolderId}
          onSelectFolder={selectFolder}
          onEditFolder={openEditTopic}
          onCreateNew={openCreateTopic}
          collapsed={sidebarCollapsed}
          onToggleCollapse={() => setSidebarCollapsed(!sidebarCollapsed)}
        />

        {/* Center Canvas - Simulation */}
        <WorkspaceCanvas
          selectedItem={selectedItem}
          key={refreshKey}
        />

        {/* Right Inspector - Parameters */}
        <WorkspaceInspector
          selectedItem={selectedItem}
          collapsed={inspectorCollapsed}
          onToggleCollapse={() => setInspectorCollapsed(!inspectorCollapsed)}
          onParameterChange={handleParameterChange}
          activeFolder={activeFolder}
          onCreateSimulation={handleCreateSimulation}
        />
      </div>

      {/* Create Simulation Modal */}
      <CreateSimulationModal
        key={`topic-${editingFolderId ?? "new"}-${showCreateModal}`}
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onCreate={handleSaveTopic}
        initialTopic={
          editingFolderId
            ? folders.find((folder) => folder.id === editingFolderId)?.name
            : ""
        }
        initialGrade={
          editingFolderId
            ? folders.find((folder) => folder.id === editingFolderId)?.grade
            : 10
        }
        isEditing={Boolean(editingFolderId)}
      />
    </div>
  );
}
