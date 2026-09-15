import { useEffect, useState, type FormEvent } from "react";
import axios from "axios";
import LearningWorkspace from "../../components/workspace/LearningWorkspace";
import CreateSimulationModal from "../../components/workspace/CreateSimulationModal";
import EmptySimulationFrame from "../../components/workspace/EmptySimulationFrame";
import { confirmProblem, createProblem, extractProblem } from "../../api/problemApi";
import { createLibraryFolder } from "../../api/libraryApi";
import { getSimulation, runSimulation, simulationHistory } from "../../api/simulationApi";
import { useTeacherLibrary } from "../../store/useTeacherLibrary";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import { getToken } from "../../utils/token";
import type { Ambiguity, LibraryItem, Problem, Simulation } from "../../types/physlive";
import "../../styles/learning.css";

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
  /** Create/clarify composer — open by default when no simulation is loaded. */
  const [composerOpen, setComposerOpen] = useState(() => !simulation);
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

  /** Open the create composer on the same simulation frame (do not swap to a separate start page). */
  const openComposer = () => {
    setPendingProblem(null);
    setAnswers({});
    setAmbiguityStep(0);
    setError("");
    setStage("");
    setDescription("");
    setComposerOpen(true);
  };

  const closeComposer = () => {
    if (loading) return;
    setComposerOpen(false);
    setPendingProblem(null);
    setAnswers({});
    setAmbiguityStep(0);
    setError("");
    setStage("");
  };

  const resetComposer = () => {
    setPendingProblem(null);
    setAnswers({});
    setAmbiguityStep(0);
    setError("");
    setStage("");
    setDescription("");
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
      setComposerOpen(false);
      setPendingProblem(null);
    } catch {
      setLibraryError("Không mở được mô phỏng đã lưu.");
    } finally {
      setOpeningLibraryId(null);
    }
  };

  const openRecent = (item: Simulation) => {
    setProblem(null);
    setSimulation(item);
    setComposerOpen(false);
    setPendingProblem(null);
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
    setComposerOpen(false);
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

  const composerProps = {
    token,
    description,
    onDescriptionChange: setDescription,
    pendingProblem,
    answers,
    onAnswersChange: setAnswers,
    loading,
    stage,
    error,
    ambiguityStep,
    typedQuestion,
    questionTyping,
    ambiguities,
    activeAmbiguity,
    recent,
    historyLoading,
    historyError,
    canDismiss: !pendingProblem && !loading,
    onClose: closeComposer,
    onCreate: create,
    onConfirmAmbiguities: confirmAmbiguities,
    onBackAmbiguity: () => { setError(""); setAmbiguityStep(step => Math.max(0, step - 1)); },
    onResetComposer: resetComposer,
    onOpenRecent: openRecent,
    onRetryHistory: () => setHistoryAttempt(value => value + 1),
  };
  const composer = composerOpen ? <CreateSimulationModal {...composerProps} inline /> : null;

  const frame = current ? (
    <LearningWorkspace
      key={current.runId || current.simulationId}
      simulation={current}
      problem={problem}
      onUpdate={setSimulation}
      onNewSimulation={openComposer}
      createPanel={composer}
    />
  ) : (
    <EmptySimulationFrame
      showTeacherLibrary={showTeacherLibrary}
      folders={folders}
      libraryItems={libraryItems}
      libraryLoading={libraryLoading}
      libraryError={libraryError}
      openingLibraryId={openingLibraryId}
      onRetryLibrary={retryLibrary}
      onCreateFolder={createWorkspaceFolder}
      onOpenLibraryItem={openWorkspaceLibraryItem}
      onNewSimulation={openComposer}
      createPanel={composer}
    />
  );

  return frame;
}
