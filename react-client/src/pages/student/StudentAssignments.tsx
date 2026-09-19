import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import {
  adjustAssignedSimulation,
  assignedSimulation,
  studentAssignments,
  submitAssignmentPrediction,
  logStudentAction,
} from "../../api/assignmentApi";
import { getSharedSimulation } from "../../api/simulationApi";
import { library } from "../../api/libraryApi";
import type { Assignment, LibraryItem, Simulation } from "../../types/physlive";
import { controlValue, indexAtTime, isWithinControlBounds, type LearningControl } from "../../utils/learningModel";
import { AssignmentList } from "../../components/roles/student/StudentAssignmentList";
import { SharedLibrary } from "../../components/roles/student/StudentSharedLibrary";
import { AssignmentWorkbench } from "../../components/roles/student/StudentAssignmentWorkbench";
import "../../styles/modern-roles.css";

interface PredictionPayload {
  answerText: string;
  estimatedValue?: number;
  reasoning?: string;
}






export default function StudentAssignments({
  initialTab = "assigned",
}: Readonly<{ initialTab?: "assigned" | "library" }>) {
  const [activeTab, setActiveTab] = useState<"assigned" | "library">(
    initialTab,
  );

  // Assigned items
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Selected assignment for practicing
  const [selectedAssignment, setSelectedAssignment] =
    useState<Assignment | null>(null);
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
  const [assignedTime, setAssignedTime] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [vectors, setVectors] = useState({
    grid: true,
    trajectory: true,
    velocity: true,
    acceleration: false,
  });
  const [parameterInitialValues, setParameterInitialValues] = useState<
    Record<string, number>
  >({});
  const [parameterDraft, setParameterDraft] = useState<Record<string, string>>(
    {},
  );
  const [parameterAdjusting, setParameterAdjusting] = useState(false);
  const [parameterError, setParameterError] = useState("");
  const parameterTimerRef = useRef<number | null>(null);
  const parameterRequestRef = useRef(0);
  const baseSimulationRef = useRef<Simulation | null>(null);
  const assignmentRequestRef = useRef(0);
  const selectedAssignmentIdRef = useRef<string | null>(null);

  // Class / Shared Library state
  const [sharedItems, setSharedItems] = useState<LibraryItem[]>([]);
  const [selectedTopic, setSelectedTopic] = useState("");
  const [sharedLoading, setSharedLoading] = useState(false);
  const [selectedSharedItem, setSelectedSharedItem] =
    useState<LibraryItem | null>(null);
  const [sharedSimulation, setSharedSimulation] = useState<Simulation | null>(
    null,
  );
  const [sharedSimLoading, setSharedSimLoading] = useState(false);
  const [sharedSimError, setSharedSimError] = useState("");
  const [sharedFrame, setSharedFrame] = useState(0);
  const [sharedTime, setSharedTime] = useState(0);
  const [sharedPlaying, setSharedPlaying] = useState(false);
  const sharedSimulationRequestRef = useRef(0);

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
      setSharedItems(data.filter((item) => item.visibility === "SHARED"));
    } catch {
      // ignore
    } finally {
      setSharedLoading(false);
    }
  }, []);

  const clearParameterState = () => {
    parameterRequestRef.current += 1;
    if (parameterTimerRef.current !== null) {
      globalThis.clearTimeout(parameterTimerRef.current);
      parameterTimerRef.current = null;
    }
    baseSimulationRef.current = null;
    setParameterInitialValues({});
    setParameterDraft({});
    setParameterAdjusting(false);
    setParameterError("");
  };

  const closeAssignment = () => {
    selectedAssignmentIdRef.current = null;
    assignmentRequestRef.current += 1;
    clearParameterState();
    setSelectedAssignment(null);
    setSimulation(null);
    setFrame(0);
    setAssignedTime(0);
    setSimLoading(false);
    setSimError("");
    setPlaying(false);
    setIsSubmittingPrediction(false);
  };

  const closeSharedSimulation = () => {
    sharedSimulationRequestRef.current += 1;
    setSelectedSharedItem(null);
    setSharedSimulation(null);
    setSharedFrame(0);
    setSharedTime(0);
    setSharedSimLoading(false);
    setSharedSimError("");
    setSharedPlaying(false);
  };

  useEffect(() => {
    void loadAssignments();
  }, [loadAssignments]);

  useEffect(() => {
    if (activeTab === "library") {
      void loadSharedLibrary(selectedTopic);
    }
  }, [activeTab, selectedTopic, loadSharedLibrary]);

  const loadAssignedSimulation = useCallback(async (assignmentId: string, requestId: number) => {
    if (requestId !== assignmentRequestRef.current || selectedAssignmentIdRef.current !== assignmentId) return;
    setSimLoading(true);
    setSimError("");
    try {
      const loaded = await assignedSimulation(assignmentId);
      if (requestId !== assignmentRequestRef.current || selectedAssignmentIdRef.current !== assignmentId) return;
      baseSimulationRef.current = loaded;
      const controls = (loaded.visualization?.controls ??
        []) as LearningControl[];
      const values = Object.fromEntries(
        controls.map((control) => [control.key, controlValue(control, loaded)]),
      );
      setParameterInitialValues(values);
      setParameterDraft(
        Object.fromEntries(
          Object.entries(values).map(([key, value]) => [
            key,
            Number.isFinite(value) ? String(value) : "",
          ]),
        ),
      );
      setParameterError("");
      setSimulation(loaded);
      setFrame(0);
      setAssignedTime(loaded.time[0] ?? 0);
    } catch {
      if (requestId !== assignmentRequestRef.current || selectedAssignmentIdRef.current !== assignmentId) return;
      clearParameterState();
      setSimulation(null);
      setSimError("Chưa tải được mô hình mô phỏng của bài tập này.");
    } finally {
      if (requestId === assignmentRequestRef.current && selectedAssignmentIdRef.current === assignmentId) {
        setSimLoading(false);
      }
    }
  }, []);

  // The simulation is deliberately requested only after the prediction gate is open.
  const handleSelectAssignment = async (item: Assignment) => {
    selectedAssignmentIdRef.current = item.id;
    const requestId = ++assignmentRequestRef.current;
    void logStudentAction(item.id, "ASSIGNMENT_OPENED").catch(() => undefined);
    clearParameterState();
    setSelectedAssignment(item);
    const alreadySubmitted = Boolean(item.predictionSubmitted);
    setPredictionSubmitted(alreadySubmitted);
    setSubmittedPredictionText(
      alreadySubmitted ? "Dự đoán đã được gửi trước đó." : "",
    );
    setPredictionInput("");
    setReasoningInput("");
    setPredictionError("");
    setSimulation(null);
    setSimLoading(false);
    setFrame(0);
    setAssignedTime(0);
    setPlaying(false);
    setIsSubmittingPrediction(false);
    setSimError("");
    if (alreadySubmitted) await loadAssignedSimulation(item.id, requestId);
  };

  useEffect(
    () => () => {
      assignmentRequestRef.current += 1;
      sharedSimulationRequestRef.current += 1;
      if (parameterTimerRef.current !== null)
        globalThis.clearTimeout(parameterTimerRef.current);
    },
    [],
  );

  useEffect(() => {
    const controls = (simulation?.visualization?.controls ??
      []) as LearningControl[];
    if (
      !selectedAssignment ||
      !predictionSubmitted ||
      !simulation ||
      controls.length === 0
    )
      return;

    const numericValues: Record<string, number> = {};
    const hasInvalidValue = controls.some((control) => {
      const rawValue = parameterDraft[control.key] ?? "";
      const value = Number(rawValue);
      const invalid = rawValue.trim() === "" || !isWithinControlBounds(control, value);
      if (!invalid) numericValues[control.key] = value;
      return invalid;
    });

    const requestId = ++parameterRequestRef.current;
    if (parameterTimerRef.current !== null)
      globalThis.clearTimeout(parameterTimerRef.current);
    if (hasInvalidValue) {
      setParameterAdjusting(false);
      return;
    }

    const changed = controls.some(
      (control) =>
        simulation.parameters?.[control.key] !== numericValues[control.key],
    );
    if (!changed) {
      setParameterAdjusting(false);
      return;
    }

    setParameterAdjusting(true);
    setParameterError("");
    const simulationId = simulation.simulationId;
    const assignmentId = selectedAssignment.id;
    parameterTimerRef.current = globalThis.setTimeout(() => {
      void adjustAssignedSimulation(assignmentId, simulationId, numericValues)
        .then((updated) => {
          if (requestId !== parameterRequestRef.current) return;
          setSimulation(updated);
          setFrame(0);
          setAssignedTime(updated.time[0] ?? 0);
          setPlaying(false);
        })
        .catch(() => {
          if (requestId === parameterRequestRef.current)
            setParameterError("Không thể cập nhật mô phỏng với giá trị này.");
        })
        .finally(() => {
          if (requestId === parameterRequestRef.current)
            setParameterAdjusting(false);
        });
    }, 180);

    return () => {
      if (parameterTimerRef.current !== null) {
        globalThis.clearTimeout(parameterTimerRef.current);
        parameterTimerRef.current = null;
      }
    };
  }, [parameterDraft, predictionSubmitted, selectedAssignment, simulation]);

  const handleParameterReset = () => {
    const base = baseSimulationRef.current;
    if (!base) return;
    parameterRequestRef.current += 1;
    if (parameterTimerRef.current !== null)
      globalThis.clearTimeout(parameterTimerRef.current);
    const controls = (base.visualization?.controls ?? []) as LearningControl[];
    const values = Object.fromEntries(
      controls.map((control) => [control.key, controlValue(control, base)]),
    );
    setParameterInitialValues(values);
    setParameterDraft(
      Object.fromEntries(
        Object.entries(values).map(([key, value]) => [
          key,
          Number.isFinite(value) ? String(value) : "",
        ]),
      ),
    );
    setParameterError("");
    setParameterAdjusting(false);
    setSimulation(base);
    setFrame(0);
    setAssignedTime(base.time[0] ?? 0);
    setPlaying(false);
  };

  const handleOpenShared = async (item: LibraryItem) => {
    const requestId = ++sharedSimulationRequestRef.current;
    setSelectedSharedItem(item);
    setSharedSimulation(null);
    setSharedFrame(0);
    setSharedTime(0);
    setSharedPlaying(false);
    setSharedSimLoading(true);
    setSharedSimError("");
    try {
      const loaded = await getSharedSimulation(item.simulationId);
      if (requestId !== sharedSimulationRequestRef.current) return;
      setSharedSimulation(loaded);
      setSharedFrame(0);
      setSharedTime(loaded.time[0] ?? 0);
    } catch {
      if (requestId !== sharedSimulationRequestRef.current) return;
      setSharedSimError("Chưa tải được mô phỏng trong tài nguyên này.");
    } finally {
      if (requestId === sharedSimulationRequestRef.current) setSharedSimLoading(false);
    }
  };

  // Submit Prediction Gate (FR-STU-02)
  const handleSubmitPrediction = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAssignment || !predictionInput.trim()) return;
    const assignmentId = selectedAssignment.id;
    const requestId = ++assignmentRequestRef.current;

    setIsSubmittingPrediction(true);
    setPredictionError("");

    const payload: PredictionPayload = {
      answerText: predictionInput.trim(),
      reasoning: reasoningInput.trim() || undefined,
    };

    try {
      await submitAssignmentPrediction(assignmentId, payload);
      if (requestId !== assignmentRequestRef.current || selectedAssignmentIdRef.current !== assignmentId) return;
      void logStudentAction(assignmentId, "PREDICTION_SUBMITTED", payload).catch(() => undefined);
      setPredictionSubmitted(true);
      setSubmittedPredictionText(predictionInput.trim());
      await loadAssignedSimulation(assignmentId, requestId);
    } catch (err: unknown) {
      if (requestId !== assignmentRequestRef.current || selectedAssignmentIdRef.current !== assignmentId) return;
      // If student has already submitted prediction previously, unlock simulation
      if (typeof err === "object" && err !== null && "response" in err) {
        const axiosErr = err as { response?: { status?: number } };
        if (axiosErr.response?.status === 409) {
          setPredictionSubmitted(true);
          setSubmittedPredictionText(
            predictionInput.trim() || "Dự đoán đã ghi nhận trước đó",
          );
          await loadAssignedSimulation(assignmentId, requestId);
          return;
        }
      }
      setPredictionError(
        "Không thể ghi nhận câu trả lời dự đoán. Vui lòng thử lại.",
      );
    } finally {
      if (requestId === assignmentRequestRef.current) setIsSubmittingPrediction(false);
    }
  };

  const teacherPrompt = useMemo(() => {
    if (!selectedAssignment?.questions)
      return "Hãy quan sát hiện tượng và đưa ra dự đoán kết quả trước khi chạy mô phỏng.";
    if (
      typeof selectedAssignment.questions === "object" &&
      selectedAssignment.questions !== null &&
      "prompt" in selectedAssignment.questions
    ) {
      return String(
        (selectedAssignment.questions as { prompt: unknown }).prompt,
      );
    }
    return typeof selectedAssignment.questions === "string"
      ? selectedAssignment.questions
      : JSON.stringify(selectedAssignment.questions);
  }, [selectedAssignment]);

  return (
    <div className={`main student-main student-layout-${activeTab}`}>
      <div className="modern-container">
        {/* Header */}
        <header className="modern-header">
          <div className="modern-header-title">
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "10px",
                marginBottom: "6px",
              }}
            >
              <h1>Bài tập của tôi</h1>
            </div>
            <p>
              Xem bài được giao, gửi dự đoán trước khi chạy mô phỏng và học từ
              tài nguyên được chia sẻ.
            </p>
          </div>
        </header>

        {/* Tabs */}
        <div className="modern-tabs">
          <button
            className={`modern-tab-btn ${activeTab === "assigned" ? "active" : ""}`}
            onClick={() => {
              setActiveTab("assigned");
              closeAssignment();
              closeSharedSimulation();
            }}
          >
            <span>Bài tập được giao</span>
            <span className="modern-tab-badge">{assignments.length}</span>
          </button>
          <button
            className={`modern-tab-btn ${activeTab === "library" ? "active" : ""}`}
            onClick={() => {
              setActiveTab("library");
              closeAssignment();
            }}
          >
            <span>Tài nguyên lớp học</span>
          </button>
        </div>

        {/* TAB 1: ASSIGNED SIMULATIONS */}
        {activeTab === "assigned" && (
          <>
            {selectedAssignment === null ? (
              <AssignmentList
                assignments={assignments}
                loading={loading}
                error={error}
                onRefresh={loadAssignments}
                onSelect={(item) => {
                  void handleSelectAssignment(item);
                }}
              />
            ) : (
              <AssignmentWorkbench
                assignment={selectedAssignment}
                predictionSubmitted={predictionSubmitted}
                submittedPredictionText={submittedPredictionText}
                predictionInput={predictionInput}
                reasoningInput={reasoningInput}
                isSubmittingPrediction={isSubmittingPrediction}
                predictionError={predictionError}
                simulation={simulation}
                time={assignedTime}
                simLoading={simLoading}
                simError={simError}
                frame={frame}
                playing={playing}
                vectors={vectors}
                parameterControls={
                  (simulation?.visualization?.controls ??
                    []) as LearningControl[]
                }
                parameterInitialValues={parameterInitialValues}
                parameterDraft={parameterDraft}
                parameterAdjusting={parameterAdjusting}
                parameterError={parameterError}
                teacherPrompt={teacherPrompt}
                onBack={closeAssignment}
                onSubmitPrediction={handleSubmitPrediction}
                onPredictionChange={setPredictionInput}
                onReasoningChange={setReasoningInput}
                onToggleVector={(key) =>
                  setVectors((current) => ({
                    ...current,
                    [key]: !current[key],
                  }))
                }
                onTogglePlaying={() => setPlaying((current) => !current)}
                onReset={() => {
                  setPlaying(false);
                  setFrame(0);
                  setAssignedTime(simulation?.time[0] ?? 0);
                }}
                onFrameChange={(frameValue) => {
                  setPlaying(false);
                  setFrame(frameValue);
                  setAssignedTime(simulation?.time[frameValue] ?? 0);
                }}
                onTimeChange={(nextTime) => {
                  if (simulation) {
                    setFrame(indexAtTime(simulation.time, nextTime));
                    setAssignedTime(nextTime);
                  }
                }}
                onPlaybackEnd={() => setPlaying(false)}
                onParameterChange={(key, value) =>
                  setParameterDraft((current) => ({ ...current, [key]: value }))
                }
                onParameterReset={handleParameterReset}
              />
            )}
          </>
        )}

        {/* TAB 2: CLASS & SHARED LIBRARY (FR-STU-04) */}
        {activeTab === "library" && (
          <SharedLibrary
            items={sharedItems}
            selectedTopic={selectedTopic}
            loading={sharedLoading}
            selectedItem={selectedSharedItem}
            simulation={sharedSimulation}
            time={sharedTime}
            simulationLoading={sharedSimLoading}
            simulationError={sharedSimError}
            frame={sharedFrame}
            playing={sharedPlaying}
            vectors={vectors}
            onTopicChange={setSelectedTopic}
            onOpen={(item) => {
              void handleOpenShared(item);
            }}
            onClose={closeSharedSimulation}
            onTogglePlaying={() => setSharedPlaying((value) => !value)}
            onReset={() => {
              setSharedPlaying(false);
              setSharedFrame(0);
              setSharedTime(sharedSimulation?.time[0] ?? 0);
            }}
            onFrameChange={(frameValue) => {
              setSharedPlaying(false);
              setSharedFrame(frameValue);
              setSharedTime(sharedSimulation?.time[frameValue] ?? 0);
            }}
            onTimeChange={(nextTime) => {
              if (sharedSimulation) {
                setSharedFrame(indexAtTime(sharedSimulation.time, nextTime));
                setSharedTime(nextTime);
              }
            }}
            onPlaybackEnd={() => setSharedPlaying(false)}
          />
        )}
      </div>
    </div>
  );
}
