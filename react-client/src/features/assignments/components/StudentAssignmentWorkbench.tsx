import { Badge, Button, Callout, Card, Heading, IconButton, Progress, Spinner, Text, TextArea, TextField } from "@radix-ui/themes";
import { motion, useReducedMotion } from "motion/react";
import PhysicsScene from "../../simulation/components/BackendSimulationView";
import LearningIcon from "../../../shared/ui/LearningIcon";
import type { Assignment, AssignmentActivityType, Simulation } from "../../../shared/types/physlive";
import SavedScene from "../../simulation/components/SavedScene";
import { hasSavedScene } from "../../simulation/api/simulationUnderstandingApi";
import { interpolateAtTime, learningSeries, numberLabel, type LearningControl } from "../../simulation/model/learningModel";
import { StudentParameterPanel } from "./StudentParameterPanel";
import { StudentSlider } from "./StudentSlider";
import type { VectorVisibility } from "../model/studentTypes";

type AssignmentWorkbenchProps = {
  assignment: Assignment;
  activityType: AssignmentActivityType;
  estimatedValue: string;
  onEstimatedValueChange: (value: string) => void;
  onRetrySimulation: () => void;
  predictionSubmitted: boolean;
  submittedPredictionText: string;
  predictionInput: string;
  reasoningInput: string;
  isSubmittingPrediction: boolean;
  predictionError: string;
  assignmentSubmitted: boolean;
  conclusionInput: string;
  isSubmittingAssignment: boolean;
  submissionError: string;
  simulation: Simulation | null;
  time: number;
  seekRevision: number;
  simLoading: boolean;
  simError: string;
  frame: number;
  playing: boolean;
  vectors: VectorVisibility;
  parameterControls: LearningControl[];
  parameterInitialValues: Record<string, number>;
  parameterDraft: Record<string, string>;
  parameterError: string;
  teacherPrompt: string;
  onBack: () => void;
  onSubmitPrediction: (event: React.FormEvent<HTMLFormElement>) => void;
  onSubmitAssignment: (event: React.FormEvent<HTMLFormElement>) => void;
  onConclusionChange: (value: string) => void;
  onPredictionChange: (value: string) => void;
  onReasoningChange: (value: string) => void;
  onToggleVector: (key: keyof VectorVisibility) => void;
  onTogglePlaying: () => void;
  onReset: () => void;
  onFrameChange: (frame: number) => void;
  onTimeChange: (time: number) => void;
  onPlaybackEnd: () => void;
  onParameterChange: (key: string, value: string) => void;
  onParameterReset: () => void;
};

export function AssignmentWorkbench({
  assignment,
  activityType,
  estimatedValue,
  onEstimatedValueChange,
  onRetrySimulation,
  predictionSubmitted,
  submittedPredictionText,
  predictionInput,
  reasoningInput,
  isSubmittingPrediction,
  predictionError,
  assignmentSubmitted,
  conclusionInput,
  isSubmittingAssignment,
  submissionError,
  simulation,
  time,
  seekRevision,
  simLoading,
  simError,
  frame,
  playing,
  vectors,
  parameterControls,
  parameterInitialValues,
  parameterDraft,
  parameterError,
  teacherPrompt,
  onBack,
  onSubmitPrediction,
  onSubmitAssignment,
  onConclusionChange,
  onPredictionChange,
  onReasoningChange,
  onToggleVector,
  onTogglePlaying,
  onReset,
  onFrameChange,
  onTimeChange,
  onPlaybackEnd,
  onParameterChange,
  onParameterReset,
}: Readonly<AssignmentWorkbenchProps>) {
  const savedScene = simulation && hasSavedScene(simulation.result) ? simulation.result : null;
  const questions = typeof assignment.questions === "object" ? assignment.questions : null;
  const measurement = questions?.measurement;
  const investigation = questions?.investigation;
  const displayedSeries = simulation
    ? learningSeries(simulation).map((series) => ({
        series,
        value: interpolateAtTime(simulation.time, series.data, time),
      }))
    : [];

  const reducedMotion = useReducedMotion();
  const steps = activityType === "PREDICT_OBSERVE_EXPLAIN"
    ? ["Dự đoán", "Quan sát", "Giải thích & nộp bài"]
    : ["Đọc nhiệm vụ", activityType === "MEASUREMENT" ? "Đo lường" : "Khảo sát", "Kết luận & nộp bài"];
  const activeStep = assignmentSubmitted ? 3 : !predictionSubmitted ? 0 : conclusionInput.trim() ? 2 : 1;

  return <motion.section className="student-workbench" initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .18 }}>
    <Button type="button" variant="ghost" className="student-back-button" onClick={onBack}><LearningIcon name="back" />Bài tập được giao</Button>
    <header className="student-workbench-heading">
      <div><Badge variant="soft" size="2">{assignment.className || "Bài tập mô phỏng"}</Badge><Heading as="h1" size="6">{assignment.title}</Heading></div>
      <dl className="student-workbench-meta"><div><dt>Hạn nộp</dt><dd>{assignment.dueAt ? new Date(assignment.dueAt).toLocaleString("vi-VN", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" }) : "Không giới hạn"}</dd></div><div><dt>Thang điểm</dt><dd>{assignment.maxScore ?? 10}</dd></div></dl>
    </header>
    <Card className="student-progress-card" size="3"><ol className="student-progress" aria-label="Tiến trình bài tập">{steps.map((step, index) => <li key={step} className={index < activeStep ? "is-complete" : index === activeStep ? "is-current" : ""} aria-current={index === activeStep ? "step" : undefined}><span className="student-step-marker" aria-hidden="true">{index < activeStep ? <LearningIcon name="check" /> : index + 1}</span><Text size="2" weight={index === activeStep ? "bold" : "medium"}>{step}</Text></li>)}</ol><Progress size="1" value={activeStep / 3 * 100} aria-label="Hoàn thành bài tập" /></Card>
    <Card asChild className="student-task-prompt" size="3"><section aria-label="Yêu cầu bài tập"><div className="student-task-title"><span className="student-task-icon"><LearningIcon name="book" /></span><Heading as="h2" size="3">Yêu cầu</Heading></div>{assignment.description && assignment.description !== teacherPrompt && <Text as="p" size="3">{assignment.description}</Text>}<Text as="p" size="3">{teacherPrompt}</Text></section></Card>
    {assignment.retryAllowed && <Callout.Root color="amber" size="1" className="student-inline-alert"><Callout.Icon><LearningIcon name="message" /></Callout.Icon><Callout.Text>Bài được trả lại. Xem nhận xét và gửi lại bài làm.</Callout.Text></Callout.Root>}
    {(assignment.feedback || (assignment.score != null && !assignment.retryAllowed)) && <Card asChild className="student-assignment-result" size="3"><section aria-label="Kết quả bài tập">{assignment.score != null && !assignment.retryAllowed && <div><Text size="1" color="gray">Điểm</Text><Heading as="h2" size="7" color="cyan">{assignment.score}/{assignment.maxScore ?? 10}</Heading><Badge color={assignment.gradingStatus === "TEACHER_CONFIRMED" ? "cyan" : "amber"}>{assignment.gradingStatus === "TEACHER_CONFIRMED" ? "Đã xác nhận" : "Tạm tính · Chờ xác nhận"}</Badge></div>}{assignment.feedback && <div><Heading as="h3" size="3">Nhận xét của giáo viên</Heading><Text as="p" size="3">{assignment.feedback}</Text></div>}</section></Card>}
    {!predictionSubmitted ? <div className="student-prediction-layout">
      <Card asChild className="student-panel" size="3"><section>
        <header className="student-panel-heading"><Heading as="h2" size="4">1. Dự đoán</Heading><Badge color="amber" size="2">Chưa gửi</Badge></header>
        <form className="student-form" onSubmit={onSubmitPrediction}>
          <Text as="label" size="2" weight="medium" htmlFor="student-prediction">Dự đoán kết quả <span aria-hidden="true">*</span></Text>
          <TextArea id="student-prediction" size="3" rows={4} required maxLength={4000} placeholder="Kết quả bạn dự đoán từ dữ kiện của đề bài…" value={predictionInput} onChange={event => onPredictionChange(event.target.value)} disabled={isSubmittingPrediction} />
          <Text as="label" size="2" weight="medium" htmlFor="student-reasoning">Lập luận hoặc công thức <Text size="1" color="gray">Không bắt buộc</Text></Text>
          <TextArea id="student-reasoning" size="3" rows={3} maxLength={4000} placeholder="Giải thích cơ sở của dự đoán…" value={reasoningInput} onChange={event => onReasoningChange(event.target.value)} disabled={isSubmittingPrediction} />
          {assignment.autoGrade && <div className="student-form-field"><Text as="label" size="2" weight="medium" htmlFor="student-estimate">Kết quả dự đoán bằng số <span aria-hidden="true">*</span></Text><TextField.Root id="student-estimate" size="3" type="number" step="any" required value={estimatedValue} disabled={isSubmittingPrediction} onChange={event => onEstimatedValueChange(event.target.value)} /><Text size="1" color="gray">Dùng đơn vị trong đề bài.</Text></div>}
          {predictionError && <Text as="p" size="2" color="red" role="alert">{predictionError}</Text>}
          <div className="student-form-actions"><Button type="submit" size="3" loading={isSubmittingPrediction} disabled={isSubmittingPrediction || !predictionInput.trim()}>{isSubmittingPrediction ? "Đang gửi…" : "Gửi dự đoán & mở mô phỏng"}<LearningIcon name="arrow" /></Button></div>
        </form>
      </section></Card>
      <Card asChild className="student-panel student-locked-panel" size="3"><section aria-label="Mô phỏng đang khóa">
        <header className="student-panel-heading"><Heading as="h2" size="4">2. Quan sát</Heading></header>
        <div className="student-locked-state"><span className="student-locked-icon"><LearningIcon name="shield" /></span><Heading as="h3" size="3">Mô phỏng chưa mở</Heading><Text size="2" color="gray">Gửi dự đoán để bắt đầu quan sát.</Text></div>
        <Text as="p" className="student-panel-footnote" size="2" color="gray">Sau khi quan sát, viết kết luận để nộp bài.</Text>
      </section></Card>
    </div> : <motion.div className="student-experiment-layout" initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .18 }}>
      <Card asChild className="student-panel student-simulation-panel" size="3"><section>
        <header className="student-panel-heading"><Heading as="h2" size="4">2. {steps[1]}</Heading><Badge color="cyan" size="2">Mô phỏng</Badge></header>
        {!savedScene && <div className="student-view-toggles" role="group" aria-label="Thành phần hiển thị">{([
          ["grid", "Lưới"], ["trajectory", "Đường đi"], ["velocity", "Vận tốc"], ["acceleration", "Gia tốc"],
        ] as [keyof VectorVisibility, string][]).map(([key, label]) => <Button key={key} type="button" size="1" variant={vectors[key] ? "soft" : "surface"} color={vectors[key] ? "indigo" : "gray"} aria-pressed={vectors[key]} onClick={() => onToggleVector(key)}><span className={`student-overlay-dot ${key}`} aria-hidden="true" />{label}</Button>)}</div>}
        {simLoading ? <div className="student-simulation-state" role="status"><Spinner size="3" /><Text color="gray">Đang tải mô phỏng…</Text></div> : simError ? <div className="student-simulation-state" role="alert"><Text as="p" color="red">{simError}</Text><Button type="button" variant="soft" onClick={onRetrySimulation}>Thử lại</Button></div> : savedScene ? <SavedScene scene={savedScene} /> : simulation ? <>
          <div className="student-simulation-stage"><PhysicsScene simulation={simulation} index={frame} overlays={vectors} time={time} seekRevision={seekRevision} playing={playing} onTimeChange={onTimeChange} onPlaybackEnd={onPlaybackEnd} /></div>
          <div className="student-playback-controls"><Button type="button" onClick={onTogglePlaying}><LearningIcon name={playing ? "pause" : "play"} />{playing ? "Tạm dừng" : "Chạy"}</Button><IconButton type="button" variant="soft" onClick={onReset} aria-label="Tua mô phỏng về đầu"><LearningIcon name="reset" /></IconButton><StudentSlider label="Thời điểm mô phỏng" min={0} max={Math.max(1, simulation.time.length - 1)} step={1} value={[frame]} disabled={simulation.time.length < 2} onValueChange={values => onFrameChange(values[0])} /><Text asChild size="2" color="gray"><output>{time.toFixed(2)} s</output></Text></div>
          <section className="student-readouts" aria-label="Đại lượng tại thời điểm đang xem"><header><Heading as="h3" size="3">Đại lượng tức thời</Heading><Badge color="cyan">t = {numberLabel(time, 2)} s</Badge></header><dl>{displayedSeries.map(({ series, value }) => <div key={series.key}><dt><span style={{ background: series.color }} aria-hidden="true" />{series.label}</dt><dd>{numberLabel(value, 3)} <small>{series.unit}</small></dd></div>)}</dl>{displayedSeries.length === 0 && <Text as="p" color="gray" size="2">Chưa có dữ liệu tại thời điểm này.</Text>}</section>
        </> : <div className="student-simulation-state"><Text color="gray">Không có dữ liệu mô phỏng.</Text></div>}
      </section></Card>
      <div className="student-response-column">
        {parameterControls.length > 0 && <Card className="student-panel" size="3"><StudentParameterPanel controls={parameterControls} initialValues={parameterInitialValues} draft={parameterDraft} error={parameterError} onChange={onParameterChange} onReset={onParameterReset} /></Card>}
        <Card asChild className="student-panel" size="3"><section>
          <header className="student-panel-heading"><Heading as="h2" size="4">3. {assignmentSubmitted ? "Bài đã nộp" : steps[2]}</Heading>{assignmentSubmitted && <Badge color="cyan" size="2">Đã nộp</Badge>}</header>
          {activityType === "MEASUREMENT" && measurement && <Callout.Root color="cyan" size="1" className="student-task-details"><Callout.Text><strong>Nhiệm vụ đo</strong><span>{measurement.seriesLabel} tại t = {measurement.sampleTime} s</span><small>Đơn vị: {measurement.unit || "không đơn vị"} · Sai số: ±{measurement.tolerance}</small></Callout.Text></Callout.Root>}
          {activityType === "PARAMETER_INVESTIGATION" && investigation && <Callout.Root size="1" className="student-task-details"><Callout.Text><strong>Nhiệm vụ khảo sát</strong><span>Thay đổi {investigation.parameterLabel}</span>{investigation.outcomeLabel && <small>Quan sát {investigation.outcomeLabel}</small>}</Callout.Text></Callout.Root>}
          {activityType === "PREDICT_OBSERVE_EXPLAIN" && <details className="student-prediction-record" open><summary>Dự đoán đã gửi</summary><p>{submittedPredictionText}</p></details>}
          {assignmentSubmitted ? <div className="student-submitted-answer" aria-live="polite"><Heading as="h3" size="3">Kết luận</Heading><Text as="p" size="3">{conclusionInput}</Text>{assignment.completedAt && <Text size="1" color="gray">Nộp lúc {new Date(assignment.completedAt).toLocaleString("vi-VN")}</Text>}</div> : <form className="student-form" onSubmit={onSubmitAssignment}>
            {activityType === "MEASUREMENT" && <div className="student-form-field"><Text as="label" size="2" weight="medium" htmlFor="student-final-measurement">Kết quả đo ({measurement?.unit || "giá trị số"}) <span aria-hidden="true">*</span></Text><TextField.Root id="student-final-measurement" type="number" size="3" step="any" required value={estimatedValue} onChange={event => onEstimatedValueChange(event.target.value)} disabled={isSubmittingAssignment} /></div>}
            <Text as="label" size="2" weight="medium" htmlFor="student-conclusion">Kết luận <span aria-hidden="true">*</span></Text>
            <TextArea id="student-conclusion" size="3" rows={6} required maxLength={4000} value={conclusionInput} onChange={event => onConclusionChange(event.target.value)} disabled={isSubmittingAssignment} placeholder={activityType === "PREDICT_OBSERVE_EXPLAIN" ? "So sánh quan sát với dự đoán và giải thích kết quả…" : "Ghi kết quả quan sát và giải thích…"} />
            {submissionError && <Text as="p" size="2" color="red" role="alert">{submissionError}</Text>}
            <div className="student-form-actions"><Button type="submit" size="3" loading={isSubmittingAssignment} disabled={isSubmittingAssignment || !conclusionInput.trim() || (activityType === "MEASUREMENT" && !estimatedValue.trim())}>{isSubmittingAssignment ? "Đang nộp…" : "Nộp bài"}<LearningIcon name="upload" /></Button></div>
          </form>}
        </section></Card>
      </div>
    </motion.div>}
  </motion.section>;
}