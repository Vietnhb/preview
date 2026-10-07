import { useCallback, useEffect, useState, type ClipboardEvent, type DragEvent, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";
import axios from "axios";
import { confirmSimulationExplanation, openGeneratedSimulation, updateSavedSimulationVisual, confirmSimulationInput, recognizeSimulationImage, understandSimulationText, recomputeSimulation, reviseSimulationIntent, type IntentResult, type SimulationParameter, type GeneratedSimulationResult, type SimulationSourceMode, type SimulationValidation, type RecognitionResult } from "../api/simulationUnderstandingApi";
import type { SolverTimeline } from "../model/svgScene";
import { createLibraryFolder } from "../../library/api/libraryApi";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import { canTeach } from "../../../shared/auth/permissions";
import type { LibraryFolder, LibraryItem } from "../../../shared/types/physlive";

type LibraryWorkspaceState = {
  folders: LibraryFolder[];
  libraryItems: LibraryItem[];
  libraryLoading: boolean;
  libraryError: string;
  retryLibrary: () => void;
  setFolders: (change: (folders: LibraryFolder[]) => LibraryFolder[]) => void;
  setLibraryItems: (change: (items: LibraryItem[]) => LibraryItem[]) => void;
};

const MAX_IMAGE_BYTES = 8 * 1024 * 1024;
const IMAGE_MIME_TYPES = new Set(["image/png", "image/jpeg", "image/webp"]);

function getError(error: unknown) {
  if (axios.isAxiosError<{ message?: string }>(error))
    return error.response?.data?.message || "Máy chủ không thể hoàn tất bước này. Vui lòng thử lại.";
  return error instanceof Error ? error.message : "Đã xảy ra lỗi không mong muốn. Vui lòng thử lại.";
}

export function parameterBounds(parameter: SimulationParameter): [number, number] {
  const center = parameter.value;
  const span = Math.max(1, Math.abs(center) * 2);
  const min = Number.isFinite(parameter.min) ? parameter.min! : center - span;
  const max = Number.isFinite(parameter.max) ? parameter.max! : center + span;
  return min <= max ? [min, max] : [center, center];
}

/** What the AI produced for the visual: custom code, otherwise its declarative SVG scene. */
export function visualSource(simulation: GeneratedSimulationResult) {
  const program = simulation.simulationSpec.visualProgram;
  if (simulation.code?.trim()) return simulation.code;
  return program?.scene ? JSON.stringify(program.scene, null, 1) : "";
}

/** Owns the simulation workflow and async cleanup, independently of panel markup. */
export function useSimulationWorkspace(libraryState: LibraryWorkspaceState) {
  const [searchParams] = useSearchParams();
  const requestedSimulationId = searchParams.get("simulationId");
  const user = useSessionStore((state) => state.user);
  const canManageLearningContent = canTeach(user) || !user;


  const { folders, setFolders, setLibraryItems, libraryItems, libraryLoading, libraryError, retryLibrary } = libraryState;
  const [saveOpen, setSaveOpen] = useState(false);
  const [savedMessage, setSavedMessage] = useState("");
  const [currentSimulationId, setCurrentSimulationId] = useState("");
  const [openingId, setOpeningId] = useState<string | null>(requestedSimulationId);

  const [sourceMode, setSourceMode] = useState<SimulationSourceMode>("TEXT");
  const [text, setText] = useState("");
  const [sourceFile, setSourceFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [busy, setBusy] = useState(Boolean(requestedSimulationId));
  const [error, setError] = useState<string | null>(null);
  const [recognition, setRecognition] = useState<RecognitionResult | null>(null);
  const [correction, setCorrection] = useState("");
  const [editingRecognition, setEditingRecognition] = useState(false);
  const [manualCorrectionDone, setManualCorrectionDone] = useState(false);
  const [intent, setIntent] = useState<IntentResult | null>(null);
  const [revision, setRevision] = useState("");
  const [simulation, setSimulation] = useState<GeneratedSimulationResult | null>(null);
  const [liveTimeline, setLiveTimeline] = useState<SolverTimeline | null>(null);
  const [renderError, setRenderError] = useState("");
  /** One automatic AI repair per user-requested design; later repairs stay manual. */
  const [autoRepairUsed, setAutoRepairUsed] = useState(false);
  const [values, setValues] = useState<Record<string, number>>({});
  const [runValues, setRunValues] = useState<Record<string, number>>({});
  const [sandboxKey, setSandboxKey] = useState(0);
  const [validation, setValidation] = useState<SimulationValidation | null>(null);
  const [locallyAdjusted, setLocallyAdjusted] = useState(false);
  /** True from a parameter change until the recomputed motion arrives. */
  const [recomputing, setRecomputing] = useState(false);

  const restoreSaved = useCallback((result: GeneratedSimulationResult, id: string) => {
    const params = result.savedParameters ?? Object.fromEntries(result.parameters.map(p => [p.name, p.value]));
    setSimulation(result);
    setIntent({ ...result, stage: "EXPLAIN" });
    setRecognition(null);
    setText(result.description);
    setLiveTimeline(result.simulationSpec.solverTimeline ?? null);
    setValues(params); setRunValues(params);
    setValidation(result.validation);
    setLocallyAdjusted(false); setRecomputing(false);
    setRenderError(""); setAutoRepairUsed(true);
    setSaveOpen(false); setSavedMessage("");
    setCurrentSimulationId(id);
    setSandboxKey(key => key + 1);
  }, []);

  useEffect(() => {
    if (!requestedSimulationId) return;
    let cancelled = false;
    openGeneratedSimulation(requestedSimulationId).then(result => {
      if (!cancelled) restoreSaved(result, requestedSimulationId);
    }).catch(cause => { if (!cancelled) setError(getError(cause)); })
      .finally(() => { if (!cancelled) { setBusy(false); setOpeningId(null); } });
    return () => { cancelled = true; };
  }, [requestedSimulationId, restoreSaved]);

  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);

  const acceptImage = (file: File) => {
    if (!IMAGE_MIME_TYPES.has(file.type)) {
      setError("Vui lòng chọn ảnh định dạng PNG, JPEG hoặc WebP.");
      return;
    }
    if (file.size > MAX_IMAGE_BYTES) {
      setError("Dung lượng ảnh phải nhỏ hơn 8 MB.");
      return;
    }
    setError(null);
    setSourceFile(file);
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setPreviewUrl(URL.createObjectURL(file));
    setSourceMode("IMAGE");
  };

  const onPaste = (event: ClipboardEvent) => {
    const file = Array.from(event.clipboardData?.files ?? []).find((item) =>
      IMAGE_MIME_TYPES.has(item.type),
    );
    if (file) {
      event.preventDefault();
      acceptImage(file);
    }
  };

  const onDrop = (event: DragEvent) => {
    const file = Array.from(event.dataTransfer?.files ?? []).find((item) =>
      IMAGE_MIME_TYPES.has(item.type),
    );
    if (file) {
      event.preventDefault();
      acceptImage(file);
    }
  };

  const reset = () => {
    if (busy || openingId) return;
    setSaveOpen(false);
    setSavedMessage("");
    setCurrentSimulationId("");
    setRecognition(null);
    setCorrection("");
    setEditingRecognition(false);
    setManualCorrectionDone(false);
    setIntent(null);
    setRevision("");
    setSimulation(null);
    setLiveTimeline(null);
    setValues({});
    setRunValues({});
    setValidation(null);
    setError(null);
    setBusy(false);
    setLocallyAdjusted(false); setRecomputing(false);
    setSourceFile(null);
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
      setPreviewUrl(null);
    }
  };

  const createFolder = async (folderName: string) => {
    try {
      const folder = await createLibraryFolder(folderName);
      setFolders((current) => [...current, folder].sort((a, b) => a.name.localeCompare(b.name, "vi")));
      return true;
    } catch {
      return false;
    }
  };

  const openLibraryItem = async (item: LibraryItem) => {
    if (busy || openingId || saveOpen) return;
    setOpeningId(item.id);
    setBusy(true);
    setError(null);
    try {
      const result = await openGeneratedSimulation(item.simulationId);
      restoreSaved(result, item.simulationId);
    } catch (cause) { setError(getError(cause)); }
    finally { setBusy(false); setOpeningId(null); }
  };

  const normalize = async (event: FormEvent) => {
    event.preventDefault();
    if (busy) return;
    setError("");
    setBusy(true);
    try {
      if (sourceMode === "IMAGE" && sourceFile) {
        const result = await recognizeSimulationImage(sourceFile, text || undefined);
        setRecognition(result);
        setCorrection(result.recognizedText || "");
        setEditingRecognition(false);
        setManualCorrectionDone(false);
      } else {
        const result = await understandSimulationText(text);
        setRecognition(null);
        setIntent(result);
        setRevision("");
      }
    } catch (cause) {
      setError(getError(cause));
    } finally {
      setBusy(false);
    }
  };

  const handleRecognition = async (acceptCurrent: boolean) => {
    if (!recognition || busy) return;
    setBusy(true);
    setError("");
    try {
      const result = await confirmSimulationInput(
        recognition.sessionId,
        recognition.recognizedText,
        acceptCurrent ? undefined : correction,
      );
      if (result.stage === "RECOGNITION" || result.stage === "RECOGNITION_FAILED") {
        setRecognition(result as RecognitionResult);
        setCorrection(result.recognizedText || "");
        if (!acceptCurrent) {
          setEditingRecognition(false);
          setManualCorrectionDone(true);
        }
      } else {
        setRecognition(null);
        setIntent(result as IntentResult);
        setRevision("");
      }
    } catch (cause) {
      setError(getError(cause));
    } finally {
      setBusy(false);
    }
  };

  const handleRevision = async (event: FormEvent) => {
    event.preventDefault();
    if (!intent || !revision.trim() || busy) return;
    setBusy(true);
    setError("");
    try {
      const result = await reviseSimulationIntent(intent, revision.trim());
      setIntent(result);
      setSimulation(null);
      setValidation(result.validation ?? null);
      setRevision("");
    } catch (cause) {
      setError(getError(cause));
    } finally {
      setBusy(false);
    }
  };

  const generate = async (renderDiagnostics?: { code: string; message: string }) => {
    if (!intent || intent.stage !== "EXPLAIN" || busy) return;
    setBusy(true);
    setError("");
    if (!renderDiagnostics) {
      setAutoRepairUsed(false);
      setSaveOpen(false);
      setSavedMessage("");
      setCurrentSimulationId("");
    }
    try {
      const result = await confirmSimulationExplanation(intent, renderDiagnostics);
      const parameters = result.parameters ?? [];
      if (new Set(parameters.map((item) => item.name)).size !== parameters.length)
        throw new Error("Mô phỏng được tạo có tên tham số bị trùng.");
      if (
        parameters.some(
          (item) =>
            (item.min !== undefined && (!Number.isFinite(item.min) || item.value < item.min)) ||
            (item.max !== undefined && (!Number.isFinite(item.max) || item.value > item.max)) ||
            (item.min !== undefined && item.max !== undefined && item.min > item.max),
        )
      )
        throw new Error("Giới hạn tham số của mô phỏng được tạo không nhất quán.");
      const paramValues = Object.fromEntries(
        parameters.map((item) => [item.name, item.value]),
      );
      if (
        Object.values(paramValues).some(
          (value) => !Number.isFinite(value),
        )
      )
        throw new Error("Mô phỏng được tạo có tham số vượt quá giới hạn chạy trên thiết bị.");
      if (!result.simulationSpec.solverTimeline?.frames?.length)
        throw new Error("Mô phỏng được tạo thiếu dữ liệu diễn tiến từ máy chủ.");
      if (renderDiagnostics && currentSimulationId) {
        try {
          await updateSavedSimulationVisual(currentSimulationId, result);
        } catch (cause) {
          throw new Error(`Chưa lưu được thiết kế mới; bài đã lưu vẫn giữ cảnh trước đó. ${getError(cause)}`);
        }
        setSavedMessage("Đã cập nhật hình ảnh trong thư viện.");
      }
      setValues(previous => renderDiagnostics ? previous : paramValues);
      setRunValues(previous => renderDiagnostics ? previous : paramValues);
      setValidation(previous => renderDiagnostics ? previous : result.validation);
      setSimulation(result);
      setRenderError("");
      setLiveTimeline(previous => renderDiagnostics ? previous : result.simulationSpec.solverTimeline!);
      setLocallyAdjusted(Boolean(renderDiagnostics));
      setSandboxKey((key) => key + 1);
    } catch (cause) {
      setError(getError(cause));
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    if (!renderError || !simulation || busy || autoRepairUsed) return;
    setAutoRepairUsed(true);
    void generate({ code: visualSource(simulation), message: renderError });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- generate is recreated each render
  }, [renderError, simulation, busy, autoRepairUsed]);

  useEffect(() => {
    if (!simulation || !locallyAdjusted) return;
    const controller = new AbortController();
    const timeout = window.setTimeout(() => {
      void recomputeSimulation(simulation, values, controller.signal).then(result => {
        if (controller.signal.aborted) return;
        setLiveTimeline(result.solverTimeline);
        setRunValues(values);
        setValidation(result.validation);
        setError(null);
        // New parameters mean a new motion: replay it from t = 0 instead of continuing mid-way.
        setSandboxKey(key => key + 1);
        setRecomputing(false);
      }).catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        setError(getError(cause));
        setValidation({ status: "FLAGGED", flags: [getError(cause)] });
        setRecomputing(false);
      });
    }, 180);
    return () => { window.clearTimeout(timeout); controller.abort(); };
  }, [values, simulation, locallyAdjusted]);

  const updateParameter = (parameter: SimulationParameter, numeric: number) => {
    const [min, max] = parameterBounds(parameter);
    if (!Number.isFinite(numeric)) return;
    setValues((current) => ({
      ...current,
      [parameter.name]: Math.min(max, Math.max(min, numeric)),
    }));
    setLocallyAdjusted(true);
    setRecomputing(true);
    setValidation({
      status: "PENDING",
      flags: [],
    });
  };

  const resetParameters = () => {
    if (!simulation?.parameters) return;
    const initial = Object.fromEntries(
      simulation.parameters.map((p) => [p.name, p.value]),
    );
    setValues(initial);
    setLocallyAdjusted(true);
    setRecomputing(true);
    setValidation({ status: "PENDING", flags: [] });
  };

  const recognitionLowConfidence =
    recognition?.stage === "RECOGNITION_FAILED" ||
    (!manualCorrectionDone &&
      typeof recognition?.confidence === "number" &&
      recognition.confidence < 0.6);


  const restartPreview = () => {
    setRunValues(values);
    setSandboxKey(key => key + 1);
  };
  const onFolder = (folder: LibraryFolder) => setFolders(current => [...current, folder]);
  const onSaved = (item: LibraryItem) => {
    setLibraryItems(current => [item, ...current.filter(value => value.id !== item.id)]);
    setFolders(current => current.map(folder => folder.id === item.folderId
      ? { ...folder, itemCount: folder.itemCount + 1 } : folder));
    setCurrentSimulationId(item.simulationId);
    setSavedMessage(`Đã lưu: ${item.title}`);
    setSaveOpen(false);
  };
  const applyExample = (description: string) => { setText(description); setSourceMode("TEXT"); };

  return {
    canManageLearningContent,
    events: { onPaste, onDrop },
    library: {
      folders, items: libraryItems, loading: libraryLoading, error: libraryError, currentSimulationId, openingId,
      onRetry: retryLibrary, onCreateFolder: createFolder, onOpen: openLibraryItem, onNewSimulation: reset,
    },
    input: {
      sourceMode, setSourceMode, text, setText, sourceFile, previewUrl, busy, error, acceptImage,
      recognition, correction, setCorrection, editingRecognition, setEditingRecognition, recognitionLowConfidence,
      intent, revision, setRevision, normalize, handleRecognition, handleRevision, generate, reset,
    },
    preview: { simulation, liveTimeline, runValues, sandboxKey, validation, renderError, setRenderError, restartPreview, recomputing },
    experiment: { simulation, values, validation, updateParameter, resetParameters, applyExample },
    explanation: { intent, revision, setRevision, handleRevision },
    save: {
      busy, saveOpen, savedMessage, renderError, currentSimulationId, canManageLearningContent, folders,
      onBusyChange: setBusy, onFolder, onSaved, onClose: () => setSaveOpen(false),
      onOpen: () => { setSavedMessage(""); setSaveOpen(true); },
    },
  };
}

export type SimulationWorkspaceModel = ReturnType<typeof useSimulationWorkspace>;