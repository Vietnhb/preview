import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  createAssignment,
  studentOptions,
  teacherAssignments,
  assignmentSubmissions,
  gradeAssignmentSubmission,
  reopenAssignmentSubmission,
  teacherAssignmentClasses,
} from "../../api/assignmentApi";
import {
  createLibraryFolder,
  personalLibrary,
} from "../../api/libraryApi";
import { getSimulation } from "../../api/simulationApi";
import TeacherLibraryPane from "../../components/workspace/TeacherLibraryPane";
import type {
  Assignment,
  AssignmentSubmission,
  LibraryItem,
  StudentOption,
  Simulation,
  AssignmentActivityType,
  TeacherClassOption,
} from "../../types/physlive";
import { isStudentRole } from "../../types/roles";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import { useTeacherLibrary } from "../../store/useTeacherLibrary";
import { TeacherAssignmentForm } from "../../components/roles/teacher/TeacherAssignmentForm";
import { TeacherAssignmentHistory } from "../../components/roles/teacher/TeacherAssignmentHistory";
import { TeacherSubmissionViewer } from "../../components/roles/teacher/TeacherSubmissionViewer";
import "../../styles/assignment-flow.css";

const StudentAssignments = lazy(() => import("../student/StudentAssignments"));

type Props = {
  workspaceLayout?: boolean;
};




export default function Assignments({
  workspaceLayout = false,
}: Readonly<Props>) {
  const user = usePhysliveStore((state) => state.user);
  const { folders, libraryItems, libraryLoading, libraryError, retryLibrary, setFolders } = useTeacherLibrary();
  const [searchParams, setSearchParams] = useSearchParams();
  const queryLibraryItemId = searchParams.get("libraryItemId") ?? "";

  const isStudent = isStudentRole(user?.role);

  // TEACHER ASSIGNMENT STUDIO & SUBMISSIONS MANAGEMENT
  const [items, setItems] = useState<Assignment[]>([]);
  const [saved, setSaved] = useState<LibraryItem[]>([]);
  const [view, setView] = useState<"create" | "history">(() => workspaceLayout ? "create" : "history");
  const [formVersion, setFormVersion] = useState(0);
  const inspectorRequest = useRef(0);
  const [students, setStudents] = useState<StudentOption[]>([]);
  const [classes, setClasses] = useState<TeacherClassOption[]>([]);
  const [selectedClassId, setSelectedClassId] = useState("");
  const [libraryItemId, setLibraryItemId] = useState(
    queryLibraryItemId,
  );
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [prompt, setPrompt] = useState("");
  const [activityType, setActivityType] = useState<AssignmentActivityType>("PREDICT_OBSERVE_EXPLAIN");
  const [assignmentSimulation, setAssignmentSimulation] = useState<Simulation | null>(null);
  const [simulationOptionsLoading, setSimulationOptionsLoading] = useState(false);
  const [targetSeriesSource, setTargetSeriesSource] = useState("");
  const [sampleTime, setSampleTime] = useState("");
  const [measurementTolerance, setMeasurementTolerance] = useState("0.1");
  const [investigationParameter, setInvestigationParameter] = useState("");
  const [investigationOutcome, setInvestigationOutcome] = useState("");
  const [maxScore, setMaxScore] = useState("10");
  const [dueAt, setDueAt] = useState("");
  const [selectedStudents, setSelectedStudents] = useState<number[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState("");

  // Submissions inspector modal state
  const [inspectingAssignment, setInspectingAssignment] =
    useState<Assignment | null>(null);
  const [submissions, setSubmissions] = useState<AssignmentSubmission[]>([]);
  const [submissionsLoading, setSubmissionsLoading] = useState(false);
  const [submissionsError, setSubmissionsError] = useState("");

  const loadData = useCallback(async () => {
    if (!user || isStudent) return;
    setLoading(true);
    setError("");
    try {
      const [assignments, libraryItems, studentItems, classItems] =
        await Promise.all([
          teacherAssignments(),
          personalLibrary(),
          studentOptions(),
          teacherAssignmentClasses(),
        ]);
      setItems(assignments);
      setSaved(libraryItems.filter(item => item.validationStatus === "PASSED"));
      setStudents(studentItems);
      setClasses(classItems);
    } catch {
      setError("Không thể tải dữ liệu bài tập và danh sách học sinh.");
    } finally {
      setLoading(false);
    }
  }, [isStudent, user]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => {
    setLibraryItemId(queryLibraryItemId);
  }, [queryLibraryItemId]);

  const updateLibrarySelection = (id: string) => {
    setLibraryItemId(id);
    if (!workspaceLayout) return;
    const next = new URLSearchParams(searchParams);
    if (id) next.set("libraryItemId", id);
    else next.delete("libraryItemId");
    setSearchParams(next, { replace: true });
  };

  const handleCreateLibraryFolder = async (name: string) => {
    try {
      const folder = await createLibraryFolder(name);
      setFolders(current => [...current, folder]);
      return true;
    } catch {
      return false;
    }
  };

  const handleOpenLibraryItem = async (item: LibraryItem) => {
    updateLibrarySelection(item.id);
    setView("create");
  };

  useEffect(() => {
    const selected = saved.find((item) => item.id === libraryItemId);
    if (selected) setTitle(current => current || selected.title);
  }, [saved, libraryItemId]);

  const selectedLibrary = useMemo(
    () => saved.find((item) => item.id === libraryItemId),
    [saved, libraryItemId],
  );

  useEffect(() => {
    let active = true;
    setAssignmentSimulation(null);
    setTargetSeriesSource("");
    setInvestigationParameter("");
    setInvestigationOutcome("");
    if (!selectedLibrary?.simulationId) return () => { active = false; };
    setSimulationOptionsLoading(true);
    void getSimulation(selectedLibrary.simulationId).then(simulation => {
      if (!active) return;
      setAssignmentSimulation(simulation);
      const firstSeries = simulation.visualization?.series?.[0];
      setTargetSeriesSource(firstSeries?.source ?? "");
      setInvestigationOutcome(firstSeries?.source ?? "");
      setSampleTime(String(simulation.time.at(-1) ?? 0));
      setInvestigationParameter(Object.keys(simulation.parameters)[0] ?? "");
    }).catch(() => {
      if (active) setAssignmentSimulation(null);
    }).finally(() => {
      if (active) setSimulationOptionsLoading(false);
    });
    return () => { active = false; };
  }, [selectedLibrary]);

  const toggleStudent = (id: number) => {
    setSelectedStudents((curr) =>
      curr.includes(id) ? curr.filter((x) => x !== id) : [...curr, id],
    );
  };

  const classStudents = useMemo(
    () => classes.find(item => item.id === selectedClassId)?.students ?? [],
    [classes, selectedClassId],
  );

  const handleClassChange = (id: string) => {
    setSelectedClassId(id);
    setSelectedStudents([]);
  };

  const handleSelectAllStudents = () => {
    if (selectedStudents.length === classStudents.length) {
      setSelectedStudents([]);
    } else {
      setSelectedStudents(classStudents.map((s) => s.id));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (
      !libraryItemId ||
      !selectedClassId ||
      !title.trim() ||
      !prompt.trim() ||
      !selectedStudents.length ||
      submitting
    )
      return;

    const measurementInvalid = activityType === "MEASUREMENT" && (!targetSeriesSource || !sampleTime.trim() || !Number.isFinite(Number(sampleTime)) || !measurementTolerance.trim() || !Number.isFinite(Number(measurementTolerance)) || Number(measurementTolerance) < 0);
    const investigationInvalid = activityType === "PARAMETER_INVESTIGATION" && !investigationParameter;
    if (!Number.isFinite(Number(maxScore)) || Number(maxScore) <= 0 || (dueAt && (!Number.isFinite(new Date(dueAt).getTime()) || new Date(dueAt).getTime() <= Date.now())) || measurementInvalid || investigationInvalid) {
      setError("Kiểm tra lại điểm tối đa, đáp án số, sai số và hạn nộp."); return;
    }
    setSubmitting(true);
    setError("");
    setNotice("");
    try {
      const selectedSeries = assignmentSimulation?.visualization?.series?.find(series => series.source === targetSeriesSource);
      const outcomeSeries = assignmentSimulation?.visualization?.series?.find(series => series.source === investigationOutcome);
      const selectedControl = assignmentSimulation?.visualization?.controls?.find(control => control.key === investigationParameter);
      await createAssignment({
        libraryItemId,
        classId: selectedClassId,
        title: title.trim(),
        description: description.trim() || undefined,
        questions: {
          prompt: prompt.trim(),
          activityType,
          measurement: activityType === "MEASUREMENT" ? { seriesSource: targetSeriesSource, seriesLabel: selectedSeries?.label ?? targetSeriesSource, unit: selectedSeries?.unit ?? "", sampleTime: Number(sampleTime), tolerance: Number(measurementTolerance) } : undefined,
          investigation: activityType === "PARAMETER_INVESTIGATION" ? { parameterKey: investigationParameter, parameterLabel: selectedControl?.label ?? investigationParameter, outcomeSource: investigationOutcome || undefined, outcomeLabel: outcomeSeries?.label } : undefined,
        },
        studentIds: selectedStudents,
        dueAt: dueAt ? new Date(dueAt).toISOString() : undefined,
        maxScore: Number(maxScore) || 10,
        autoGrade: activityType === "MEASUREMENT",
      });
      setFormVersion(version => version + 1);
      // The workspace keeps assignment history in the right rail, so switch
      // back to a fresh composer after sending instead of leaving the center empty.
      setView(workspaceLayout ? "create" : "history");
      setTitle("");
      setDescription("");
      setPrompt("");
      setActivityType("PREDICT_OBSERVE_EXPLAIN");
      setDueAt("");
      setMaxScore("10");
      setSelectedStudents([]);
      setSelectedClassId("");
      updateLibrarySelection("");
      setNotice("Đã giao bài tập cho học sinh thành công.");
      await loadData();
    } catch {
      setError(
        "Không thể giao bài. Hãy kiểm tra mô phỏng, lớp và danh sách học sinh đã chọn.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  // Open submissions viewer modal
  const handleOpenSubmissions = async (assignment: Assignment) => {
    const request = ++inspectorRequest.current;
    setSubmissions([]);
    setInspectingAssignment(assignment);
    setSubmissionsLoading(true);
    setSubmissionsError("");
    try {
      const data = await assignmentSubmissions(assignment.id);
      if (request === inspectorRequest.current) setSubmissions(data);
    } catch {
      if (request === inspectorRequest.current) setSubmissionsError("Chưa tải được danh sách bài nộp của học sinh.");
    } finally {
      if (request === inspectorRequest.current) setSubmissionsLoading(false);
    }
  };

  const gradeSubmission = async (submissionId: string, score: number, feedback: string) => {
    if (!inspectingAssignment) return;
    const updated = await gradeAssignmentSubmission(inspectingAssignment.id, submissionId, score, feedback, true);
    setSubmissions(current => current.map(item => item.id === updated.id ? updated : item));
  };

  const reopenSubmission = async (submissionId: string) => {
    if (!inspectingAssignment) return;
    const updated = await reopenAssignmentSubmission(inspectingAssignment.id, submissionId);
    setSubmissions(current => current.map(item => item.id === updated.id ? updated : item));
  };

  if (isStudent) {
    return (
      <Suspense fallback={<main className="route-loading" aria-busy="true" />}>
        <StudentAssignments />
      </Suspense>
    );
  }
  if (!user)
    return (
      <main className="main student-main">
        <div className="modern-container">
          <div className="modern-card">
            <h1>Đăng nhập để xem bài tập</h1>
            <p className="muted">
              Khu vực này dành cho giáo viên và học sinh đã đăng nhập.
            </p>
          </div>
        </div>
      </main>
    );

  return (
    <div
      className={
        workspaceLayout ? "learn-workspace assignment-workspace-main" : "main"
      }
    >
      {workspaceLayout && <TeacherLibraryPane
        folders={folders}
        items={libraryItems}
        currentSimulationId=""
        loading={libraryLoading}
        error={libraryError}
        openingId={null}
        onCreateFolder={handleCreateLibraryFolder}
        onOpen={handleOpenLibraryItem}
        onRetry={retryLibrary}
      />}
      <div
        className={
          workspaceLayout
            ? "assignment-workspace-container"
            : "modern-container"
        }
      >
        <div className={workspaceLayout ? "assignment-workspace-content" : "assignment-workspace-content-standard"}>
        {notice && (
          <div
            className="status-pill pass"
            style={{
              padding: "10px 16px",
              marginBottom: "16px",
              width: "100%",
            }}
          >
            {notice}
          </div>
        )}
        {error && (
          <div
            className="status-pill fail"
            style={{
              padding: "10px 16px",
              marginBottom: "16px",
              width: "100%",
            }}
          >
            {error}
          </div>
        )}

        {!workspaceLayout && <header className="assignment-flow-header"><div><span className="assignment-eyebrow">KHÔNG GIAN GIÁO VIÊN</span><h1>Bài tập mô phỏng</h1><p>Soạn bài, giao cho học sinh và theo dõi bài nộp tại một nơi.</p></div><button type="button" className="prediction-submit-btn" onClick={() => setView("create")}>+ Giao bài mới</button></header>}
        {!workspaceLayout && <div className="modern-tabs"><button className={`modern-tab-btn ${view === "history" ? "active" : ""}`} onClick={() => setView("history")}>Bài đã giao · {items.length}</button><button className={`modern-tab-btn ${view === "create" ? "active" : ""}`} onClick={() => setView("create")}>Soạn bài tập</button></div>}
        {loading && <p role="status">Đang tải dữ liệu bài tập…</p>}
        <div
          className="assignment-flow-body"
        >
          {view === "create" && (!loading || (workspaceLayout && saved.length > 0)) && <TeacherAssignmentForm
            key={formVersion}
            workspaceLayout={workspaceLayout}
            saved={saved}
            selectedLibrary={selectedLibrary}
            libraryItemId={libraryItemId}
            title={title}
            description={description}
            prompt={prompt}
            activityType={activityType}
            simulation={assignmentSimulation}
            simulationOptionsLoading={simulationOptionsLoading}
            targetSeriesSource={targetSeriesSource}
            sampleTime={sampleTime}
            measurementTolerance={measurementTolerance}
            investigationParameter={investigationParameter}
            investigationOutcome={investigationOutcome}
            maxScore={maxScore}
            dueAt={dueAt}
            students={classStudents}
            classes={classes}
            selectedClassId={selectedClassId}
            selectedStudents={selectedStudents}
            submitting={submitting}
            onSubmit={handleSubmit}
            onLibraryChange={updateLibrarySelection}
            onTitleChange={setTitle}
            onPromptChange={setPrompt}
            onActivityTypeChange={setActivityType}
            onTargetSeriesSourceChange={setTargetSeriesSource}
            onSampleTimeChange={setSampleTime}
            onMeasurementToleranceChange={setMeasurementTolerance}
            onInvestigationParameterChange={setInvestigationParameter}
            onInvestigationOutcomeChange={setInvestigationOutcome}
            onMaxScoreChange={setMaxScore}
            onDueAtChange={setDueAt}
            onDescriptionChange={setDescription}
            onToggleStudent={toggleStudent}
            onSelectAll={handleSelectAllStudents}
            onClassChange={handleClassChange}
          />}

          {!workspaceLayout && view === "history" && <TeacherAssignmentHistory
            workspaceLayout={workspaceLayout}
            items={items}
            classes={classes}
            students={students}
            loading={loading}
            selectedAssignmentId={inspectingAssignment?.id}
            onRefresh={loadData}
            onOpenSubmissions={(assignment) =>
              void handleOpenSubmissions(assignment)
            }
          />}
        </div>

        </div>
      </div>
      {workspaceLayout && <aside className="assignment-history-rail" aria-label="Danh sách bài đã giao">
        <TeacherAssignmentHistory
          workspaceLayout
          items={items}
          classes={classes}
          students={students}
          loading={loading}
          selectedAssignmentId={inspectingAssignment?.id}
          onRefresh={loadData}
          onOpenSubmissions={assignment => void handleOpenSubmissions(assignment)}
        />
      </aside>}
      {inspectingAssignment && (
        <TeacherSubmissionViewer
          assignment={inspectingAssignment}
          submissions={submissions}
          loading={submissionsLoading}
          error={submissionsError}
          inline={false}
          onClose={() => { inspectorRequest.current += 1; setInspectingAssignment(null); }}
          onGrade={gradeSubmission}
          onReopen={reopenSubmission}
        />
      )}
    </div>
  );
}
