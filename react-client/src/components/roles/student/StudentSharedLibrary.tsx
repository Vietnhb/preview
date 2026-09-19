import PhysicsScene from "../../simulation/PhysicsScene";
import type { LibraryItem, Simulation } from "../../../types/physlive";

type SharedLibraryProps = {
  items: LibraryItem[];
  selectedTopic: string;
  loading: boolean;
  selectedItem: LibraryItem | null;
  simulation: Simulation | null;
  time: number;
  simulationLoading: boolean;
  simulationError: string;
  frame: number;
  playing: boolean;
  vectors: {
    grid: boolean;
    trajectory: boolean;
    velocity: boolean;
    acceleration: boolean;
  };
  onTopicChange: (topic: string) => void;
  onOpen: (item: LibraryItem) => void;
  onClose: () => void;
  onTogglePlaying: () => void;
  onReset: () => void;
  onFrameChange: (frame: number) => void;
  onTimeChange: (time: number) => void;
  onPlaybackEnd: () => void;
};

export function SharedLibrary({
  items,
  selectedTopic,
  loading,
  selectedItem,
  simulation,
  time,
  simulationLoading,
  simulationError,
  frame,
  playing,
  vectors,
  onTopicChange,
  onOpen,
  onClose,
  onTogglePlaying,
  onReset,
  onFrameChange,
  onTimeChange,
  onPlaybackEnd,
}: Readonly<SharedLibraryProps>) {
  const topics = ["", "Kinematics", "Dynamics", "Circuits"];
  return (
    <div className="modern-card">
      <div className="modern-card-header">
        <div>
          <h2>Tài nguyên lớp học</h2>
          <p>Xem và chạy các mô phỏng đã được giáo viên chia sẻ.</p>
        </div>
        <div style={{ display: "flex", gap: "8px" }}>
          {topics.map((topic) => (
            <button
              key={topic}
              type="button"
              className={`role-switch-pill ${selectedTopic === topic ? "active" : ""}`}
              style={{ border: "1px solid var(--border-subtle)" }}
              onClick={() => onTopicChange(topic)}
            >
              {topic || "Tất cả chủ đề"}
            </button>
          ))}
        </div>
      </div>
      {loading && (
        <p style={{ color: "var(--text-muted)", padding: "20px" }}>
          Đang tải thư viện…
        </p>
      )}
      {!loading && items.length === 0 && (
        <div
          style={{
            padding: "40px",
            textAlign: "center",
            color: "var(--text-muted)",
          }}
        >
          Chưa có mô hình nào trong danh mục này.
        </div>
      )}
      {!loading && items.length > 0 && (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
            gap: "16px",
          }}
        >
          {items.map((item) => (
            <div
              key={item.id}
              className="modern-card"
              style={{ margin: 0, padding: "16px" }}
            >
              <span
                className="status-pill pass"
                style={{ marginBottom: "8px" }}
              >
                {item.topic || "Vật lý"}
              </span>
              <h3 style={{ fontSize: "15px", margin: "0 0 8px 0" }}>
                {item.title}
              </h3>
              <small
                style={{
                  color: "var(--text-muted)",
                  display: "block",
                  marginBottom: "14px",
                }}
              >
                Đã kiểm chứng: {item.validationStatus}
              </small>
              <button
                type="button"
                className="prediction-submit-btn"
                style={{ padding: "6px 12px", fontSize: "12px" }}
                onClick={() => onOpen(item)}
              >
                Xem tài nguyên
              </button>
            </div>
          ))}
        </div>
      )}
      {selectedItem && (
        <section className="student-resource-player">
          <div className="student-resource-player-header">
            <div>
              <span className="status-pill info">Tài nguyên được chia sẻ</span>
              <h3>{selectedItem.title}</h3>
            </div>
            <button type="button" className="modern-tab-btn" onClick={onClose}>
              Đóng
            </button>
          </div>
          {simulationLoading && (
            <p className="student-player-state">Đang tải mô hình…</p>
          )}
          {!simulationLoading && simulationError !== "" && (
            <p className="student-player-state error">{simulationError}</p>
          )}
          {!simulationLoading && simulationError === "" && simulation && (
            <>
              <div className="student-resource-scene">
                <PhysicsScene
                  simulation={simulation}
                  index={frame}
                  overlays={vectors}
                  time={time}
                  playing={playing}
                  onTimeChange={onTimeChange}
                  onPlaybackEnd={onPlaybackEnd}
                />
              </div>
              <div className="student-playback">
                <button
                  type="button"
                  className="prediction-submit-btn"
                  onClick={onTogglePlaying}
                >
                  {playing ? "Tạm dừng" : "Chạy mô phỏng"}
                </button>
                <button
                  type="button"
                  className="modern-tab-btn"
                  onClick={onReset}
                >
                  Tua về đầu
                </button>
                <input
                  type="range"
                  min={0}
                  max={Math.max(0, simulation.time.length - 1)}
                  value={frame}
                  onChange={(event) =>
                    onFrameChange(Number(event.target.value))
                  }
                />
                <span>{time.toFixed(2)} s</span>
              </div>
            </>
          )}
        </section>
      )}
    </div>
  );
}
