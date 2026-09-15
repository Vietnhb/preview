import { useEffect, useState, type FormEvent } from "react";
import axios from "axios";
import LearningWorkspace from "../../components/workspace/LearningWorkspace";
import CreateSimulationModal from "../../components/workspace/CreateSimulationModal";
import EmptySimulationFrame from "../../components/workspace/EmptySimulationFrame";
import { confirmProblem, createProblem, createProblemFromImage, extractProblem } from "../../api/problemApi";
import mammoth from "mammoth";
import { GlobalWorkerOptions, getDocument } from "pdfjs-dist/legacy/build/pdf.mjs";
import pdfWorker from "pdfjs-dist/legacy/build/pdf.worker.mjs?url";
import { createLibraryFolder } from "../../api/libraryApi";
import { getSimulation, runSimulation, simulationHistory } from "../../api/simulationApi";
import { useTeacherLibrary } from "../../store/useTeacherLibrary";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import { getToken } from "../../utils/token";
import type { Ambiguity, ConversationMessage, LibraryItem, Problem, Simulation } from "../../types/physlive";
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

const MAX_SOURCE_FILE_BYTES = 10 * 1024 * 1024;
const IMAGE_TYPES = new Set(["image/png", "image/jpeg", "image/webp", "image/gif"]);

function fileExtension(file: File) {
  return file.name.toLowerCase().split(".").pop() ?? "";
}

async function readPdfText(file: File) {
  GlobalWorkerOptions.workerSrc = pdfWorker;
  const loadingTask = getDocument({ data: await file.arrayBuffer() });
  const document = await loadingTask.promise;
  try {
    const pages: string[] = [];
    for (let pageNumber = 1; pageNumber <= document.numPages; pageNumber += 1) {
      const page = await document.getPage(pageNumber);
      const content = await page.getTextContent();
      pages.push(content.items.map(item => ("str" in item ? item.str : "")).join(" "));
    }
    return pages.join("\n").trim();
  } finally {
    await loadingTask.destroy();
  }
}

async function readDocumentText(file: File) {
  const extension = fileExtension(file);
  if (extension === "txt" || file.type === "text/plain") return (await file.text()).trim();
  if (extension === "docx" || file.type === "application/vnd.openxmlformats-officedocument.wordprocessingml.document") {
    return (await mammoth.extractRawText({ arrayBuffer: await file.arrayBuffer() })).value.trim();
  }
  if (extension === "pdf" || file.type === "application/pdf") return readPdfText(file);
  throw new Error("Chỉ hỗ trợ ảnh PNG/JPEG/WebP/GIF, PDF, DOCX và TXT.");
}

export default function Workspace() {
  const token = getToken();
  const user = usePhysliveStore(state => state.user);
  const problem = usePhysliveStore(state => state.problem);
  const simulation = usePhysliveStore(state => state.simulation);
  const setProblem = usePhysliveStore(state => state.setProblem);
  const setSimulation = usePhysliveStore(state => state.setSimulation);
  const [description, setDescription] = useState("");
  const [sourceFile, setSourceFile] = useState<File | null>(null);
  const [sourceFileError, setSourceFileError] = useState("");
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
  const [conversation, setConversation] = useState<ConversationMessage[]>([]);
  const { folders, setFolders, libraryItems, libraryLoading, libraryError, setLibraryError, retryLibrary } = useTeacherLibrary();
  const [openingLibraryId, setOpeningLibraryId] = useState<string | null>(null);
  const [composerOrigin, setComposerOrigin] = useState<{ simulation: Simulation; problem: Problem | null } | null>(null);
  /** Create/clarify composer — open by default when no simulation is loaded. */
  const [composerOpen, setComposerOpen] = useState(() => !simulation);
  const current = simulation;
  const showTeacherLibrary = Boolean(token) && user?.role === "TEACHER";
  const ambiguities = openAmbiguities(pendingProblem);
  const activeAmbiguity = ambiguities[Math.min(ambiguityStep, Math.max(ambiguities.length - 1, 0))];

  const appendConversationMessage = (role: ConversationMessage["role"], text: string) => {
    setConversation(messages => [...messages, {
      id: `${role}-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      role,
      text,
    }]);
  };

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

  const handleSourceFileChange = (file: File | null) => {
    setSourceFileError("");
    if (!file) {
      setSourceFile(null);
      return;
    }
    const extension = fileExtension(file);
    const supported = IMAGE_TYPES.has(file.type)
      || ["pdf", "docx", "txt"].includes(extension);
    if (!supported) {
      setSourceFile(null);
      setSourceFileError("Chỉ hỗ trợ ảnh PNG/JPEG/WebP/GIF, PDF, DOCX và TXT.");
      return;
    }
    if (file.size > MAX_SOURCE_FILE_BYTES) {
      setSourceFile(null);
      setSourceFileError("Tệp không được vượt quá 10 MB.");
      return;
    }
    setSourceFile(file);
  };

  /** Open a fresh three-column workspace for creating a simulation. */
  const openComposer = () => {
    setComposerOrigin(current ? { simulation: current, problem } : null);
    setSimulation(null);
    setProblem(null);
    setPendingProblem(null);
    setAnswers({});
    setAmbiguityStep(0);
    setError("");
    setStage("");
    setDescription("");
    setSourceFile(null);
    setSourceFileError("");
    setConversation([]);
    setComposerOpen(true);
  };

  const closeComposer = () => {
    if (loading) return;
    if (composerOrigin) {
      setSimulation(composerOrigin.simulation);
      setProblem(composerOrigin.problem);
    }
    setComposerOrigin(null);
    setComposerOpen(false);
    setPendingProblem(null);
    setAnswers({});
    setAmbiguityStep(0);
    setError("");
    setStage("");
    setSourceFile(null);
    setSourceFileError("");
    setConversation([]);
  };

  const resetComposer = () => {
    setPendingProblem(null);
    setAnswers({});
    setAmbiguityStep(0);
    setError("");
    setStage("");
    setDescription("");
    setSourceFile(null);
    setSourceFileError("");
    setConversation([]);
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
      setComposerOrigin(null);
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
    setComposerOrigin(null);
    setComposerOpen(false);
    setPendingProblem(null);
  };

  const finishSimulation = async (resolvedProblem: Problem) => {
    const specification = resolvedProblem.currentSpecification;
    if (!specification?.id || !specification.schemaId) {
      throw new Error("AI chưa trả về schema hợp lệ cho đề bài này.");
    }
    setStage("Đang chạy solver và đối chiếu kết quả…");
    appendConversationMessage("assistant", "Các dữ kiện đã đủ. Mình bắt đầu chạy mô phỏng để kiểm tra kết quả.");
    const result = await runSimulation(specification.id, specification.schemaId, {});
    setProblem(resolvedProblem);
    setSimulation(result);
    setRecent(items => [result, ...items.filter(item => item.simulationId !== result.simulationId)]);
    setComposerOrigin(null);
    setPendingProblem(null);
    setAnswers({});
    setDescription("");
    setSourceFile(null);
    setSourceFileError("");
    setStage("");
    setComposerOpen(false);
  };

  const create = async (event: FormEvent) => {
    event.preventDefault();
    const text = description.trim();
    if ((!text && !sourceFile) || loading) return;
    if (!token) {
      setError("Bạn cần đăng nhập để dùng AI Problem Understanding.");
      return;
    }
    setLoading(true);
    setError("");
    const promptMessage = [
      text,
      sourceFile ? `Tệp đính kèm: ${sourceFile.name}` : "",
    ].filter(Boolean).join("\n");
    const messageId = Date.now();
    setConversation([
      { id: `user-${messageId}`, role: "user", text: promptMessage },
      {
        id: `assistant-${messageId}`,
        role: "assistant",
        text: sourceFile
          ? "Mình đã nhận đề bài và tệp đính kèm. Mình sẽ đọc nội dung rồi dựng mô hình vật lý."
          : "Mình đã nhận đề bài. Mình sẽ đọc dữ kiện rồi dựng mô hình vật lý.",
      },
    ]);
    try {
      setStage("Đang lưu đề bài…");
      if (sourceFile) setStage("Đang đọc tệp nguồn…");
      const created = sourceFile && IMAGE_TYPES.has(sourceFile.type)
        ? await createProblemFromImage(sourceFile, text || undefined)
        : await createProblem(sourceFile ? `${text}\n\n${await readDocumentText(sourceFile)}`.trim() : text);
      setStage("AI đang đọc đề và tạo specification…");
      const extracted = await extractProblem(created.id);
      if (!extracted.currentSpecification?.id || !extracted.currentSpecification.schemaId) {
        throw new Error("AI chưa xác định được mô hình vật lý hợp lệ.");
      }
      setProblem(extracted);
      const missingAmbiguities = openAmbiguities(extracted);
      if (missingAmbiguities.length > 0) {
        appendConversationMessage(
          "assistant",
          `Mình đã đọc xong đề bài nhưng còn ${missingAmbiguities.length} dữ kiện cần bạn xác nhận. Mình sẽ hỏi từng ý một.`,
        );
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
    const answer = answers[activeAmbiguity.code].trim();
    const submittedAnswers = { ...answers, [activeAmbiguity.code]: answer };
    appendConversationMessage("assistant", activeAmbiguity.question);
    appendConversationMessage("user", answer);
    setAnswers({ ...answers, [activeAmbiguity.code]: "" });
    if (ambiguityStep < ambiguities.length - 1) {
      setError("");
      setAmbiguityStep(step => step + 1);
      return;
    }
    setLoading(true);
    setError("");
    appendConversationMessage("assistant", "Mình đã nhận câu trả lời. Đang cập nhật mô hình và kiểm tra xem còn thiếu dữ kiện nào không.");
    setStage("AI đang đọc câu trả lời và cập nhật specification…");
    try {
      const resolved = await confirmProblem(pendingProblem.id, submittedAnswers);
      const remaining = openAmbiguities(resolved);
      if (remaining.length > 0) {
        appendConversationMessage(
          "assistant",
          `Mình đã cập nhật mô hình, nhưng vẫn còn ${remaining.length} dữ kiện cần làm rõ.`,
        );
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
    sourceFile,
    sourceFileError,
    onSourceFileChange: handleSourceFileChange,
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
    conversation,
    canDismiss: !pendingProblem && !loading,
    onClose: closeComposer,
    onCreate: create,
    onConfirmAmbiguities: confirmAmbiguities,
    onBackAmbiguity: () => { setError(""); setAmbiguityStep(step => Math.max(0, step - 1)); },
    onResetComposer: resetComposer,
  };
  const composer = composerOpen ? <CreateSimulationModal {...composerProps} inline /> : null;

  const frame = current ? (
    <LearningWorkspace
      key={current.runId || current.simulationId}
      simulation={current}
      problem={problem}
      onUpdate={setSimulation}
      onNewSimulation={openComposer}
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
      recent={recent}
      historyLoading={historyLoading}
      historyError={historyError}
      onRetryHistory={() => setHistoryAttempt(value => value + 1)}
      onOpenRecent={openRecent}
      onExampleSelect={setDescription}
    />
  );

  return frame;
}
