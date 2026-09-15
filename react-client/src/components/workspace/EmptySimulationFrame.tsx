import { useState, type ReactNode } from "react";
import { LearningHeader } from "./LearningWorkspace";
import { CREATE_SIMULATION_EXAMPLES } from "./workspaceResources";
import TeacherLibraryPane from "./TeacherLibraryPane";
import Icon from "../common/LearningIcon";
import type { LibraryFolder, LibraryItem, Simulation } from "../../types/physlive";
import "../../styles/learning.css";

type Props = {
  showTeacherLibrary: boolean;
  folders: LibraryFolder[];
  libraryItems: LibraryItem[];
  libraryLoading: boolean;
  libraryError: string;
  openingLibraryId: string | null;
  onRetryLibrary: () => void;
  onCreateFolder: (name: string) => Promise<boolean>;
  onOpenLibraryItem: (item: LibraryItem) => Promise<void>;
  onNewSimulation: () => void;
  createPanel?: ReactNode;
  recent: Simulation[];
  historyLoading: boolean;
  historyError: boolean;
  onRetryHistory: () => void;
  onOpenRecent: (item: Simulation) => void;
  onExampleSelect: (example: string) => void;
};

/** Same chrome as LearningWorkspace (header + library + stage + inspector) with an empty stage CTA. */
export default function EmptySimulationFrame({
  showTeacherLibrary,
  folders,
  libraryItems,
  libraryLoading,
  libraryError,
  openingLibraryId,
  onRetryLibrary,
  onCreateFolder,
  onOpenLibraryItem,
  onNewSimulation,
  createPanel,
  recent,
  historyLoading,
  historyError,
  onRetryHistory,
  onOpenRecent,
  onExampleSelect,
}: Props) {
  const [libraryCollapsed, setLibraryCollapsed] = useState(false);
  const [mobilePanel, setMobilePanel] = useState<"observe" | "inspect">("observe");

  return (
    <div className="learning-app">
      <LearningHeader
        onNewSimulation={onNewSimulation}
        libraryCollapsed={libraryCollapsed}
        onToggleLibrary={showTeacherLibrary ? () => setLibraryCollapsed(value => !value) : undefined}
      />
      <main className="learn-workspace" id="learning-workspace">
        <div className="learn-top-area" aria-hidden="true" />
        <nav className="learn-mobile-nav" aria-label="Chuyển vùng học tập">
          <button type="button" aria-pressed={mobilePanel === "observe"} onClick={() => setMobilePanel("observe")}>
            <Icon name="play" />Quan sát
          </button>
          <button type="button" aria-pressed={mobilePanel === "inspect"} onClick={() => setMobilePanel("inspect")}>
            <Icon name="book" />Hướng dẫn
          </button>
        </nav>
        <div
          className="learn-layout"
          data-mobile-panel={mobilePanel}
          data-library-pane={showTeacherLibrary}
          data-library-collapsed={libraryCollapsed}
        >
          {showTeacherLibrary && (
            <TeacherLibraryPane
              folders={folders}
              items={libraryItems}
              onRetry={onRetryLibrary}
              currentSimulationId=""
              loading={libraryLoading}
              error={libraryError}
              openingId={openingLibraryId}
              onCreateFolder={onCreateFolder}
              onOpen={onOpenLibraryItem}
              onNewSimulation={onNewSimulation}
            />
          )}
          <section className="learn-exploration" aria-label="Quan sát và khám phá">
            <section className="learn-stage" aria-label="Khung mô phỏng">
              {createPanel}
              {!createPanel && (
                <div className="learn-stage-wrapper">
                <div className="learn-canvas-container">
                  <div className="learn-canvas">
                    <div className="learn-empty" style={{ minHeight: "100%", padding: 40 }}>
                      <span className="learn-empty-icon"><Icon name="atom" /></span>
                      <span className="learn-small-label">SIMULATION FRAME</span>
                      <h1>Chưa có mô phỏng đang mở</h1>
                      <p>Tạo đề bài mới hoặc mở một mô phỏng đã lưu từ thư viện. Giao diện khung mô phỏng giữ nguyên như khi xem mô phỏng hiện có.</p>
                      <button type="button" className="learn-primary-link" style={{ border: 0, cursor: "pointer", background: "transparent" }} onClick={onNewSimulation}>
                        Nhập đề bài mới <Icon name="arrow" />
                      </button>
                    </div>
                  </div>
                </div>
                </div>
              )}
            </section>
          </section>
          <aside className="learn-inspector learn-inspector-empty" aria-label="Hướng dẫn">
            <div className={createPanel ? "learn-inspector-body create-resources-mode" : "learn-inspector-body"} style={{ padding: 20 }}>
              {createPanel && (
                <div className="create-resources-panel">
                  <div className="create-resources-section">
                    <span className="learn-small-label">Điền nhanh</span>
                    <p className="create-resources-note">Chọn một đề mẫu để bắt đầu.</p>
                    <div className="create-example-list">
                      {CREATE_SIMULATION_EXAMPLES.map((example, index) => (
                        <button type="button" className="create-example-item" key={example} onClick={() => onExampleSelect(example)}>
                          <span>{index + 1}</span>
                          <strong>{example}</strong>
                        </button>
                      ))}
                    </div>
                  </div>
                  <div className="create-resources-section">
                    <div className="create-resources-heading">
                      <span className="learn-small-label">Mô phỏng gần đây</span>
                      {!historyLoading && historyError && <button type="button" className="create-resource-retry" onClick={onRetryHistory}>Thử lại</button>}
                    </div>
                    {historyLoading && <p className="create-resources-note">Đang tải mô phỏng…</p>}
                    {!historyLoading && historyError && <p className="create-resources-error">Không tải được mô phỏng gần đây.</p>}
                    {!historyLoading && !historyError && recent.length === 0 && <p className="create-resources-note">Chưa có mô phỏng nào.</p>}
                    {!historyLoading && !historyError && recent.length > 0 && (
                      <div className="create-recent-list">
                        {recent.slice(0, 6).map(item => (
                          <button type="button" className="create-recent-item" key={item.simulationId} onClick={() => onOpenRecent(item)}>
                            <span className="create-recent-icon"><Icon name="atom" /></span>
                            <span><strong>{item.schemaId}</strong><small>{item.time.length} mốc dữ liệu</small></span>
                            <Icon name="arrow" />
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}
              <span className="learn-small-label">AI PROBLEM UNDERSTANDING</span>
              <h2 style={{ marginTop: 8 }}>Từ đề bài đến mô phỏng đã kiểm chứng</h2>
              <ol style={{ marginTop: 16, paddingLeft: 18, color: "var(--learn-muted)", fontSize: 13, lineHeight: 1.7 }}>
                <li><strong>AI hiểu đề</strong> — nhận diện mô hình, đại lượng và quan hệ.</li>
                <li><strong>Xác nhận</strong> — trả lời ambiguity do AI phát hiện.</li>
                <li><strong>Đối chiếu</strong> — solver kiểm tra trước khi hiển thị trên khung mô phỏng.</li>
              </ol>
              <button type="button" className="learn-save-button" style={{ marginTop: 20 }} onClick={onNewSimulation}>
                Mở form tạo mô phỏng
              </button>
            </div>
          </aside>
        </div>
      </main>
    </div>
  );
}
