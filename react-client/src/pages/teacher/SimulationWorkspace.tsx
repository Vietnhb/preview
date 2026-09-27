import { useEffect, useState, type ClipboardEvent, type DragEvent, type FormEvent } from "react";
import axios from "axios";
import katex from "katex";
import "katex/dist/katex.min.css";
import LearningHeader from "../../components/common/LearningHeader";
import Icon from "../../components/common/LearningIcon";
import TeacherLibraryPane from "../../components/workspace/TeacherLibraryPane";
import SvgPixiScene from "../../simulation/SvgPixiScene";
import type { SolverTimeline } from "../../simulation/svgScene";
import {
  confirmSimulationExplanation,
  confirmSimulationInput,
  recognizeSimulationImage,
  understandSimulationText,
  recomputeSimulation,
  reviseSimulationIntent,
  type IntentResult,
  type SimulationParameter,
  type GeneratedSimulationResult,
  type SimulationSourceMode,
  type SimulationValidation,
  type RecognitionResult,
} from "../../api/simulationUnderstandingApi";
import { createLibraryFolder } from "../../api/libraryApi";
import { useTeacherLibrary } from "../../store/useTeacherLibrary";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import { canManageLearning } from "../../types/roles";
import type { LibraryItem } from "../../types/physlive";
import "../../styles/learning.css";
import "../../styles/simulation.css";

const MAX_IMAGE_BYTES = 8 * 1024 * 1024;
const IMAGE_MIME_TYPES = new Set(["image/png", "image/jpeg", "image/webp"]);

const QUICK_EXAMPLES = [
  {
    title: "Chuyển động thẳng biến đổi đều",
    text: "Một ô tô đang chạy với vận tốc 15 m/s thì hãm phanh chuyển động chậm dần đều với gia tốc 2 m/s². Hãy mô phỏng chuyển động của xe cho đến khi dừng lại.",
  },
  {
    title: "Ném ngang từ độ cao",
    text: "Ném một vật từ độ cao 20 m theo phương ngang với vận tốc đầu 15 m/s. Lấy gia tốc trọng trường g = 9.8 m/s², bỏ qua sức cản không khí.",
  },
  {
    title: "Va chạm đàn hồi 2 xe",
    text: "Xe 1 có khối lượng 2 kg chuyển động với vận tốc 4 m/s đến va chạm đàn hồi trực diện với xe 2 có khối lượng 1 kg đang đứng yên.",
  },
  {
    title: "Con lắc đơn dao động",
    text: "Một con lắc đơn có chiều dài dây 1.5 m, vật nặng 0.5 kg được kéo lệch góc 30 độ so với phương thẳng đứng rồi thả nhẹ không vận tốc đầu.",
  },
];

function getError(error: unknown) {
  if (axios.isAxiosError<{ message?: string }>(error))
    return error.response?.data?.message || "The server could not complete this step.";
  return error instanceof Error ? error.message : "An unexpected error occurred.";
}

function parameterValue(parameter: SimulationParameter) {
  return parameter.value;
}

function parameterBounds(parameter: SimulationParameter): [number, number] {
  const center = parameterValue(parameter);
  const span = Math.max(1, Math.abs(center) * 2);
  const min = Number.isFinite(parameter.min) ? parameter.min! : center - span;
  const max = Number.isFinite(parameter.max) ? parameter.max! : center + span;
  return min <= max ? [min, max] : [center, center];
}

function RecognitionDisplay({ recognition }: Readonly<{ recognition: RecognitionResult }>) {
  const source = (recognition.displayText || recognition.recognizedText || "").trim();
  if (recognition.sourceMode === "LATEX") {
    const rendered = katex.renderToString(recognition.recognizedText, {
      throwOnError: false,
      trust: false,
      strict: "warn",
      output: "htmlAndMathml",
    });
    return <div className="simulation-recognized-math" aria-label={source}
      dangerouslySetInnerHTML={{ __html: rendered }} />;
  }
  return <div className="simulation-recognized-text">
    {source.split(/\n\s*\n/).map((paragraph, index) => <p key={index}>{paragraph}</p>)}
  </div>;
}

function SimulationParameterControl({ parameter, value, onChange }: Readonly<{
  parameter: SimulationParameter;
  value: number;
  onChange: (value: number) => void;
}>) {
  const [min, max] = parameterBounds(parameter);
  const [draft, setDraft] = useState<string | null>(null);
  const commit = () => {
    const numeric = draft?.trim() ? Number(draft) : NaN;
    if (Number.isFinite(numeric)) onChange(numeric);
    setDraft(null);
  };
  return <label className="simulation-control"><span>{parameter.label || parameter.name}</span>
    <strong>{Number(value.toPrecision(5))} {parameter.unit}</strong>
    {min < max && <><input type="range" min={min} max={max} step={parameter.step ?? "any"} value={value}
      onChange={(event) => onChange(Number(event.target.value))} />
      <input type="number" min={min} max={max} step="any" value={draft ?? String(value)}
        onFocus={() => setDraft(String(value))} onChange={(event) => setDraft(event.target.value)}
        onBlur={commit} onKeyDown={(event) => { if (event.key === "Enter") event.currentTarget.blur(); }} /></>}
  </label>;
}

function FormulaReview({ intent }: Readonly<{ intent: IntentResult }>) {
  return <div className="simulation-formulas">
    <h3>Công thức áp dụng</h3>
    {intent.formulas?.length ? intent.formulas.map(formula => <div key={formula.modelId}>
      <strong>{formula.modelId} · {formula.capabilityId}</strong>
      {formula.canonical.map((equation, index) => <p key={index}><code>{equation}</code></p>)}
      {!!formula.derived?.length && <p>Biểu thức suy ra: {formula.derived.join("; ")}</p>}
    </div>) : <p>Chưa có phương trình thực thi phù hợp trong schema; chỉ có thể tạo minh họa chưa xác minh.</p>}
    <p className="simulation-muted">Schema {intent.schemaId} · phiên bản {intent.schemaVersion}</p>
  </div>;
}

export default function SimulationWorkspace() {
  const user = usePhysliveStore((state) => state.user);
  const canManageLearningContent = canManageLearning(user?.role) || !user;

  const [libraryCollapsed, setLibraryCollapsed] = useState(false);
  const [mobilePanel, setMobilePanel] = useState<"observe" | "inspect">("observe");
  const [inspectorTab, setInspectorTab] = useState<"experiment" | "understand" | "details">("experiment");

  const { folders, setFolders, libraryItems, libraryLoading, libraryError, retryLibrary } = useTeacherLibrary();

  const [sourceMode, setSourceMode] = useState<SimulationSourceMode>("TEXT");
  const [text, setText] = useState("");
  const [sourceFile, setSourceFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
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
  const [values, setValues] = useState<Record<string, number>>({});
  const [runValues, setRunValues] = useState<Record<string, number>>({});
  const [sandboxKey, setSandboxKey] = useState(0);
  const [validation, setValidation] = useState<SimulationValidation | null>(null);
  const [locallyAdjusted, setLocallyAdjusted] = useState(false);

  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);

  useEffect(() => {
    if (simulation) {
      setInspectorTab("experiment");
    }
  }, [simulation]);

  const acceptImage = (file: File) => {
    if (!IMAGE_MIME_TYPES.has(file.type)) {
      setError("Please choose a PNG, JPEG, or WebP image.");
      return;
    }
    if (file.size > MAX_IMAGE_BYTES) {
      setError("Image must be smaller than 8 MB.");
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
    setLocallyAdjusted(false);
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
    reset();
    setText(item.title);
    setSourceMode("TEXT");
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
    try {
      const result = await confirmSimulationExplanation(intent, renderDiagnostics);
      const parameters = result.parameters ?? [];
      if (new Set(parameters.map((item) => item.name)).size !== parameters.length)
        throw new Error("Generated simulation contains duplicate parameter names.");
      if (
        parameters.some(
          (item) =>
            (item.min !== undefined && (!Number.isFinite(item.min) || item.value < item.min)) ||
            (item.max !== undefined && (!Number.isFinite(item.max) || item.value > item.max)) ||
            (item.min !== undefined && item.max !== undefined && item.min > item.max),
        )
      )
        throw new Error("Generated simulation contains inconsistent parameter bounds.");
      const paramValues = Object.fromEntries(
        parameters.map((item) => [item.name, parameterValue(item)]),
      );
      if (
        Object.values(paramValues).some(
          (value) => !Number.isFinite(value),
        )
      )
        throw new Error("Generated simulation has a parameter outside the local runtime limit.");
      if (!result.simulationSpec.visualProgram?.code || !result.simulationSpec.solverTimeline)
        throw new Error("Generated simulation is missing its PixiJS code or server timeline.");
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
    if (!simulation || !locallyAdjusted) return;
    const controller = new AbortController();
    const timeout = window.setTimeout(() => {
      void recomputeSimulation(simulation, values, controller.signal).then(result => {
        if (controller.signal.aborted) return;
        setLiveTimeline(result.solverTimeline);
        setRunValues(values);
        setValidation(result.validation);
        setError(null);
      }).catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        setError(getError(cause));
        setValidation({ status: "FLAGGED", flags: [getError(cause)] });
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
    setValidation({
      status: "PENDING",
      flags: [],
    });
  };

  const resetParameters = () => {
    if (!simulation?.parameters) return;
    const initial = Object.fromEntries(
      simulation.parameters.map((p) => [p.name, parameterValue(p)]),
    );
    setValues(initial);
    setLocallyAdjusted(true);
  };

  const recognitionLowConfidence =
    recognition?.stage === "RECOGNITION_FAILED" ||
    (!manualCorrectionDone &&
      typeof recognition?.confidence === "number" &&
      recognition.confidence < 0.6);

  return (
    <div
      className="learning-app simulation-workspace-app"
      onPaste={onPaste}
      onDragOver={(event) => {
        if (event.dataTransfer.types.includes("Files")) event.preventDefault();
      }}
      onDrop={onDrop}
    >
      <LearningHeader
        onNewSimulation={reset}
        libraryCollapsed={libraryCollapsed}
        onToggleLibrary={canManageLearningContent ? () => setLibraryCollapsed((val) => !val) : undefined}
      />

      <main className="learn-workspace" id="learning-workspace">
        <div className="learn-top-area" aria-hidden="true" />
        <nav className="learn-mobile-nav" aria-label="Chuyển vùng học tập">
          <button
            type="button"
            aria-pressed={mobilePanel === "observe"}
            onClick={() => setMobilePanel("observe")}
          >
            <Icon name="play" /> Quan sát
          </button>
          <button
            type="button"
            aria-pressed={mobilePanel === "inspect"}
            onClick={() => setMobilePanel("inspect")}
          >
            <Icon name="sliders" /> Thông số & Chi tiết
          </button>
        </nav>

        <div
          className="learn-layout"
          data-mobile-panel={mobilePanel}
          data-library-pane={canManageLearningContent}
          data-library-collapsed={libraryCollapsed}
        >
          {/* KHU VỰC 1: THƯ VIỆN BÊN TRÁI */}
          {canManageLearningContent && (
            <TeacherLibraryPane
              folders={folders}
              items={libraryItems}
              onRetry={retryLibrary}
              currentSimulationId=""
              loading={libraryLoading}
              error={libraryError}
              openingId={null}
              onCreateFolder={createFolder}
              onOpen={openLibraryItem}
              onNewSimulation={reset}
            />
          )}

          {/* KHU VỰC 2: KHU VỰC CHÍNH / GIỮA - "giữ yên layout giữa như này" */}
          <section className="learn-exploration" aria-label="Quan sát và khám phá">
            <section className="learn-stage" aria-label="Mô phỏng tương tác">
              {simulation ? (
                /* Layout giữa khi có mô phỏng: Canvas card + Header + Restart */
                <div className="simulation-stage-container">
                  <div className="simulation-panel-heading">
                    <div>
                      <span className="simulation-eyebrow">Interactive simulation</span>
                      <h2>Explore the model</h2>
                    </div>
                    <button
                      type="button"
                      className="simulation-restart-button"
                      onClick={() => {
                        setRunValues(values);
                        setSandboxKey((key) => key + 1);
                      }}
                    >
                      <Icon name="reset" /> Restart
                    </button>
                  </div>

                  <div className="simulation-sandbox-card">
                    {simulation.simulationSpec.visualProgram && liveTimeline ? (
                      <SvgPixiScene
                        key={sandboxKey}
                          program={simulation.simulationSpec.visualProgram}
                          timeline={liveTimeline}
                          parameters={runValues}
                          verificationStatus={validation?.status ?? "VISUAL_ONLY_UNVERIFIED"}
                          onRenderError={setRenderError}
                      />
                    ) : (
                      <p className="simulation-muted">Chưa có code PixiJS và timeline để hiển thị.</p>
                    )}
                  </div>
                  {renderError && <button type="button" disabled={busy} onClick={() => void generate({ code: simulation.code, message: renderError })}>
                    {busy ? "AI đang sửa code…" : "Yêu cầu AI sửa lỗi render"}
                  </button>}
                  {!renderError && <button type="button" disabled={busy} onClick={() => void generate({
                    code: simulation.code,
                    message: "Redesign the current visual presentation as a polished, contextual illustrated world following the original user description and the rendering contract's art direction. Improve clarity, artwork, environment and composition; preserve the signed physics plan. This is visual design feedback, not physics validation.",
                  })}>{busy ? "AI đang thiết kế lại…" : "Thiết kế lại hình ảnh bằng AI"}</button>}
                  <p className="simulation-muted">
                    Kéo tham số để tính lại bằng backend. PixiJS + SVG do AI sinh; BE xác minh dữ liệu vật lý, không xác minh code vẽ.
                  </p>
                  <details>
                    <summary>Code PixiJS + SVG do LLM sinh</summary>
                    <pre style={{ overflow: "auto", maxHeight: "28rem", whiteSpace: "pre-wrap" }}>{simulation.code}</pre>
                  </details>
                  {error && <p className="simulation-error" role="alert">{error}</p>}
                </div>
              ) : (
                /* Layout giữa khi chưa có mô phỏng: Nhập đề, nhận diện, giải thích */
                <div className="simulation-stage-input-container">
                  <ol className="simulation-steps" aria-label="Simulation progress">
                    <li className={!recognition && !intent ? "active" : "done"}>1. Input</li>
                    <li className={recognition ? "active" : intent ? "done" : ""}>2. Recognition</li>
                    <li className={intent ? "active" : ""}>3. Intent & Build</li>
                  </ol>

                  {error && (
                    <div className="simulation-error" role="alert">
                      {error}
                    </div>
                  )}

                  {!recognition && !intent && (
                    <section className="simulation-card simulation-entry">
                      <h2>Enter a description</h2>
                      <div className="simulation-source-tabs" role="group" aria-label="Input channel">
                        {(["TEXT", "LATEX", "IMAGE"] as const).map((mode) => (
                          <button
                            key={mode}
                            type="button"
                            className={sourceMode === mode ? "selected" : ""}
                            aria-pressed={sourceMode === mode}
                            onClick={() => setSourceMode(mode)}
                          >
                            {mode === "TEXT" ? "Text" : mode === "LATEX" ? "LaTeX" : "Image"}
                          </button>
                        ))}
                      </div>

                      <form onSubmit={normalize}>
                        {sourceMode === "IMAGE" && (
                          <div className="simulation-image-drop">
                            {previewUrl ? (
                              <img src={previewUrl} alt="Screenshot selected for recognition" />
                            ) : (
                              <p>Drop or paste a PNG, JPEG, or WebP image up to 8 MB, or choose a file.</p>
                            )}
                            <label className="simulation-file-label">
                              Choose image
                              <input
                                type="file"
                                accept="image/png,image/jpeg,image/webp"
                                onChange={(event) => {
                                  const file = event.target.files?.[0];
                                  if (file) acceptImage(file);
                                }}
                              />
                            </label>
                            {sourceFile && <span>{sourceFile.name}</span>}
                          </div>
                        )}
                        <label htmlFor="simulation-input">
                          {sourceMode === "IMAGE"
                            ? "Optional context"
                            : sourceMode === "LATEX"
                            ? "Paste LaTeX"
                            : "Describe the physical setup"}
                        </label>
                        <textarea
                          id="simulation-input"
                          value={text}
                          onChange={(event) => setText(event.target.value)}
                          rows={sourceMode === "IMAGE" ? 3 : 5}
                          placeholder={
                            sourceMode === "IMAGE"
                              ? "Additional context, if needed"
                              : sourceMode === "LATEX"
                              ? "Paste your LaTeX expression or description"
                              : "Write the objects, interactions, and values you know"
                          }
                        />
                        <div className="simulation-actions">
                          <button
                            className="simulation-primary-button"
                            disabled={
                              busy ||
                              (sourceMode === "IMAGE" && !sourceFile) ||
                              (sourceMode !== "IMAGE" && !text.trim())
                            }
                          >
                            {busy ? "Recognizing…" : "Recognize input"}
                          </button>
                        </div>
                      </form>
                    </section>
                  )}

                  {recognition && (
                    <section className="simulation-card">
                      <span className="simulation-eyebrow">Recognition confirmation</span>
                      <h2>Is this what you meant?</h2>
                      {recognitionLowConfidence && (
                        <p className="simulation-warning" role="status">
                          Recognition was uncertain. Please correct the text before continuing.
                        </p>
                      )}
                      {recognition.message && <p className="simulation-muted">{recognition.message}</p>}
                      <RecognitionDisplay recognition={recognition} />
                      {editingRecognition ? (
                        <div className="simulation-correction">
                          <label htmlFor="simulation-correction">Correct the recognized description</label>
                          <textarea
                            id="simulation-correction"
                            rows={5}
                            value={correction}
                            onChange={(event) => setCorrection(event.target.value)}
                          />
                          <div className="simulation-actions">
                            <button
                              type="button"
                              className="simulation-primary-button"
                              disabled={busy || !correction.trim()}
                              onClick={() => void handleRecognition(false)}
                            >
                              {busy ? "Updating…" : "Review correction"}
                            </button>
                            {!recognitionLowConfidence && (
                              <button type="button" onClick={() => setEditingRecognition(false)}>
                                Cancel edit
                              </button>
                            )}
                          </div>
                        </div>
                      ) : (
                        <div className="simulation-actions">
                          <button
                            type="button"
                            className="simulation-primary-button"
                            disabled={busy || recognition.stage !== "RECOGNITION"}
                            onClick={() => void handleRecognition(true)}
                          >
                            {busy ? "Understanding…" : "Yes, continue"}
                          </button>
                          <button
                            type="button"
                            onClick={() => setEditingRecognition(true)}
                            disabled={busy}
                          >
                            Correct it
                          </button>
                          <button type="button" onClick={reset} disabled={busy}>
                            Start over
                          </button>
                        </div>
                      )}
                    </section>
                  )}

                  {intent && !simulation && (
                    <section className="simulation-card">
                      <span className="simulation-eyebrow">Physics understanding</span>
                      {intent.stage === "CLARIFY" && (
                        <>
                          <h2>One detail is needed</h2>
                          <p className="simulation-explanation">{intent.question || intent.message}</p>
                          <form onSubmit={handleRevision}>
                            <label htmlFor="simulation-answer">Your answer</label>
                            <textarea
                              id="simulation-answer"
                              rows={3}
                              value={revision}
                              onChange={(event) => setRevision(event.target.value)}
                            />
                            <div className="simulation-actions">
                              <button
                                className="simulation-primary-button"
                                disabled={busy || !revision.trim()}
                              >
                                {busy ? "Checking…" : "Answer"}
                              </button>
                            </div>
                          </form>
                        </>
                      )}
                      {intent.stage === "UNSUPPORTED" && (
                        <>
                          <h2>This setup is outside the available simulation templates</h2>
                          <p className="simulation-explanation">
                            {intent.message || intent.explanation}
                          </p>
                          <form onSubmit={handleRevision}>
                            <label htmlFor="simulation-revision">Describe a different setup</label>
                            <textarea
                              id="simulation-revision"
                              rows={3}
                              value={revision}
                              onChange={(event) => setRevision(event.target.value)}
                            />
                            <div className="simulation-actions">
                              <button
                                className="simulation-primary-button"
                                disabled={busy || !revision.trim()}
                              >
                                Try another description
                              </button>
                            </div>
                          </form>
                        </>
                      )}
                      {intent.stage === "EXPLAIN" && (
                        <>
                          <h2>Review the intended simulation</h2>
                          <p className="simulation-explanation">{intent.explanation}</p>
                          <FormulaReview intent={intent} />
                          <p className="simulation-muted">
                            Any predicted outcome here is provisional until the simulation and background check run.
                          </p>
                          <div className="simulation-actions">
                            <button
                              type="button"
                              className="simulation-primary-button"
                              disabled={busy}
                              onClick={() => void generate()}
                            >
                              {busy ? "Generating…" : "Yes, build simulation"}
                            </button>
                            <button type="button" onClick={reset} disabled={busy}>
                              Start over
                            </button>
                          </div>
                          <form className="simulation-revision-form" onSubmit={handleRevision}>
                            <label htmlFor="simulation-change">Something needs to change?</label>
                            <textarea
                              id="simulation-change"
                              rows={2}
                              value={revision}
                              onChange={(event) => setRevision(event.target.value)}
                            />
                            <button disabled={busy || !revision.trim()}>Update explanation</button>
                          </form>
                        </>
                      )}
                    </section>
                  )}
                </div>
              )}
            </section>
          </section>

          {/* KHU VỰC 3: INSPECTOR BÊN PHẢI */}
          <aside className="learn-inspector" aria-label="Thông số và giải thích">
            <div className="learn-inspector-nav">
              <div className="learn-tabs" role="tablist">
                <button
                  role="tab"
                  type="button"
                  aria-selected={inspectorTab === "experiment"}
                  onClick={() => setInspectorTab("experiment")}
                >
                  <Icon name="sliders" /> Thử nghiệm
                </button>
                <button
                  role="tab"
                  type="button"
                  aria-selected={inspectorTab === "understand"}
                  onClick={() => setInspectorTab("understand")}
                >
                  <Icon name="book" /> Giải thích
                </button>
                <button
                  role="tab"
                  type="button"
                  aria-selected={inspectorTab === "details"}
                  onClick={() => setInspectorTab("details")}
                >
                  <Icon name="atom" /> Chi tiết
                </button>
              </div>
            </div>

            <div className="learn-inspector-body">
              {/* Tab 1: Parameters / Controls */}
              {inspectorTab === "experiment" && (
                <div>
                  <div className="learn-section-title">
                    <h3>Thông số mô phỏng</h3>
                    {simulation?.parameters?.length ? (
                      <button
                        type="button"
                        style={{ border: 0, padding: "4px 8px", cursor: "pointer", fontSize: "11px" }}
                        onClick={resetParameters}
                        title="Khôi phục thông số mặc định"
                      >
                        <Icon name="reset" /> Mặc định
                      </button>
                    ) : null}
                  </div>

                  {simulation?.parameters?.length ? (
                    <div style={{ display: "grid", gap: 14, marginTop: 12 }}>
                      {simulation.parameters.map((parameter) => (
                        <SimulationParameterControl
                          key={parameter.name}
                          parameter={parameter}
                          value={values[parameter.name] ?? parameterValue(parameter)}
                          onChange={(next) => updateParameter(parameter, next)}
                        />
                      ))}
                    </div>
                  ) : (
                    <div style={{ padding: "16px 0", color: "#607187" }}>
                      {simulation
                        ? "Mô phỏng này không có biến số điều chỉnh."
                        : (
                          <div>
                            <p style={{ marginBottom: 16 }}>Nhập đề bài để xem và điều chỉnh các thông số mô phỏng tại đây.</p>
                            <span className="learn-small-label">GỢI Ý ĐỀ BÀI</span>
                            <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
                              {QUICK_EXAMPLES.map((ex, i) => (
                                <button
                                  key={i}
                                  type="button"
                                  style={{
                                    textAlign: "left",
                                    padding: "8px 10px",
                                    border: "1px solid #dce4ee",
                                    borderRadius: 8,
                                    background: "#f9fbfe",
                                    cursor: "pointer",
                                    display: "block",
                                    width: "100%",
                                    fontSize: 12,
                                  }}
                                  onClick={() => {
                                    setText(ex.text);
                                    setSourceMode("TEXT");
                                  }}
                                >
                                  <strong>{ex.title}</strong>
                                  <p style={{ margin: "4px 0 0", color: "#607187", fontSize: 11, lineHeight: 1.4 }}>
                                    {ex.text}
                                  </p>
                                </button>
                              ))}
                            </div>
                          </div>
                        )}
                    </div>
                  )}

                  {/* Trạng thái kiểm chứng */}
                  <div className="simulation-validation" role="status" aria-live="polite" style={{ marginTop: 20 }}>
                    <strong>
                      {simulation?.simulationSpec?.runtimeKind === "VISUAL"
                        ? "Trạng thái mô hình: "
                        : "Kiểm tra vật lý: "}
                      {validation?.status === "FLAGGED"
                        ? "Cần kiểm tra lại"
                        : validation?.status === "VERIFIED_ANALYTICAL"
                        ? "Đã xác minh bằng số + công thức chuẩn ✓"
                        : validation?.status === "VERIFIED_NUMERICAL"
                        ? "Đã xác minh bằng solver số ✓"
                        : validation?.status === "VISUAL_ONLY_UNVERIFIED"
                        ? "Chỉ minh họa — chưa xác minh vật lý"
                        : validation?.status === "UNSUPPORTED"
                        ? "Chưa hỗ trợ trung thực"
                        : validation?.status === "PENDING"
                        ? "Đang tính lại và kiểm tra…"
                        : validation?.status === "PAUSED"
                        ? "Đã dừng"
                        : validation?.status === "UNVERIFIED"
                        ? "Chưa kiểm chứng độc lập"
                        : "Đang sẵn sàng"}
                    </strong>
                    {validation?.capabilityId && (
                      <p>
                        Capability: {validation.capabilityId}
                        {validation.solverMethod ? ` · Solver: ${validation.solverMethod}` : ""}
                        {validation.referenceSolverVersion ? ` · Reference: ${validation.referenceSolverVersion}` : ""}
                      </p>
                    )}
                    {validation?.verificationScope && <p>Phạm vi xác minh: {validation.verificationScope}</p>}
                    {validation?.verificationMethod && (
                      <p>Phương pháp xác minh: {validation.verificationMethod}</p>
                    )}
                    {validation?.absoluteError !== undefined && validation.absoluteError !== null && (
                      <p>Sai số lớn nhất: {validation.absoluteError} (tương đối {validation.relativeError ?? "—"})</p>
                    )}
                    {!!validation?.assumptions?.length && (
                      <p>Giả định: {validation.assumptions.join("; ")}</p>
                    )}
                    {validation?.flags?.map((flag, index) => (
                      <p key={index}>{flag}</p>
                    ))}
                  </div>
                </div>
              )}

              {/* Tab 2: Physics Explanation */}
              {inspectorTab === "understand" && (
                <div>
                  <h3>Giải thích hiện tượng vật lý</h3>
                  {intent?.explanation ? (
                    <div style={{ marginTop: 10 }}>
                      <p className="simulation-explanation">{intent.explanation}</p>
                      <FormulaReview intent={intent} />
                    </div>
                  ) : (
                    <p className="simulation-muted" style={{ padding: "12px 0" }}>
                      Phần giải thích hiện tượng và lý thuyết vật lý sẽ xuất hiện ở đây sau khi AI phân tích đề bài.
                    </p>
                  )}

                  {!!intent?.defaults?.length && (
                    <div className="simulation-defaults" style={{ marginTop: 14 }}>
                      <strong>Giả định mặc định</strong>
                      <ul>
                        {intent.defaults.map((item, index) => (
                          <li key={index}>{item}</li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {intent && (
                    <form className="simulation-revision-form" onSubmit={handleRevision}>
                      <label htmlFor="inspector-simulation-change">Cần điều chỉnh gì?</label>
                      <textarea
                        id="inspector-simulation-change"
                        rows={2}
                        value={revision}
                        onChange={(event) => setRevision(event.target.value)}
                        placeholder="Ví dụ: Thay đổi vận tốc hoặc góc ném..."
                      />
                      <button disabled={busy || !revision.trim()}>Cập nhật giải thích</button>
                    </form>
                  )}
                </div>
              )}

              {/* Tab 3: Details & Scene Inventory */}
              {inspectorTab === "details" && (
                <div>
                  <h3>Chi tiết mô hình</h3>

                  {intent?.simulationSpec?.requiredObjects?.length ? (
                    <div className="simulation-requirement-review" style={{ marginTop: 12 }}>
                      <h4>Thành phần AI nhận diện ({intent.simulationSpec.requiredObjects.length})</h4>
                      <ul>
                        {intent.simulationSpec.requiredObjects.map((object, index) => (
                          <li key={index}>
                            <strong>
                              {object.count} × {object.label}
                            </strong>
                            {object.shape && object.shape !== "unspecified" && <span> ({object.shape})</span>}
                          </li>
                        ))}
                      </ul>
                    </div>
                  ) : (
                    !simulation && (
                      <p className="simulation-muted" style={{ padding: "12px 0" }}>
                        AI sẽ chọn các thành phần trực quan theo ngữ cảnh mô tả.
                      </p>
                    )
                  )}
                </div>
              )}
            </div>

            <footer className="learn-inspector-footer">
              <Icon name="atom" />
              <span>PhysLive Simulator · Dual Validation</span>
            </footer>
          </aside>
        </div>
      </main>
    </div>
  );
}
