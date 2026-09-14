import { useEffect, useState, type FormEvent } from "react";
import axios from "axios";
import LearningWorkspace, { LearningHeader } from "../components/LearningWorkspace";
import TeacherLibraryPane from "../components/TeacherLibraryPane";
import { confirmProblem, createLibraryFolder, createProblem, extractProblem, getSimulation, runSimulation, simulationHistory } from "../api/physliveApi";
import { useTeacherLibrary } from "../store/useTeacherLibrary";
import { usePhysliveStore } from "../store/usePhysliveStore";
import { getToken } from "../utils/token";
import type { Ambiguity, LibraryItem, Problem, Simulation } from "../types/physlive";
import "../styles/learning.css";
import "../styles/workspace-live.css";

const EXAMPLES = [
  "Một vật chuyển động thẳng với vận tốc đầu 10 m/s và gia tốc 2 m/s². Hãy mô phỏng trong 8 giây.",
  "Ném một vật với vận tốc đầu 20 m/s, góc ném 45 độ, bỏ qua sức cản không khí.",
  "Hai vật có khối lượng 2 kg và 3 kg chuyển động ngược chiều rồi va chạm đàn hồi.",
];

function apiMessage(error: unknown) {
  if (axios.isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? "Không thể kết nối tới máy chủ PhysLive.";
  }
  return error instanceof Error ? error.message : "Đã xảy ra lỗi không xác định.";
}

function openAmbiguities(problem: Problem | null): Ambiguity[] {
  const specification = problem?.currentSpecification;
  return (specification?.ambiguityCases ?? specification?.ambiguities ?? [])
    .filter(item => !item.status || item.status === "OPEN");
}

export default function Workspace() {
  const token = getToken();
  const user = usePhysliveStore(state => state.user);
  const problem = usePhysliveStore(state => state.problem);
  const simulation = usePhysliveStore(state => state.simulation);
  const setProblem = usePhysliveStore(state => state.setProblem);
  const setSimulation = usePhysliveStore(state => state.setSimulation);
  const [description, setDescription] = useState("");
  const [recent, setRecent] = useState<Simulation[]>([]);
  const [pendingProblem, setPendingProblem] = useState<Problem | null>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(Boolean(token));
  const [historyError, setHistoryError] = useState(false);
  const [historyAttempt, setHistoryAttempt] = useState(0);
  const [stage, setStage] = useState("");
  const [error, setError] = useState("");
  const [ambiguityStep, setAmbiguityStep] = useState(0);
  const [typedQuestion, setTypedQuestion] = useState("");
  const [questionTyping, setQuestionTyping] = useState(false);
  const { folders, setFolders, libraryItems, libraryLoading, libraryError, setLibraryError, retryLibrary } = useTeacherLibrary();
  const [openingLibraryId, setOpeningLibraryId] = useState<string | null>(null);
  const [libraryCollapsed, setLibraryCollapsed] = useState(false);
  const current = simulation;
  const showTeacherLibrary = Boolean(token) && user?.role === "TEACHER";
  const ambiguities = openAmbiguities(pendingProblem);
  const activeAmbiguity = ambiguities[Math.min(ambiguityStep, Math.max(ambiguities.length - 1, 0))];

  useEffect(() => {
    if (!token) { setHistoryLoading(false); return; }
    let active = true;
    setHistoryLoading(true);
    setHistoryError(false);
    void simulationHistory()
      .then(items => { if (active) setRecent(items); })
      .catch(() => { if (active) setHistoryError(true); })
      .finally(() => { if (active) setHistoryLoading(false); });
    return () => { active = false; };
  }, [token, historyAttempt]);

  useEffect(() => {
    setAmbiguityStep(0);
  }, [pendingProblem?.id]);

  useEffect(() => {
    const question = activeAmbiguity?.question ?? "";
    setTypedQuestion("");
    if (!question) { setQuestionTyping(false); return; }
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      setTypedQuestion(question); setQuestionTyping(false); return;
    }
    setQuestionTyping(true);
    let visible = 0;
    const timer = window.setInterval(() => {
      visible = Math.min(question.length, visible + 2);
      setTypedQuestion(question.slice(0, visible));
      if (visible >= question.length) { window.clearInterval(timer); setQuestionTyping(false); }
    }, 24);
    return () => window.clearInterval(timer);
  }, [activeAmbiguity?.code, activeAmbiguity?.question]);

  const startNew = () => {
    setProblem(null);
    setSimulation(null);
    setPendingProblem(null);
    setAnswers({});
    setAmbiguityStep(0);
    setError("");
    setStage("");
  };

  const createWorkspaceFolder = async (name: string) => {
    setLibraryError("");
    try {
      const folder = await createLibraryFolder(name);
      setFolders(currentFolders => [...currentFolders, folder]
        .sort((left, right) => left.name.localeCompare(right.name, "vi")));
      return true;
    } catch {
      setLibraryError("Chưa tạo được thư mục. Tên thư mục có thể đã tồn tại.");
      return false;
    }
  };

  const openWorkspaceLibraryItem = async (item: LibraryItem) => {
    if (openingLibraryId) return;
    setOpeningLibraryId(item.id);
    setLibraryError("");
    try {
      const selected = await getSimulation(item.simulationId);
      setProblem(null);
      setSimulation(selected);
    } catch {
      setLibraryError("Không mở được mô phỏng đã lưu.");
    } finally {
      setOpeningLibraryId(null);
    }
  };

  const finishSimulation = async (resolvedProblem: Problem) => {
    const specification = resolvedProblem.currentSpecification;
    if (!specification?.id || !specification.schemaId) {
      throw new Error("AI chưa trả về schema hợp lệ cho đề bài này.");
    }
    setStage("Đang chạy solver và đối chiếu kết quả…");
    const result = await runSimulation(specification.id, specification.schemaId, {});
    setProblem(resolvedProblem);
    setSimulation(result);
    setRecent(items => [result, ...items.filter(item => item.simulationId !== result.simulationId)]);
    setPendingProblem(null);
    setAnswers({});
    setDescription("");
    setStage("");
  };

  const create = async (event: FormEvent) => {
    event.preventDefault();
    const text = description.trim();
    if (!text || loading) return;
    if (!token) {
      setError("Bạn cần đăng nhập để dùng AI Problem Understanding.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      setStage("Đang lưu đề bài…");
      const created = await createProblem(text);
      setStage("AI đang đọc đề và tạo specification…");
      const extracted = await extractProblem(created.id);
      if (!extracted.currentSpecification?.id || !extracted.currentSpecification.schemaId) {
        throw new Error("AI chưa xác định được mô hình vật lý hợp lệ.");
      }
      setProblem(extracted);
      if (openAmbiguities(extracted).length > 0) {
        setPendingProblem(extracted);
        setAnswers({});
        setAmbiguityStep(0);
        setStage("");
        return;
      }
      await finishSimulation(extracted);
    } catch (requestError) {
      setError(apiMessage(requestError));
      setStage("");
    } finally {
      setLoading(false);
    }
  };

  const confirmAmbiguities = async (event: FormEvent) => {
    event.preventDefault();
    if (!pendingProblem || !activeAmbiguity || loading || questionTyping) return;
    if (!answers[activeAmbiguity.code]?.trim()) {
      setError("Hãy trả lời câu hỏi hiện tại trước khi tiếp tục.");
      return;
    }
    if (ambiguityStep < ambiguities.length - 1) {
      setError("");
      setAmbiguityStep(step => step + 1);
      return;
    }
    setLoading(true);
    setError("");
    setStage("AI đang đọc câu trả lời và cập nhật specification…");
    try {
      const resolved = await confirmProblem(pendingProblem.id, answers);
      const remaining = openAmbiguities(resolved);
      if (remaining.length > 0) {
        setProblem(resolved);
        setPendingProblem(resolved);
        setAnswers({});
        setAmbiguityStep(0);
        setStage("");
        return;
      }
      await finishSimulation(resolved);
    } catch (requestError) {
      setError(apiMessage(requestError));
      setStage("");
    } finally {
      setLoading(false);
    }
  };

  if (current) {
    return <LearningWorkspace key={current.runId || current.simulationId} simulation={current}
      problem={problem} onUpdate={setSimulation} onNewSimulation={startNew} />;
  }

  return <div className="workspace-start learning-app">
    <LearningHeader libraryCollapsed={libraryCollapsed} onToggleLibrary={showTeacherLibrary ? () => setLibraryCollapsed(value => !value) : undefined} />
    <main className="workspace-start-main" data-library-pane={showTeacherLibrary} data-library-collapsed={libraryCollapsed}>
      {showTeacherLibrary && <TeacherLibraryPane folders={folders} items={libraryItems}
        onRetry={retryLibrary}
        currentSimulationId="" loading={libraryLoading} error={libraryError}
        openingId={openingLibraryId} onCreateFolder={createWorkspaceFolder} onOpen={openWorkspaceLibraryItem} onNewSimulation={startNew} />}
      <section className="workspace-start-copy">
        <span className="workspace-eyebrow">AI PROBLEM UNDERSTANDING</span>
        <h1>Từ đề bài đến mô phỏng đã kiểm chứng.</h1>
        <p>AI đọc ngữ nghĩa đề bài, tạo specification và hỏi lại khi dữ kiện chưa rõ. Solver chỉ chạy sau khi giáo viên xác nhận đầy đủ.</p>
        <ol>
          <li><strong>AI hiểu đề</strong><span>Nhận diện mô hình, đại lượng, đơn vị và quan hệ.</span></li>
          <li><strong>Xác nhận</strong><span>Giáo viên trả lời các ambiguity do AI phát hiện.</span></li>
          <li><strong>Đối chiếu</strong><span>Solver và validation kiểm tra trước khi hiển thị.</span></li>
        </ol>
      </section>
      <section className="workspace-create-card">
        {pendingProblem ? <>
          <div className="workspace-card-heading"><div><span>AI CLARIFICATION</span><h2>AI đang làm rõ đề bài cùng bạn</h2></div><span className={`workspace-api-status ${questionTyping ? "typing" : ""}`}>{questionTyping ? "AI đang viết" : `Câu ${ambiguityStep + 1}/${ambiguities.length}`}</span></div>
          <form onSubmit={confirmAmbiguities}>
            <div className="workspace-clarify-progress" aria-label={`Đã đến câu ${ambiguityStep + 1} trên ${ambiguities.length}`}><span style={{ width: `${((ambiguityStep + 1) / ambiguities.length) * 100}%` }} /></div>
            <p className="workspace-confirm-note">AI hỏi từng điểm chưa rõ. Nếu câu trả lời chưa đủ, AI sẽ hỏi tiếp thay vì tự điền giá trị.</p>
            {activeAmbiguity && <fieldset className={`workspace-ambiguity workspace-ambiguity-single ${questionTyping ? "is-typing" : ""}`} key={activeAmbiguity.id ?? activeAmbiguity.code}>
              <legend><span className="workspace-ai-mark">AI</span><span className="workspace-typed-question">{typedQuestion}<i aria-hidden="true" /></span></legend>
              <div className={`workspace-answer-area ${questionTyping ? "is-hidden" : ""}`}>
                {activeAmbiguity.options && activeAmbiguity.options.length > 0 && <div className="workspace-options">{activeAmbiguity.options.map(option => <button className={answers[activeAmbiguity.code] === option ? "selected" : ""} type="button" key={option} onClick={() => setAnswers(currentAnswers => ({ ...currentAnswers, [activeAmbiguity.code]: option }))}>{option}</button>)}</div>}
                <label htmlFor="ambiguity-answer">Câu trả lời của bạn</label>
                <input id="ambiguity-answer" autoFocus value={answers[activeAmbiguity.code] ?? ""} disabled={loading || questionTyping} onChange={event => setAnswers(currentAnswers => ({ ...currentAnswers, [activeAmbiguity.code]: event.target.value }))} placeholder="Nhập giá trị và đơn vị nếu có" />
                <small>{activeAmbiguity.code} · {activeAmbiguity.fieldPath ?? activeAmbiguity.field}</small>
              </div>
            </fieldset>}
            {error && <div className="workspace-error" role="alert"><strong>Chưa thể xác nhận</strong><span>{error}</span></div>}
            {stage && <div className="workspace-progress" role="status"><span />{stage}</div>}
            <div className="workspace-clarify-actions">
              <button className="workspace-back-step" type="button" disabled={loading || ambiguityStep === 0} onClick={() => { setError(""); setAmbiguityStep(step => Math.max(0, step - 1)); }}>Quay lại</button>
              <button className="workspace-submit" type="submit" disabled={loading || questionTyping || !activeAmbiguity || !answers[activeAmbiguity.code]?.trim()}>{ambiguityStep < ambiguities.length - 1 ? "Tiếp tục" : "Gửi cho AI kiểm tra"}</button>
            </div>
          </form>
          <div className="workspace-card-footer"><button type="button" onClick={startNew}>Nhập đề khác</button><span>{ambiguityStep} câu đã trả lời · Chưa chạy simulation</span></div>
        </> : <>
          <div className="workspace-card-heading"><div><span>ĐỀ BÀI MỚI</span><h2>Bạn muốn mô phỏng hiện tượng nào?</h2></div><span className="workspace-api-status">{token ? "AI sẵn sàng" : "Chế độ khách"}</span></div>
          <form onSubmit={create}>
            <label htmlFor="workspace-description">Mô tả đầy đủ dữ kiện và yêu cầu</label>
            <textarea id="workspace-description" rows={7} value={description} disabled={loading} onChange={event => setDescription(event.target.value)} placeholder="Ví dụ: Một ô tô bắt đầu từ trạng thái nghỉ, tăng tốc đều 2 m/s² trong 8 giây. Hãy mô phỏng vị trí và vận tốc." />
            <div className="workspace-examples"><span>Điền nhanh:</span>{EXAMPLES.map((example, index) => <button type="button" disabled={loading} key={example} onClick={() => setDescription(example)}>Ví dụ {index + 1}</button>)}</div>
            {error && <div className="workspace-error" role="alert"><strong>Chưa thể tạo mô phỏng</strong><span>{error}</span></div>}
            {stage && <div className="workspace-progress" role="status"><span />{stage}</div>}
            <button className="workspace-submit" type="submit" disabled={!description.trim() || loading}>{loading ? "Đang xử lý…" : "Đưa đề bài cho AI"}</button>
          </form>
          <div className="workspace-card-footer"><span>{historyLoading ? "Đang tải lịch sử…" : token ? `${recent.length} mô phỏng gần đây` : "Đăng nhập để dùng AI"}</span></div>
          {!historyLoading && historyError && <div className="workspace-error" role="alert"><span>Không tải được các mô phỏng gần đây.</span><button type="button" onClick={() => setHistoryAttempt(value => value + 1)}>Thử lại</button></div>}
          {!historyLoading && recent.length > 0 && <div className="workspace-recent"><h3>Mô phỏng gần đây</h3>{recent.slice(0, 4).map(item => <button type="button" key={item.simulationId} onClick={() => { setProblem(null); setSimulation(item); }}><span>{item.schemaId}</span><strong>{item.time.length} mốc dữ liệu</strong></button>)}</div>}
        </>}
      </section>
    </main>
  </div>;
}
