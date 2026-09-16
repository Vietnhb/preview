import { useEffect, useRef, useState, type FormEvent } from "react";
import axios from "axios";
import CreateSimulationModal from "../../components/workspace/CreateSimulationModal";
import EmptySimulationFrame from "../../components/workspace/EmptySimulationFrame";
import LearningWorkspace from "../../components/workspace/LearningWorkspace";
import { confirmProblem, createProblem, createProblemFromImage, extractProblem, updateProblemText, updateSpecification } from "../../api/problemApi";
import { createLibraryFolder } from "../../api/libraryApi";
import { getSimulation, recentSimulationHistory, runSimulation } from "../../api/simulationApi";
import { useTeacherLibrary } from "../../store/useTeacherLibrary";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import { getToken } from "../../utils/token";
import type { Ambiguity, ConversationMessage, LibraryItem, Problem, Simulation, SimulationSummary, Specification } from "../../types/physlive";
import "../../styles/learning.css";

const simulationCache = new Map<string, Simulation>();

function rememberSimulation(value: Simulation) {
  simulationCache.set(value.simulationId, value);
  if (simulationCache.size > 12) {
    const oldestId = simulationCache.keys().next().value;
    if (oldestId) simulationCache.delete(oldestId);
  }
}

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
  const [pdfjs, worker] = await Promise.all([
    import("pdfjs-dist/legacy/build/pdf.mjs"),
    import("pdfjs-dist/legacy/build/pdf.worker.mjs?url"),
  ]);
  pdfjs.GlobalWorkerOptions.workerSrc = worker.default;
  const loadingTask = pdfjs.getDocument({ data: await file.arrayBuffer() });
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
    const { default: mammoth } = await import("mammoth");
    return (await mammoth.extractRawText({ arrayBuffer: await file.arrayBuffer() })).value.trim();
  }
  if (extension === "pdf" || file.type === "application/pdf") return readPdfText(file);
  throw new Error("Chỉ hỗ trợ ảnh PNG/JPEG/WebP/GIF, PDF, DOCX và TXT.");
}

async function createTextProblem(file: File | null, text: string) {
  if (!file) return createProblem(text);
  const documentText = await readDocumentText(file);
  return createProblem(`${text}\n\n${documentText}`.trim());
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
  const [recent, setRecent] = useState<SimulationSummary[]>([]);
  const [pendingProblem, setPendingProblem] = useState<Problem | null>(null);
  const [specificationReview, setSpecificationReview] = useState<Specification | null>(null);
  const [ocrReviewRequired, setOcrReviewRequired] = useState(false);
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
  const [openingRecentId, setOpeningRecentId] = useState<string | null>(null);
  const [selectedSimulationId, setSelectedSimulationId] = useState<string | null>(() => simulation?.simulationId ?? null);
  const [simulationLoadingId, setSimulationLoadingId] = useState<string | null>(null);
  const selectionSourceRef = useRef<"library" | "recent">("library");
  const [composerOrigin, setComposerOrigin] = useState<{ simulation: Simulation; problem: Problem | null } | null>(null);
  /** Create/clarify composer — open by default when no simulation is loaded. */
  const [composerOpen, setComposerOpen] = useState(() => !simulation);
  const current = simulation;
  const showTeacherLibrary = Boolean(token) && user?.role === "TEACHER";
  const ambiguities = openAmbiguities(pendingProblem);
  const activeAmbiguity = ambiguities[Math.min(ambiguityStep, Math.max(ambiguities.length - 1, 0))];

  useEffect(() => {
    if (simulation) rememberSimulation(simulation);
  }, [simulation]);

  useEffect(() => {
    if (!selectedSimulationId) return;
    const requestId = selectedSimulationId;
    const source = selectionSourceRef.current;
    let active = true;
    const cached = simulationCache.get(requestId);
    setSimulationLoadingId(requestId);

    if (cached) {
      if (usePhysliveStore.getState().simulation?.simulationId !== requestId) setProblem(null);
      setSimulation(cached);
      setComposerOrigin(null);
      setComposerOpen(false);
      setPendingProblem(null);
      setSpecificationReview(null);
      setSimulationLoadingId(null);
      setOpeningLibraryId(null);
      setOpeningRecentId(null);
      return () => { active = false; };
    }

    void getSimulation(requestId)
      .then(loaded => {
        if (!active) return;
        rememberSimulation(loaded);
        setProblem(null);
        setSimulation(loaded);
        setComposerOrigin(null);
        setComposerOpen(false);
        setPendingProblem(null);
        setSpecificationReview(null);
      })
      .catch(() => {
        if (!active) return;
        if (source === "library") setLibraryError("Không mở được mô phỏng đã lưu.");
        else setHistoryError(true);
      })
      .finally(() => {
        if (!active) return;
        setSimulationLoadingId(null);
        setOpeningLibraryId(null);
        setOpeningRecentId(null);
      });

    return () => { active = false; };
  // Loading is intentionally keyed only by the selected simulation ID.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedSimulationId]);

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
    void recentSimulationHistory()
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
    if (globalThis.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      setTypedQuestion(question); setQuestionTyping(false); return;
    }
    setQuestionTyping(true);
    let visible = 0;
    let lastTick = 0;
    let animationFrame: number | null = null;
    const tick = (now: number) => {
      if (now - lastTick < 24) {
        animationFrame = requestAnimationFrame(tick);
        return;
      }
      lastTick = now;
      visible = Math.min(question.length, visible + 2);
      setTypedQuestion(question.slice(0, visible));
      if (visible >= question.length) { setQuestionTyping(false); animationFrame = null; }
      else animationFrame = requestAnimationFrame(tick);
    };
    animationFrame = requestAnimationFrame(tick);
    return () => { if (animationFrame !== null) cancelAnimationFrame(animationFrame); };
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
    setSimulationLoadingId(null);
    setOpeningLibraryId(null);
    setOpeningRecentId(null);
    setComposerOrigin(current ? { simulation: current, problem } : null);
    setSelectedSimulationId(null);
    setSimulation(null);
    setProblem(null);
    setPendingProblem(null);
    setSpecificationReview(null);
    setOcrReviewRequired(false);
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
      setSelectedSimulationId(composerOrigin.simulation.simulationId);
      setSimulation(composerOrigin.simulation);
      setProblem(composerOrigin.problem);
    } else {
      setSelectedSimulationId(null);
    }
    setComposerOrigin(null);
    setComposerOpen(false);
    setPendingProblem(null);
    setSpecificationReview(null);
    setOcrReviewRequired(false);
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
    setSpecificationReview(null);
    setOcrReviewRequired(false);
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

  const selectSimulation = (simulationId: string, source: "library" | "recent"): boolean => {
    if (simulationLoadingId || selectedSimulationId === simulationId) return false;
    selectionSourceRef.current = source;
    setSelectedSimulationId(simulationId);
    return true;
  };

  const openWorkspaceLibraryItem = async (item: LibraryItem) => {
    if (simulationLoadingId) return;
    setLibraryError("");
    setHistoryError(false);
    if (selectSimulation(item.simulationId, "library")) setOpeningLibraryId(item.id);
  };

  const openRecent = async (item: SimulationSummary) => {
    if (simulationLoadingId) return;
    setHistoryError(false);
    setLibraryError("");
    if (selectSimulation(item.simulationId, "recent")) setOpeningRecentId(item.simulationId);
  };

  const finishSimulation = async (resolvedProblem: Problem) => {
    const specification = resolvedProblem.currentSpecification;
    if (!specification?.id || !specification.schemaId) {
      throw new Error("AI chưa trả về schema hợp lệ cho đề bài này.");
    }
    setStage("Đang chạy solver và đối chiếu kết quả…");
    appendConversationMessage("assistant", "Các dữ kiện đã đủ. Mình bắt đầu chạy mô phỏng để kiểm tra kết quả.");
    const result = await runSimulation(specification.id, specification.schemaId, {});
    rememberSimulation(result);
    setSelectedSimulationId(result.simulationId);
    setProblem(resolvedProblem);
    setSimulation(result);
    const recentResult: SimulationSummary = {
      simulationId: result.simulationId,
      specificationId: result.specificationId,
      schemaId: result.schemaId,
      status: result.valid ? "READY" : "BLOCKED",
      createdAt: new Date().toISOString(),
    };
    setRecent(items => [recentResult, ...items.filter(item => item.simulationId !== result.simulationId)]);
    setComposerOrigin(null);
    setPendingProblem(null);
    setSpecificationReview(null);
    setAnswers({});
    setDescription("");
    setSourceFile(null);
    setSourceFileError("");
    setStage("");
    setComposerOpen(false);
  };

  const handleExtractedProblem = async (extracted: Problem) => {
    if (!extracted.currentSpecification?.id || !extracted.currentSpecification.schemaId) {
      throw new Error("AI chưa trả về schema hợp lệ cho đề bài này.");
    }
    setProblem(extracted);
    setSpecificationReview(extracted.currentSpecification);
    appendConversationMessage("assistant", "Mình đã tạo specification từ đề bài. Bạn kiểm tra và chỉnh các trường bên dưới trước khi xác nhận.");
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
    }
  };

  const saveSpecification = async (draft: Pick<Specification, "objects" | "quantities" | "relations">) => {
    if (!problem?.id || loading) return;
    setLoading(true);
    setError("");
    setStage("Đang lưu specification đã chỉnh…");
    try {
      const updated = await updateSpecification(problem.id, draft);
      setProblem(updated);
      setSpecificationReview(updated.currentSpecification ?? null);
      const remaining = openAmbiguities(updated);
      setPendingProblem(remaining.length > 0 ? updated : null);
      setAnswers({});
      setAmbiguityStep(0);
      appendConversationMessage("assistant", remaining.length > 0
        ? "Mình đã lưu thay đổi. Vẫn còn dữ kiện cần bạn xác nhận trong khung chat."
        : "Mình đã lưu specification. Hãy xác nhận để chạy mô phỏng.");
    } catch (requestError) {
      setError(apiMessage(requestError));
    } finally {
      setStage("");
      setLoading(false);
    }
  };

  const confirmSpecification = async (draft: Pick<Specification, "objects" | "quantities" | "relations">) => {
    if (!problem?.id || loading) return;
    setLoading(true);
    setError("");
    setStage("Đang lưu specification và kiểm tra dữ kiện…");
    try {
      const updated = await updateSpecification(problem.id, draft);
      const remaining = openAmbiguities(updated);
      setProblem(updated);
      setSpecificationReview(updated.currentSpecification ?? null);
      if (remaining.length > 0) {
        setPendingProblem(updated);
        setAnswers({});
        setAmbiguityStep(0);
        appendConversationMessage("assistant", `Specification vẫn còn ${remaining.length} dữ kiện cần xác nhận.`);
        return;
      }
      await finishSimulation(updated);
    } catch (requestError) {
      setError(apiMessage(requestError));
    } finally {
      setStage("");
      setLoading(false);
    }
  };

  const confirmOcrReview = async (event: FormEvent) => {
    event.preventDefault();
    const text = description.trim();
    if (!problem?.id || !text || loading) return;
    setLoading(true);
    setError("");
    appendConversationMessage("user", text);
    appendConversationMessage("assistant", "Mình đã nhận phần nội dung bạn chỉnh. Mình sẽ dùng bản này để tạo specification.");
    setStage("Đang lưu nội dung đã rà soát…");
    try {
      const updated = await updateProblemText(problem.id, text);
      setProblem(updated);
      setOcrReviewRequired(false);
      setStage("AI đang đọc đề và tạo specification…");
      await handleExtractedProblem(await extractProblem(updated.id));
    } catch (requestError) {
      setError(apiMessage(requestError));
      setStage("");
    } finally {
      setLoading(false);
    }
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
        : await createTextProblem(sourceFile, text);
      if (sourceFile && IMAGE_TYPES.has(sourceFile.type)) {
        setProblem(created);
        setDescription(created.editableText ?? text);
        setOcrReviewRequired(true);
        setStage("");
        appendConversationMessage(
          "assistant",
          created.editableText
            ? "Mình đã đọc nội dung trong ảnh. Hãy kiểm tra, sửa nếu cần rồi gửi lại để tiếp tục."
            : "Mình chưa đọc được chữ trong ảnh. Hãy nhập hoặc sửa nội dung đề bài rồi gửi lại để tiếp tục.",
        );
        return;
      }
      setStage("AI đang đọc đề và tạo specification…");
      await handleExtractedProblem(await extractProblem(created.id));
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
    ocrReviewRequired,
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
    onConfirmOcrReview: confirmOcrReview,
    specificationReview,
    onSaveSpecification: saveSpecification,
    onConfirmSpecification: confirmSpecification,
    onConfirmAmbiguities: confirmAmbiguities,
    onBackAmbiguity: () => { setError(""); setAmbiguityStep(step => Math.max(0, step - 1)); },
    onResetComposer: resetComposer,
  };
  const composer = composerOpen ? <CreateSimulationModal {...composerProps} inline /> : null;

  const frame = current ? (
      <LearningWorkspace
        simulation={current}
        problem={problem}
        onUpdate={setSimulation}
        onNewSimulation={openComposer}
        onSelectSimulation={simulationId => selectSimulation(simulationId, "library")}
        simulationLoading={Boolean(simulationLoadingId)}
        loadingSimulationId={simulationLoadingId}
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
      openingRecentId={openingRecentId}
      onExampleSelect={setDescription}
    />
  );

  return frame;
}
