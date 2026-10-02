import { useCallback, useEffect, useMemo, useRef, useState, type ComponentProps } from "react";
import { adjustAssignedSimulation, assignedSimulation, studentAssignments, submitAssignmentPrediction, completeAssignment, logStudentAction } from "../api/assignmentApi";
import { createSimulationAdjustment } from "../../simulation/model/simulationAdjustment";
import { controlValue, indexAtTime, isWithinControlBounds, type LearningControl } from "../../simulation/model/learningModel";
import type { Assignment, AssignmentActivityType, Simulation } from "../../../shared/types/physlive";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import type { AssignmentWorkbench } from "../components/StudentAssignmentWorkbench";
import type { AssignmentList } from "../components/StudentAssignmentList";

interface PredictionPayload {
  answerText: string;
  estimatedValue?: number;
  reasoning?: string;
}

const activityTypeOf = (assignment: Assignment): AssignmentActivityType =>
  typeof assignment.questions === "object" && assignment.questions?.activityType
    ? assignment.questions.activityType
    : "PREDICT_OBSERVE_EXPLAIN";

export function useAssignmentPractice(userId: number | undefined) {
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
  const [conclusionInput, setConclusionInput] = useState("");
  const [isSubmittingAssignment, setIsSubmittingAssignment] = useState(false);
  const [submissionError, setSubmissionError] = useState("");

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
  const listRequestRef = useRef(0);
  const mountedRef = useRef(true);
  const predictionLockRef = useRef<object | null>(null);
  const completionLockRef = useRef<object | null>(null);
  const isCurrentSession = useCallback(() => mountedRef.current && userId !== undefined && useSessionStore.getState().user?.id === userId, [userId]);
  const selectedAssignmentIdRef = useRef<string | null>(null);
  const answerDrafts = useRef(new Map<string, { answer: string; reasoning: string; estimate: string }>());

  const loadAssignments = useCallback(async () => {
    if (!isCurrentSession()) return;
    const requestId = ++listRequestRef.current;
    setLoading(true);
    setError("");
    try {
      const data = await studentAssignments();
      if (isCurrentSession() && requestId === listRequestRef.current) setAssignments(data);
    } catch {
      if (isCurrentSession() && requestId === listRequestRef.current) setError("Không thể tải danh sách bài tập được giao.");
    } finally {
      if (isCurrentSession() && requestId === listRequestRef.current) setLoading(false);
    }
  }, [isCurrentSession]);

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
    predictionLockRef.current = null;
    completionLockRef.current = null;
    clearParameterState();
    setSelectedAssignment(null);
    setSimulation(null);
    setFrame(0);
    setAssignedTime(0);
    setSimLoading(false);
    setSimError("");
    setPlaying(false);
    setIsSubmittingPrediction(false);
    setIsSubmittingAssignment(false);
  };

  const loadAssignedSimulation = useCallback(async (assignmentId: string, requestId: number) => {
    if (!isCurrentSession() || requestId !== assignmentRequestRef.current || selectedAssignmentIdRef.current !== assignmentId) return;
    setSimLoading(true);
    setSimError("");
    try {
      const loaded = await assignedSimulation(assignmentId);
      if (!isCurrentSession() || requestId !== assignmentRequestRef.current || selectedAssignmentIdRef.current !== assignmentId) return;
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
      if (!isCurrentSession() || requestId !== assignmentRequestRef.current || selectedAssignmentIdRef.current !== assignmentId) return;
      clearParameterState();
      setSimulation(null);
      setSimError("Chưa tải được mô hình mô phỏng của bài tập này.");
    } finally {
      if (isCurrentSession() && requestId === assignmentRequestRef.current && selectedAssignmentIdRef.current === assignmentId) {
        setSimLoading(false);
      }
    }
  }, [clearParameterState, isCurrentSession]);

  // The simulation is deliberately requested only after the prediction gate is open.
  const handleSelectAssignment = async (item: Assignment) => {
    if (!isCurrentSession()) return;
    predictionLockRef.current = null;
    completionLockRef.current = null;
    setIsSubmittingAssignment(false);
    selectedAssignmentIdRef.current = item.id;
    const requestId = ++assignmentRequestRef.current;
    void logStudentAction(item.id, "ASSIGNMENT_OPENED").catch(() => undefined);
    clearParameterState();
    setSelectedAssignment(item);
    const requiresPrediction = activityTypeOf(item) === "PREDICT_OBSERVE_EXPLAIN";
    const predictionReady = requiresPrediction ? Boolean(item.predictionSubmitted && !item.retryAllowed) : true;
    setPredictionSubmitted(predictionReady);
    setSubmittedPredictionText(
      item.predictions?.answerText ?? (requiresPrediction && predictionReady ? "Dự đoán đã được ghi nhận." : ""),
    );
    const draft = answerDrafts.current.get(item.id);
    setPredictionInput(draft?.answer ?? (item.retryAllowed ? item.predictions?.answerText ?? "" : ""));
    setReasoningInput(draft?.reasoning ?? (item.retryAllowed ? item.predictions?.reasoning ?? "" : ""));
    setEstimatedValue(draft?.estimate ?? (item.retryAllowed && item.predictions?.estimatedValue != null ? String(item.predictions.estimatedValue) : ""));
    setConclusionInput(item.retryAllowed ? "" : item.predictions?.conclusion ?? "");
    setPredictionError("");
    setSubmissionError("");
    setSimulation(null);
    setSimLoading(false);
    setFrame(0);
    setAssignedTime(0);
    setPlaying(false);
    setIsSubmittingPrediction(false);
    setSimError("");
    if (predictionReady) await loadAssignedSimulation(item.id, requestId);
  };

  useEffect(() => {
    mountedRef.current = true;
    void loadAssignments();
    return () => {
      mountedRef.current = false;
      listRequestRef.current += 1;
      assignmentRequestRef.current += 1;
      predictionLockRef.current = null;
      completionLockRef.current = null;
      parameterAdjustment.cancel();
    };
  }, [loadAssignments, parameterAdjustment]);

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
    const requestId = assignmentRequestRef.current;
    const current = () => isCurrentSession() && requestId === assignmentRequestRef.current && selectedAssignmentIdRef.current === assignmentId;
    parameterAdjustment.schedule(
      () => adjustAssignedSimulation(assignmentId, simulationId, numericValues),
      {
        success: updated => {
          if (!current()) return;
          setSimulation(updated);
          setFrame(0);
          setAssignedTime(updated.time[0] ?? 0);
          setSeekRevision(current => current + 1);
          setPlaying(false);
        },
        error: () => { if (current()) setParameterError("Không thể cập nhật mô phỏng. Hãy thử điều chỉnh lại hoặc hoàn tác thông số."); },
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

  // Submit Prediction Gate (FR-STU-02)
  const handleSubmitPrediction = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isCurrentSession() || !selectedAssignment || !predictionInput.trim() || predictionLockRef.current) return;
    if (selectedAssignment.autoGrade && (!estimatedValue.trim() || !Number.isFinite(Number(estimatedValue)))) { setPredictionError("Vui lòng nhập kết quả dự đoán bằng số."); return; }
    const assignmentId = selectedAssignment.id;
    const requestId = ++assignmentRequestRef.current;

    const lock = {};
    predictionLockRef.current = lock;
    setIsSubmittingPrediction(true);
    setPredictionError("");

    const payload: PredictionPayload = {
      answerText: predictionInput.trim(),
      estimatedValue: selectedAssignment.autoGrade ? Number(estimatedValue) : undefined,
      reasoning: reasoningInput.trim() || undefined,
    };

    try {
      const submission = await submitAssignmentPrediction(assignmentId, payload);
      if (!isCurrentSession()) return;
      answerDrafts.current.delete(assignmentId);
      const updated = { ...selectedAssignment, predictionSubmitted: true, submissionCompleted: false, completedAt: null, retryAllowed: false, predictions: payload, score: submission.score, feedback: submission.feedback, gradingStatus: submission.gradingStatus };
      setAssignments(current => current.map(item => item.id === assignmentId ? updated : item));
      if (!isCurrentSession() || requestId !== assignmentRequestRef.current || selectedAssignmentIdRef.current !== assignmentId) return;
      void logStudentAction(assignmentId, "PREDICTION_SUBMITTED", payload).catch(() => undefined);
      setSelectedAssignment(updated);
      setPredictionSubmitted(true);
      setSubmittedPredictionText(predictionInput.trim());
      await loadAssignedSimulation(assignmentId, requestId);
    } catch (err: unknown) {
      if (!isCurrentSession() || requestId !== assignmentRequestRef.current || selectedAssignmentIdRef.current !== assignmentId) return;
      // If student has already submitted prediction previously, unlock simulation
      if (typeof err === "object" && err !== null && "response" in err) {
        const axiosErr = err as { response?: { status?: number } };
        if (axiosErr.response?.status === 409) {
          try {
            const latest = await studentAssignments();
            if (!isCurrentSession() || requestId !== assignmentRequestRef.current) return;
            setAssignments(latest);
            const current = latest.find(item => item.id === assignmentId);
            if (current) await handleSelectAssignment(current);
            else setPredictionError("Bài tập không còn khả dụng. Hãy quay lại danh sách.");
          } catch {
            if (isCurrentSession() && requestId === assignmentRequestRef.current) setPredictionError("Bài đã được gửi nhưng chưa tải được trạng thái mới. Vui lòng thử lại.");
          }
          return;
        }
      }
      setPredictionError(
        "Không thể ghi nhận câu trả lời dự đoán. Vui lòng thử lại.",
      );
    } finally {
      if (predictionLockRef.current === lock) predictionLockRef.current = null;
      if (isCurrentSession() && requestId === assignmentRequestRef.current) setIsSubmittingPrediction(false);
    }
  };

  const handleCompleteAssignment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isCurrentSession() || !selectedAssignment || !predictionSubmitted || !conclusionInput.trim() || completionLockRef.current) return;
    const activityType = activityTypeOf(selectedAssignment);
    if (activityType === "MEASUREMENT" && (!estimatedValue.trim() || !Number.isFinite(Number(estimatedValue)))) {
      setSubmissionError("Vui lòng nhập kết quả đo bằng số trước khi nộp bài.");
      return;
    }
    const assignmentId = selectedAssignment.id;
    const requestId = assignmentRequestRef.current;
    const lock = {};
    completionLockRef.current = lock;
    setIsSubmittingAssignment(true);
    setSubmissionError("");
    try {
      const submission = await completeAssignment(assignmentId, {
        conclusion: conclusionInput.trim(),
        answerText: activityType === "PREDICT_OBSERVE_EXPLAIN" ? selectedAssignment.predictions?.answerText : conclusionInput.trim(),
        estimatedValue: activityType === "MEASUREMENT" ? Number(estimatedValue) : undefined,
      });
      if (!isCurrentSession() || requestId !== assignmentRequestRef.current || selectedAssignmentIdRef.current !== assignmentId) return;
      const updated: Assignment = {
        ...selectedAssignment,
        submissionCompleted: true,
        completedAt: submission.completedAt,
        predictions: { ...selectedAssignment.predictions, conclusion: conclusionInput.trim() },
        score: submission.score,
        feedback: submission.feedback,
        gradingStatus: submission.gradingStatus,
        retryAllowed: false,
      };
      setAssignments(current => current.map(item => item.id === assignmentId ? updated : item));
      setSelectedAssignment(updated);
      void logStudentAction(assignmentId, "ASSIGNMENT_SUBMITTED", { conclusion: conclusionInput.trim() }).catch(() => undefined);
    } catch {
      if (isCurrentSession() && requestId === assignmentRequestRef.current && selectedAssignmentIdRef.current === assignmentId) setSubmissionError("Không thể nộp bài. Vui lòng kiểm tra kết luận và thử lại.");
    } finally {
      if (completionLockRef.current === lock) completionLockRef.current = null;
      if (isCurrentSession() && requestId === assignmentRequestRef.current) setIsSubmittingAssignment(false);
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

  const changeAnswer = (field: "answer" | "reasoning" | "estimate", value: string) => {
    if (!selectedAssignment) return;
    const draft = { answer: predictionInput, reasoning: reasoningInput, estimate: estimatedValue, [field]: value };
    answerDrafts.current.set(selectedAssignment.id, draft);
    if (field === "answer") setPredictionInput(value);
    if (field === "reasoning") setReasoningInput(value);
    if (field === "estimate") setEstimatedValue(value);
  };
  const resetPlayback = () => {
    setPlaying(false);
    setFrame(0);
    setAssignedTime(simulation?.time[0] ?? 0);
    setSeekRevision(current => current + 1);
  };
  const list: ComponentProps<typeof AssignmentList> = {
    assignments, loading, error, onRefresh: () => { void loadAssignments(); },
    onSelect: item => { void handleSelectAssignment(item); },
  };
  const workbench: ComponentProps<typeof AssignmentWorkbench> | null = selectedAssignment ? {
    assignment: selectedAssignment,
    activityType: activityTypeOf(selectedAssignment),
    estimatedValue,
    onEstimatedValueChange: value => changeAnswer("estimate", value),
    onRetrySimulation: () => { void loadAssignedSimulation(selectedAssignment.id, assignmentRequestRef.current); },
    predictionSubmitted, submittedPredictionText, predictionInput, reasoningInput,
    isSubmittingPrediction, predictionError,
    assignmentSubmitted: Boolean(selectedAssignment.submissionCompleted && !selectedAssignment.retryAllowed),
    conclusionInput, isSubmittingAssignment, submissionError,
    simulation, time: assignedTime, seekRevision, simLoading, simError, frame, playing, vectors,
    parameterControls: ((simulation?.visualization?.controls ?? []) as LearningControl[]).filter(control => {
      const questions = typeof selectedAssignment.questions === "object" ? selectedAssignment.questions : null;
      return activityTypeOf(selectedAssignment) !== "PARAMETER_INVESTIGATION" || !questions?.investigation?.parameterKey || control.key === questions.investigation.parameterKey;
    }),
    parameterInitialValues, parameterDraft, parameterError, teacherPrompt,
    onBack: closeAssignment,
    onSubmitPrediction: handleSubmitPrediction,
    onSubmitAssignment: handleCompleteAssignment,
    onConclusionChange: setConclusionInput,
    onPredictionChange: value => changeAnswer("answer", value),
    onReasoningChange: value => changeAnswer("reasoning", value),
    onToggleVector: key => setVectors(current => ({ ...current, [key]: !current[key] })),
    onTogglePlaying: () => {
      if (!simulation) return;
      if (assignedTime >= (simulation.time.at(-1) ?? 0)) {
        setFrame(0);
        setAssignedTime(simulation.time[0] ?? 0);
        setSeekRevision(current => current + 1);
      }
      setPlaying(current => !current);
    },
    onReset: resetPlayback,
    onFrameChange: frameValue => {
      setPlaying(false);
      const nextFrame = Math.min(Math.max(0, frameValue), Math.max(0, (simulation?.time.length ?? 1) - 1));
      setFrame(nextFrame);
      setAssignedTime(simulation?.time[nextFrame] ?? 0);
      setSeekRevision(current => current + 1);
    },
    onTimeChange: nextTime => {
      if (!simulation) return;
      setFrame(indexAtTime(simulation.time, nextTime));
      setAssignedTime(nextTime);
    },
    onPlaybackEnd: () => setPlaying(false),
    onParameterChange: handleParameterChange,
    onParameterReset: handleParameterReset,
  } : null;

  return {
    assignments, list, workbench, vectors, close: closeAssignment,
    pending: assignments.filter(item => !item.submissionCompleted || item.retryAllowed).length,
    completed: assignments.filter(item => item.submissionCompleted && !item.retryAllowed).length,
  };
}