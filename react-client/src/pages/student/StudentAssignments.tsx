import { useState, useEffect, useCallback, useMemo } from "react";
import PhysicsScene from "../../components/simulation/PhysicsScene";
import { assignedSimulation, studentAssignments, submitAssignmentPrediction } from "../../api/assignmentApi";
import { getSharedSimulation } from "../../api/simulationApi";
import { library } from "../../api/libraryApi";
import type { Assignment, LibraryItem, Simulation } from "../../types/physlive";
import LearningIcon from "../../components/common/LearningIcon";
import "../../styles/modern-roles.css";

interface PredictionPayload {
  answerText: string;
  estimatedValue?: number;
  reasoning?: string;
}

export default function StudentAssignments({ initialTab = "assigned" }: { initialTab?: "assigned" | "library" }) {
  const [activeTab, setActiveTab] = useState<"assigned" | "library">(initialTab);

  // Assigned items
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Selected assignment for practicing
  const [selectedAssignment, setSelectedAssignment] = useState<Assignment | null>(null);
  const [simulation, setSimulation] = useState<Simulation | null>(null);
  const [simLoading, setSimLoading] = useState(false);
  const [simError, setSimError] = useState("");

  // Prediction Gate State
  const [predictionSubmitted, setPredictionSubmitted] = useState(false);
  const [submittedPredictionText, setSubmittedPredictionText] = useState("");
  const [predictionInput, setPredictionInput] = useState("");
  const [reasoningInput, setReasoningInput] = useState("");
  const [isSubmittingPrediction, setIsSubmittingPrediction] = useState(false);
  const [predictionError, setPredictionError] = useState("");

  // Playback state
  const [frame, setFrame] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [vectors, setVectors] = useState({ grid: true, trajectory: true, velocity: true, acceleration: false });

  // Class / Shared Library state
  const [sharedItems, setSharedItems] = useState<LibraryItem[]>([]);
  const [selectedTopic, setSelectedTopic] = useState("");
  const [sharedLoading, setSharedLoading] = useState(false);
  const [selectedSharedItem, setSelectedSharedItem] = useState<LibraryItem | null>(null);
  const [sharedSimulation, setSharedSimulation] = useState<Simulation | null>(null);
  const [sharedSimLoading, setSharedSimLoading] = useState(false);
  const [sharedSimError, setSharedSimError] = useState("");
  const [sharedFrame, setSharedFrame] = useState(0);
  const [sharedPlaying, setSharedPlaying] = useState(false);

  const loadAssignments = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await studentAssignments();
      setAssignments(data);
    } catch {
      setError("Không thể tải danh sách bài tập được giao.");
    } finally {
      setLoading(false);
    }
  }, []);

  const loadSharedLibrary = useCallback(async (topic?: string) => {
    setSharedLoading(true);
    try {
      const data = await library(topic || undefined);
      setSharedItems(data.filter(item => item.visibility === "SHARED"));
    } catch {
      // ignore
    } finally {
      setSharedLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadAssignments();
  }, [loadAssignments]);

  useEffect(() => {
    if (activeTab === "library") {
      void loadSharedLibrary(selectedTopic);
    }
  }, [activeTab, selectedTopic, loadSharedLibrary]);

  const loadAssignedSimulation = useCallback(async (assignmentId: string) => {
    setSimLoading(true);
    setSimError("");
    try {
      setSimulation(await assignedSimulation(assignmentId));
    } catch {
      setSimulation(null);
      setSimError("Chưa tải được mô hình mô phỏng của bài tập này.");
    } finally {
      setSimLoading(false);
    }
  }, []);

  // The simulation is deliberately requested only after the prediction gate is open.
  const handleSelectAssignment = async (item: Assignment) => {
    setSelectedAssignment(item);
    const alreadySubmitted = Boolean(item.predictionSubmitted);
    setPredictionSubmitted(alreadySubmitted);
    setSubmittedPredictionText(alreadySubmitted ? "Dự đoán đã được gửi trước đó." : "");
    setPredictionInput("");
    setReasoningInput("");
    setPredictionError("");
    setSimulation(null);
    setFrame(0);
    setPlaying(false);
    setSimError("");
    if (alreadySubmitted) await loadAssignedSimulation(item.id);
  };

  const handleOpenShared = async (item: LibraryItem) => {
    setSelectedSharedItem(item);
    setSharedSimulation(null);
    setSharedFrame(0);
    setSharedPlaying(false);
    setSharedSimLoading(true);
    setSharedSimError("");
    try {
      setSharedSimulation(await getSharedSimulation(item.simulationId));
    } catch {
      setSharedSimError("Chưa tải được mô phỏng trong tài nguyên này.");
    } finally {
      setSharedSimLoading(false);
    }
  };

  // Submit Prediction Gate (FR-STU-02)
  const handleSubmitPrediction = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAssignment || !predictionInput.trim()) return;

    setIsSubmittingPrediction(true);
    setPredictionError("");

    const payload: PredictionPayload = {
      answerText: predictionInput.trim(),
      reasoning: reasoningInput.trim() || undefined
    };

    try {
      await submitAssignmentPrediction(selectedAssignment.id, payload);
      setPredictionSubmitted(true);
      setSubmittedPredictionText(predictionInput.trim());
      await loadAssignedSimulation(selectedAssignment.id);
    } catch (err: unknown) {
      // If student has already submitted prediction previously, unlock simulation
      if (typeof err === "object" && err !== null && "response" in err) {
        const axiosErr = err as { response?: { status?: number } };
        if (axiosErr.response?.status === 409) {
          setPredictionSubmitted(true);
          setSubmittedPredictionText(predictionInput.trim() || "Dự đoán đã ghi nhận trước đó");
          await loadAssignedSimulation(selectedAssignment.id);
          return;
        }
      }
      setPredictionError("Không thể ghi nhận câu trả lời dự đoán. Vui lòng thử lại.");
    } finally {
      setIsSubmittingPrediction(false);
    }
  };

  // Playback timer
  useEffect(() => {
    if (!playing || !simulation?.time.length) return;
    const interval = window.setInterval(() => {
      setFrame(current => (current + 1) % simulation.time.length);
    }, 40);
    return () => window.clearInterval(interval);
  }, [playing, simulation]);

  useEffect(() => {
    if (!sharedPlaying || !sharedSimulation?.time.length) return;
    const interval = window.setInterval(() => {
      setSharedFrame(current => (current + 1) % sharedSimulation.time.length);
    }, 40);
    return () => window.clearInterval(interval);
  }, [sharedPlaying, sharedSimulation]);

  const teacherPrompt = useMemo(() => {
    if (!selectedAssignment?.questions) return "Hãy quan sát hiện tượng và đưa ra dự đoán kết quả trước khi chạy mô phỏng.";
    if (typeof selectedAssignment.questions === "object" && selectedAssignment.questions !== null && "prompt" in selectedAssignment.questions) {
      return String((selectedAssignment.questions as { prompt: unknown }).prompt);
    }
    return String(selectedAssignment.questions);
  }, [selectedAssignment]);

  return (
    <div className={`main student-main student-layout-${activeTab}`}>
      <div className="modern-container">
      {/* Header */}
      <header className="modern-header">
        <div className="modern-header-title">
          <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "6px" }}>
            <h1>Bài tập của tôi</h1>
            <span className="modern-badge-role student">Học sinh</span>
          </div>
          <p>Xem bài được giao, gửi dự đoán trước khi chạy mô phỏng và học từ tài nguyên được chia sẻ.</p>
        </div>
      </header>

      {/* Tabs */}
      <div className="modern-tabs">
        <button
          className={`modern-tab-btn ${activeTab === "assigned" ? "active" : ""}`}
          onClick={() => { setActiveTab("assigned"); setSelectedAssignment(null); setSelectedSharedItem(null); }}
        >
          <span>Bài tập được giao</span>
          <span className="modern-tab-badge">{assignments.length}</span>
        </button>
        <button
          className={`modern-tab-btn ${activeTab === "library" ? "active" : ""}`}
          onClick={() => { setActiveTab("library"); setSelectedAssignment(null); }}
        >
          <span>Tài nguyên lớp học</span>
        </button>
      </div>

      {/* TAB 1: ASSIGNED SIMULATIONS */}
      {activeTab === "assigned" && (
        <>
          {!selectedAssignment ? (
            <div className="modern-card">
              <div className="modern-card-header">
                <div>
                  <h2>Danh sách bài tập cần hoàn thành</h2>
                  <p>Chọn một bài tập để bắt đầu dự đoán hiện tượng và mở khóa kết quả mô phỏng.</p>
                </div>
                <button
                  type="button"
                  className="modern-tab-btn"
                  style={{ border: "1px solid var(--border-subtle)", background: "#ffffff" }}
                  onClick={loadAssignments}
                  disabled={loading}
                >
                  {loading ? "Đang tải…" : "Làm mới"}
                </button>
              </div>

              {loading && <p style={{ color: "var(--text-muted)", padding: "20px" }}>Đang tải danh sách bài tập từ giáo viên…</p>}
              {error && <div className="status-pill fail" style={{ margin: "16px" }}>{error}</div>}

              {!loading && assignments.length === 0 ? (
                <div style={{ padding: "40px 20px", textAlign: "center", color: "var(--text-muted)" }}>
                  <LearningIcon name="book" />
                  <h3 style={{ margin: "0 0 8px 0", color: "var(--text-primary)" }}>Chưa có bài tập nào được giao</h3>
                  <p style={{ margin: 0, fontSize: "14px" }}>Khi giáo viên giao bài mô phỏng, bài tập sẽ xuất hiện tại đây.</p>
                </div>
              ) : (
                <div className="modern-table-wrapper">
                  <table className="modern-table">
                    <thead>
                      <tr>
                        <th>Tên bài tập</th>
                        <th>Nội dung / Câu hỏi dự đoán</th>
                        <th>Hạn hoàn thành</th>
                        <th>Trạng thái</th>
                        <th>Thao tác</th>
                      </tr>
                    </thead>
                    <tbody>
                      {assignments.map(item => (
                        <tr key={item.id}>
                          <td>
                            <strong>{item.title}</strong>
                          </td>
                          <td style={{ maxWidth: "380px" }}>
                            <span style={{ color: "var(--text-secondary)", fontSize: "13px" }}>
                              {typeof item.questions === "object" && item.questions && "prompt" in item.questions
                                ? String((item.questions as { prompt: string }).prompt)
                                : item.description || "Dự đoán hiện tượng"}
                            </span>
                          </td>
                          <td>
                            <small style={{ color: "var(--text-muted)" }}>
                              {item.dueAt ? new Date(item.dueAt).toLocaleString("vi-VN") : "Không giới hạn"}
                            </small>
                          </td>
                          <td>
                            <span className={item.predictionSubmitted ? "status-pill completed" : "status-pill draft"}>
                              {item.predictionSubmitted ? "Đã dự đoán" : "Chưa làm"}
                            </span>
                          </td>
                          <td>
                            <button
                              type="button"
                              className="prediction-submit-btn"
                              style={{ padding: "6px 14px", fontSize: "13px" }}
                              onClick={() => void handleSelectAssignment(item)}
                            >
                              {item.predictionSubmitted ? "Xem lại" : "Làm bài"}
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          ) : (
            /* DETAILED ASSIGNMENT WORKBENCH WITH PREDICTION GATE */
            <div>
              <div style={{ marginBottom: "16px" }}>
                <button
                  type="button"
                  className="modern-tab-btn"
                  style={{ background: "#ffffff", border: "1px solid var(--border-subtle)" }}
                  onClick={() => setSelectedAssignment(null)}
                >
                  ← Trở về danh sách bài tập
                </button>
              </div>

              {/* Assignment Overview */}
              <div className="modern-card">
                <div className="modern-card-header">
                  <div>
                    <span className="status-pill info" style={{ marginBottom: "6px" }}>Bài tập mô phỏng</span>
                    <h2>{selectedAssignment.title}</h2>
                    {selectedAssignment.description && (
                      <p style={{ marginTop: "6px", color: "var(--text-secondary)" }}>
                        {selectedAssignment.description}
                      </p>
                    )}
                  </div>
                  {selectedAssignment.dueAt && (
                    <div style={{ textAlign: "right" }}>
                      <small style={{ color: "var(--text-muted)", display: "block" }}>Hạn nộp bài</small>
                      <strong>{new Date(selectedAssignment.dueAt).toLocaleString("vi-VN")}</strong>
                    </div>
                  )}
                </div>

                {/* PREDICTION GATE (FR-STU-02): Required before simulation unlocking */}
                {!predictionSubmitted ? (
                  <div className="prediction-gate-card">
                    <span className="prediction-gate-badge"><LearningIcon name="shield" /> Cổng dự đoán bắt buộc</span>
                    <h3 className="prediction-gate-title">Câu hỏi dự đoán trước khi xem mô phỏng</h3>
                    <p className="prediction-gate-desc">
                      Theo nguyên tắc học tập tương tác, bạn cần đưa ra giả thuyết / dự đoán kết quả trước. 
                      Ngay sau khi gửi câu trả lời, mô phỏng chuyển động thực tế sẽ được mở khóa hoàn toàn.
                    </p>

                    <div className="prediction-prompt-box">
                      <LearningIcon name="message" /> {teacherPrompt}
                    </div>

                    <form onSubmit={handleSubmitPrediction} className="prediction-input-area">
                      <label style={{ fontSize: "13px", fontWeight: "600", color: "#166534" }}>
                        Câu trả lời / Dự đoán của bạn:
                      </label>
                      <textarea
                        rows={3}
                        required
                        placeholder="Ví dụ: Vận tốc trước khi chạm đất là khoảng 14 m/s vì gia tốc trọng trường g = 9.8 m/s²..."
                        value={predictionInput}
                        onChange={e => setPredictionInput(e.target.value)}
                        disabled={isSubmittingPrediction}
                      />

                      <label style={{ fontSize: "13px", fontWeight: "600", color: "#166534" }}>
                        Lập luận hoặc công thức bạn áp dụng (không bắt buộc):
                      </label>
                      <textarea
                        rows={2}
                        placeholder="Ví dụ: Áp dụng công thức v² - v₀² = 2as hoặc định luật bảo toàn cơ năng..."
                        value={reasoningInput}
                        onChange={e => setReasoningInput(e.target.value)}
                        disabled={isSubmittingPrediction}
                      />

                      {predictionError && (
                        <p style={{ color: "#dc2626", fontSize: "13px", margin: 0 }}>{predictionError}</p>
                      )}

                      <button
                        type="submit"
                        className="prediction-submit-btn"
                        disabled={isSubmittingPrediction || !predictionInput.trim()}
                      >
                        {isSubmittingPrediction ? "Đang ghi nhận dự đoán…" : "Xác nhận dự đoán & mở khóa"}
                      </button>
                    </form>
                  </div>
                ) : (
                  /* SIMULATION UNLOCKED: Teacher-run Replay & Interactive Player (FR-STU-03) */
                  <div>
                    <div className="simulation-unlocked-banner">
                      <div>
                        <strong>Dự đoán đã được gửi:</strong> "{submittedPredictionText}"
                      </div>
                      <span className="status-pill pass">Đã mở khóa mô phỏng</span>
                    </div>

                    <div style={{ display: "grid", gridTemplateColumns: "1fr 340px", gap: "20px", alignItems: "start" }}>
                      {/* Left: Physics Scene & Playback */}
                      <div className="modern-card" style={{ padding: "16px", margin: 0 }}>
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "12px" }}>
                          <h3 style={{ margin: 0, fontSize: "15px" }}>Không gian Mô phỏng Vật lý</h3>
                          <div style={{ display: "flex", gap: "8px" }}>
                            <button
                              type="button"
                              className="role-switch-pill"
                              style={{ border: "1px solid var(--border-subtle)" }}
                              onClick={() => setVectors(v => ({ ...v, trajectory: !v.trajectory }))}
                            >
                              {vectors.trajectory ? "Quỹ đạo ✓" : "Quỹ đạo"}
                            </button>
                            <button
                              type="button"
                              className="role-switch-pill"
                              style={{ border: "1px solid var(--border-subtle)" }}
                              onClick={() => setVectors(v => ({ ...v, velocity: !v.velocity }))}
                            >
                              {vectors.velocity ? "Vận tốc v⃗ ✓" : "Vận tốc v⃗"}
                            </button>
                            <button
                              type="button"
                              className="role-switch-pill"
                              style={{ border: "1px solid var(--border-subtle)" }}
                              onClick={() => setVectors(v => ({ ...v, acceleration: !v.acceleration }))}
                            >
                              {vectors.acceleration ? "Gia tốc a⃗ ✓" : "Gia tốc a⃗"}
                            </button>
                          </div>
                        </div>

                        {simLoading ? (
                          <div style={{ height: "360px", display: "flex", alignItems: "center", justifyContent: "center", color: "var(--text-muted)" }}>
                            Đang chuẩn bị mô hình vật lý…
                          </div>
                        ) : simError ? (
                          <div style={{ height: "360px", display: "flex", alignItems: "center", justifyContent: "center", color: "#dc2626" }}>
                            {simError}
                          </div>
                        ) : simulation ? (
                          <>
                            <div style={{ height: "380px", background: "#f8fafc", borderRadius: "8px", overflow: "hidden" }}>
                              <PhysicsScene
                                simulation={simulation}
                                index={frame}
                                overlays={vectors}
                              />
                            </div>

                            {/* Playback Control bar */}
                            <div style={{ display: "flex", alignItems: "center", gap: "12px", marginTop: "14px", padding: "10px", background: "#f1f5f9", borderRadius: "8px" }}>
                              <button
                                type="button"
                                className="prediction-submit-btn"
                                style={{ padding: "6px 14px", fontSize: "13px" }}
                                onClick={() => setPlaying(!playing)}
                              >
                                {playing ? "Tạm dừng" : "Chạy tiếp"}
                              </button>
                              <button
                                type="button"
                                className="role-switch-pill"
                                style={{ background: "#ffffff", border: "1px solid var(--border-subtle)" }}
                                onClick={() => { setPlaying(false); setFrame(0); }}
                              >
                                Tua về đầu
                              </button>
                              <input
                                type="range"
                                min={0}
                                max={Math.max(0, simulation.time.length - 1)}
                                value={frame}
                                onChange={e => { setPlaying(false); setFrame(Number(e.target.value)); }}
                                style={{ flex: 1 }}
                              />
                              <span style={{ fontFamily: "monospace", fontWeight: "700", minWidth: "55px" }}>
                                {(simulation.time[frame] ?? 0).toFixed(2)}s
                              </span>
                            </div>
                          </>
                        ) : (
                          <div style={{ padding: "40px", textAlign: "center", color: "var(--text-muted)" }}>
                            Không có dữ liệu mô phỏng.
                          </div>
                        )}
                      </div>

                      {/* Right: Comparative Analysis */}
                      <div className="modern-card" style={{ padding: "18px", margin: 0 }}>
                        <h3 style={{ margin: "0 0 12px 0", fontSize: "15px" }}>Đối chiếu Kết quả</h3>
                        
                        <div style={{ background: "#f8fafc", padding: "12px", borderRadius: "8px", marginBottom: "14px", border: "1px solid var(--border-subtle)" }}>
                          <small style={{ color: "var(--text-muted)", display: "block", marginBottom: "4px" }}>Dự đoán của bạn:</small>
                          <p style={{ margin: 0, fontSize: "13.5px", fontWeight: "600", color: "#166534" }}>
                            "{submittedPredictionText}"
                          </p>
                        </div>

                        {simulation && (
                          <div style={{ background: "#eff6ff", padding: "12px", borderRadius: "8px", border: "1px solid #bfdbfe" }}>
                            <small style={{ color: "#1d4ed8", display: "block", marginBottom: "6px", fontWeight: "700" }}>Thông số mô phỏng thực tế:</small>
                            <div style={{ fontSize: "13px", display: "flex", flexDirection: "column", gap: "4px" }}>
                              {Object.entries(simulation.parameters ?? {}).map(([key, value]) => (
                                <div key={key}><strong>{key}:</strong> {value}</div>
                              ))}
                              <div><strong>Thời điểm t:</strong> {(simulation.time[frame] ?? 0).toFixed(2)} s</div>
                              {simulation.positions && Object.keys(simulation.positions).map(key => (
                                <div key={key}>
                                  <strong>Vị trí ({key}):</strong> {(simulation.positions[key]?.[frame] ?? 0).toFixed(2)} m
                                </div>
                              ))}
                              {simulation.velocities && Object.keys(simulation.velocities).map(key => (
                                <div key={key}>
                                  <strong>Vận tốc ({key}):</strong> {(simulation.velocities[key]?.[frame] ?? 0).toFixed(2)} m/s
                                </div>
                              ))}
                            </div>
                          </div>
                        )}

                        <div style={{ marginTop: "18px" }}>
                          <small style={{ color: "var(--text-muted)", lineHeight: "1.4", display: "block" }}>
                            <strong>Gợi ý học tập:</strong> Di chuyển thanh trượt thời gian để quan sát sự biến thiên của vận tốc và gia tốc so với dự đoán ban đầu của bạn.
                          </small>
                        </div>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}
        </>
      )}

      {/* TAB 2: CLASS & SHARED LIBRARY (FR-STU-04) */}
      {activeTab === "library" && (
        <div className="modern-card">
          <div className="modern-card-header">
            <div>
              <h2>Tài nguyên lớp học</h2>
              <p>Xem và chạy các mô phỏng đã được giáo viên chia sẻ.</p>
            </div>
            <div style={{ display: "flex", gap: "8px" }}>
              {["", "Kinematics", "Dynamics", "Circuits"].map(t => (
                <button
                  key={t}
                  type="button"
                  className={`role-switch-pill ${selectedTopic === t ? "active" : ""}`}
                  style={{ border: "1px solid var(--border-subtle)" }}
                  onClick={() => setSelectedTopic(t)}
                >
                  {t || "Tất cả chủ đề"}
                </button>
              ))}
            </div>
          </div>

          {sharedLoading ? (
            <p style={{ color: "var(--text-muted)", padding: "20px" }}>Đang tải thư viện…</p>
          ) : sharedItems.length === 0 ? (
            <div style={{ padding: "40px", textAlign: "center", color: "var(--text-muted)" }}>
              Chưa có mô hình nào trong danh mục này.
            </div>
          ) : (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: "16px" }}>
              {sharedItems.map(item => (
                <div key={item.id} className="modern-card" style={{ margin: 0, padding: "16px" }}>
                  <span className="status-pill pass" style={{ marginBottom: "8px" }}>{item.topic || "Vật lý"}</span>
                  <h3 style={{ fontSize: "15px", margin: "0 0 8px 0" }}>{item.title}</h3>
                  <small style={{ color: "var(--text-muted)", display: "block", marginBottom: "14px" }}>
                    Đã kiểm chứng: {item.validationStatus}
                  </small>
                  <button
                    type="button"
                    className="prediction-submit-btn"
                    style={{ padding: "6px 12px", fontSize: "12px" }}
                    onClick={() => void handleOpenShared(item)}
                  >
                    Xem tài nguyên
                  </button>
                </div>
              ))}
            </div>
          )}
          {selectedSharedItem && (
            <section className="student-resource-player">
              <div className="student-resource-player-header">
                <div>
                  <span className="status-pill info">Tài nguyên được chia sẻ</span>
                  <h3>{selectedSharedItem.title}</h3>
                </div>
                <button type="button" className="modern-tab-btn" onClick={() => setSelectedSharedItem(null)}>
                  Đóng
                </button>
              </div>
              {sharedSimLoading ? (
                <p className="student-player-state">Đang tải mô hình…</p>
              ) : sharedSimError ? (
                <p className="student-player-state error">{sharedSimError}</p>
              ) : sharedSimulation ? (
                <>
                  <div className="student-resource-scene">
                    <PhysicsScene simulation={sharedSimulation} index={sharedFrame} overlays={vectors} />
                  </div>
                  <div className="student-playback">
                    <button type="button" className="prediction-submit-btn" onClick={() => setSharedPlaying(value => !value)}>
                      {sharedPlaying ? "Tạm dừng" : "Chạy mô phỏng"}
                    </button>
                    <button type="button" className="modern-tab-btn" onClick={() => { setSharedPlaying(false); setSharedFrame(0); }}>
                      Tua về đầu
                    </button>
                    <input
                      type="range"
                      min={0}
                      max={Math.max(0, sharedSimulation.time.length - 1)}
                      value={sharedFrame}
                      onChange={event => { setSharedPlaying(false); setSharedFrame(Number(event.target.value)); }}
                    />
                    <span>{(sharedSimulation.time[sharedFrame] ?? 0).toFixed(2)} s</span>
                  </div>
                </>
              ) : null}
            </section>
          )}
        </div>
      )}
    </div>
    </div>
  );
}
