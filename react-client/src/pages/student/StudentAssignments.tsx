import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import {
  adjustAssignedSimulation,
  assignedSimulation,
  studentAssignments,
  submitAssignmentPrediction,
  logStudentAction,
} from "../../api/assignmentApi";
import { createSimulationAdjustment } from "../../utils/simulationAdjustment";
import { getSharedSimulation } from "../../api/simulationApi";
import { library } from "../../api/libraryApi";
import { studentClasses, type StudentClassSummary } from "../../api/schoolApi";
import type { Assignment, LibraryItem, Simulation } from "../../types/physlive";
import { controlValue, indexAtTime, isWithinControlBounds, type LearningControl } from "../../utils/learningModel";
import { AssignmentList } from "../../components/roles/student/StudentAssignmentList";
import { StudentClassOverview } from "../../components/roles/student/StudentClassOverview";
import { ResourceDiscovery as StudentDiscovery } from "../../features/library/components/ResourceDiscovery";
import { AssignmentWorkbench } from "../../components/roles/student/StudentAssignmentWorkbench";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import "../../styles/assignment-flow.css";

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
  const user = usePhysliveStore(state => state.user);

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
  const [estimatedValue, setEstimatedValue] = useState("");
  const [reasoningInput, setReasoningInput] = useState("");
  const [isSubmittingPrediction, setIsSubmittingPrediction] = useState(false);
  const [predictionError, setPredictionError] = useState("");

  // Playback state
  const [frame, setFrame] = useState(0);
  const [assignedTime, setAssignedTime] = useState(0);
  const [seekRevision, setSeekRevision] = useState(0);
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
  const [parameterError, setParameterError] = useState("");
  const [parameterAdjustment] = useState(createSimulationAdjustment);
  const baseSimulationRef = useRef<Simulation | null>(null);
  const assignmentRequestRef = useRef(0);
  const selectedAssignmentIdRef = useRef<string | null>(null);
  const answerDrafts = useRef(new Map<string, { answer: string; reasoning: string; estimate: string }>());

  // Class / Shared Library state
  const [sharedItems, setSharedItems] = useState<LibraryItem[]>([]);
  const [sharedLoading, setSharedLoading] = useState(false);
  const [classes, setClasses] = useState<StudentClassSummary[]>([]);
  const [classesLoading, setClassesLoading] = useState(true);
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

  const loadSharedLibrary = useCallback(async () => {
    setSharedLoading(true);
    try {
      const data = await library();
      setSharedItems(data.filter((item) => item.visibility === "SHARED" || item.visibility === "PUBLIC"));
    } catch {
      // ignore
    } finally {
      setSharedLoading(false);
    }
  }, []);

  const clearParameterState = useCallback(() => {
    parameterAdjustment.cancel();
    baseSimulationRef.current = null;
    setParameterInitialValues({});
    setParameterDraft({});
    setParameterError("");
  }, [parameterAdjustment]);

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
    void loadSharedLibrary();
    void studentClasses().then(setClasses).catch(() => setClasses([])).finally(() => setClassesLoading(false));
  }, [loadSharedLibrary]);

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
  }, [clearParameterState]);

  // The simulation is deliberately requested only after the prediction gate is open.
  const handleSelectAssignment = async (item: Assignment) => {
    selectedAssignmentIdRef.current = item.id;
    const requestId = ++assignmentRequestRef.current;
    void logStudentAction(item.id, "ASSIGNMENT_OPENED").catch(() => undefined);
    clearParameterState();
    setSelectedAssignment(item);
    const alreadySubmitted = Boolean(item.predictionSubmitted && !item.retryAllowed);
    setPredictionSubmitted(alreadySubmitted);
    setSubmittedPredictionText(
      item.predictions?.answerText ?? (alreadySubmitted ? "Dự đoán đã được ghi nhận." : ""),
    );
    const draft = answerDrafts.current.get(item.id);
    setPredictionInput(draft?.answer ?? (item.retryAllowed ? item.predictions?.answerText ?? "" : ""));
    setReasoningInput(draft?.reasoning ?? (item.retryAllowed ? item.predictions?.reasoning ?? "" : ""));
    setEstimatedValue(draft?.estimate ?? (item.retryAllowed && item.predictions?.estimatedValue != null ? String(item.predictions.estimatedValue) : ""));
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
      parameterAdjustment.cancel();
    },
    [parameterAdjustment],
  );

  const handleParameterChange = (key: string, value: string) => {
    if (!selectedAssignment || !predictionSubmitted || !simulation) return;
    parameterAdjustment.cancel();
    setPlaying(false);
    setFrame(0);
    setAssignedTime(simulation.time[0] ?? 0);
    setSeekRevision(current => current + 1);
    setParameterError("");
    const nextDraft = { ...parameterDraft, [key]: value };
    setParameterDraft(nextDraft);
    const controls = (simulation.visualization?.controls ?? []) as LearningControl[];
    if (!controls.some(control => control.key === key)) return;
    const numericValues: Record<string, number> = {};
    for (const control of controls) {
      const raw = nextDraft[control.key] ?? "";
      const numeric = Number(raw);
      if (!raw.trim() || !isWithinControlBounds(control, numeric)) return;
      numericValues[control.key] = numeric;
    }
    const assignmentId = selectedAssignment.id;
    const simulationId = simulation.simulationId;
    parameterAdjustment.schedule(
      () => adjustAssignedSimulation(assignmentId, simulationId, numericValues),
      {
        success: updated => {
          setSimulation(updated);
          setFrame(0);
          setAssignedTime(updated.time[0] ?? 0);
          setSeekRevision(current => current + 1);
          setPlaying(false);
        },
        error: () => setParameterError("Không thể cập nhật mô phỏng. Hãy thử điều chỉnh lại hoặc hoàn tác thông số."),
        settled: () => undefined,
      },
    );
  };

  const handleParameterReset = () => {
    const base = baseSimulationRef.current;
    if (!base) return;
    parameterAdjustment.cancel();
    setSeekRevision(current => current + 1);
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
    if (!selectedAssignment || !predictionInput.trim() || isSubmittingPrediction) return;
    if (selectedAssignment.autoGrade && (!estimatedValue.trim() || !Number.isFinite(Number(estimatedValue)))) { setPredictionError("Vui lòng nhập kết quả dự đoán bằng số."); return; }
    const assignmentId = selectedAssignment.id;
    const requestId = ++assignmentRequestRef.current;

    setIsSubmittingPrediction(true);
    setPredictionError("");

    const payload: PredictionPayload = {
      answerText: predictionInput.trim(),
      estimatedValue: selectedAssignment.autoGrade ? Number(estimatedValue) : undefined,
      reasoning: reasoningInput.trim() || undefined,
    };

    try {
      const submission = await submitAssignmentPrediction(assignmentId, payload);
      answerDrafts.current.delete(assignmentId);
      const updated = { ...selectedAssignment, predictionSubmitted: true, retryAllowed: false, predictions: payload, score: submission.score, feedback: submission.feedback, gradingStatus: submission.gradingStatus };
      setAssignments(current => current.map(item => item.id === assignmentId ? updated : item));
      if (requestId !== assignmentRequestRef.current || selectedAssignmentIdRef.current !== assignmentId) return;
      void logStudentAction(assignmentId, "PREDICTION_SUBMITTED", payload).catch(() => undefined);
      setSelectedAssignment(updated);
      setPredictionSubmitted(true);
      setSubmittedPredictionText(predictionInput.trim());
      await loadAssignedSimulation(assignmentId, requestId);
    } catch (err: unknown) {
      if (requestId !== assignmentRequestRef.current || selectedAssignmentIdRef.current !== assignmentId) return;
      // If student has already submitted prediction previously, unlock simulation
      if (typeof err === "object" && err !== null && "response" in err) {
        const axiosErr = err as { response?: { status?: number } };
        if (axiosErr.response?.status === 409) {
          try {
            const latest = await studentAssignments();
            if (requestId !== assignmentRequestRef.current) return;
            setAssignments(latest);
            const current = latest.find(item => item.id === assignmentId);
            if (current) await handleSelectAssignment(current);
            else setPredictionError("Bài tập không còn khả dụng. Hãy quay lại danh sách.");
          } catch {
            if (requestId === assignmentRequestRef.current) setPredictionError("Bài đã được gửi nhưng chưa tải được trạng thái mới. Vui lòng thử lại.");
          }
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
        {selectedAssignment === null && activeTab === "assigned" && <StudentClassOverview
          studentName={user?.fullName || "bạn"}
          schoolName={classes[0]?.schoolName ?? user?.schoolName}
          classes={classes}
          loading={classesLoading}
          pendingAssignments={assignments.filter(item => !item.predictionSubmitted || item.retryAllowed).length}
          completedAssignments={assignments.filter(item => item.predictionSubmitted && !item.retryAllowed).length}
          sharedResources={sharedItems.length}
          onExplore={() => setActiveTab("library")}
        />}

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
            <span>Khám phá mô phỏng</span>
            <span className="modern-tab-badge">{sharedItems.length}</span>
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
                estimatedValue={estimatedValue}
                onEstimatedValueChange={value => {
                  setEstimatedValue(value);
                  answerDrafts.current.set(selectedAssignment.id, { answer: predictionInput, reasoning: reasoningInput, estimate: value });
                }}
                onRetrySimulation={() => void loadAssignedSimulation(selectedAssignment.id, assignmentRequestRef.current)}
                predictionSubmitted={predictionSubmitted}
                submittedPredictionText={submittedPredictionText}
                predictionInput={predictionInput}
                reasoningInput={reasoningInput}
                isSubmittingPrediction={isSubmittingPrediction}
                predictionError={predictionError}
                simulation={simulation}
                time={assignedTime}
                seekRevision={seekRevision}
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
                parameterError={parameterError}
                teacherPrompt={teacherPrompt}
                onBack={closeAssignment}
                onSubmitPrediction={handleSubmitPrediction}
                onPredictionChange={value => {
                  setPredictionInput(value);
                  answerDrafts.current.set(selectedAssignment.id, { answer: value, reasoning: reasoningInput, estimate: estimatedValue });
                }}
                onReasoningChange={value => {
                  setReasoningInput(value);
                  answerDrafts.current.set(selectedAssignment.id, { answer: predictionInput, reasoning: value, estimate: estimatedValue });
                }}
                onToggleVector={(key) =>
                  setVectors((current) => ({
                    ...current,
                    [key]: !current[key],
                  }))
                }
                onTogglePlaying={() => {
                  if (!simulation) return;
                  if (assignedTime >= (simulation.time.at(-1) ?? 0)) {
                    setFrame(0);
                    setAssignedTime(simulation.time[0] ?? 0);
                    setSeekRevision(current => current + 1);
                  }
                  setPlaying(current => !current);
                }}
                onReset={() => {
                  setPlaying(false);
                  setFrame(0);
                  setAssignedTime(simulation?.time[0] ?? 0);
                  setSeekRevision(current => current + 1);
                }}
                onFrameChange={(frameValue) => {
                  setPlaying(false);
                  setFrame(frameValue);
                  setAssignedTime(simulation?.time[frameValue] ?? 0);
                  setSeekRevision(current => current + 1);
                }}
                onTimeChange={(nextTime) => {
                  if (simulation) {
                    setFrame(indexAtTime(simulation.time, nextTime));
                    setAssignedTime(nextTime);
                  }
                }}
                onPlaybackEnd={() => setPlaying(false)}
                onParameterChange={handleParameterChange}
                onParameterReset={handleParameterReset}
              />
            )}
          </>
        )}

        {/* TAB 2: CLASS & SHARED LIBRARY (FR-STU-04) */}
        {activeTab === "library" && (
          <StudentDiscovery
            items={sharedItems}
            loading={sharedLoading}
            selectedItem={selectedSharedItem}
            simulation={sharedSimulation}
            time={sharedTime}
            simulationLoading={sharedSimLoading}
            simulationError={sharedSimError}
            frame={sharedFrame}
            playing={sharedPlaying}
            vectors={vectors}
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
