import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  createAssignment,
  studentOptions,
  teacherAssignments,
  assignmentSubmissions,
  gradeAssignmentSubmission,
  reopenAssignmentSubmission,
} from "../../api/assignmentApi";
import {
  createLibraryFolder,
  personalLibrary,
} from "../../api/libraryApi";
import TeacherLibraryPane from "../../components/workspace/TeacherLibraryPane";
import type {
  Assignment,
  AssignmentSubmission,
  LibraryItem,
  StudentOption,
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
  const [libraryItemId, setLibraryItemId] = useState(
    queryLibraryItemId,
  );
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [prompt, setPrompt] = useState("");
  const [maxScore, setMaxScore] = useState("10");
  const [autoGrade, setAutoGrade] = useState(false);
  const [expectedValue, setExpectedValue] = useState("");
  const [tolerance, setTolerance] = useState("0");
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
      const [assignments, libraryItems, studentItems] =
        await Promise.all([
          teacherAssignments(),
          personalLibrary(),
          studentOptions(),
        ]);
      setItems(assignments);
      setSaved(libraryItems.filter(item => item.validationStatus === "PASSED"));
      setStudents(studentItems);
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

  const toggleStudent = (id: number) => {
    setSelectedStudents((curr) =>
      curr.includes(id) ? curr.filter((x) => x !== id) : [...curr, id],
    );
  };

  const handleSelectAllStudents = () => {
    if (selectedStudents.length === students.length) {
      setSelectedStudents([]);
    } else {
      setSelectedStudents(students.map((s) => s.id));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (
      !libraryItemId ||
      !title.trim() ||
      !prompt.trim() ||
      !selectedStudents.length ||
      submitting
    )
      return;

    if (!Number.isFinite(Number(maxScore)) || Number(maxScore) <= 0 || (dueAt && (!Number.isFinite(new Date(dueAt).getTime()) || new Date(dueAt).getTime() <= Date.now())) || (autoGrade && (!expectedValue.trim() || !Number.isFinite(Number(expectedValue)) || !tolerance.trim() || !Number.isFinite(Number(tolerance)) || Number(tolerance) < 0))) {
      setError("Kiểm tra lại điểm tối đa, đáp án số, sai số và hạn nộp."); return;
    }
    setSubmitting(true);
    setError("");
    setNotice("");
    try {
      await createAssignment({
        libraryItemId,
        title: title.trim(),
        description: description.trim() || undefined,
        questions: { prompt: prompt.trim() },
        studentIds: selectedStudents,
        dueAt: dueAt ? new Date(dueAt).toISOString() : undefined,
        maxScore: Number(maxScore) || 10,
        autoGrade,
        gradingCriteria: autoGrade ? { expectedValue: Number(expectedValue), tolerance: Number(tolerance) || 0 } : undefined,
      });
      setFormVersion(version => version + 1);
      // The workspace keeps assignment history in the right rail, so switch
      // back to a fresh composer after sending instead of leaving the center empty.
      setView(workspaceLayout ? "create" : "history");
      setTitle("");
      setDescription("");
      setPrompt("");
      setDueAt("");
      setMaxScore("10"); setAutoGrade(false); setExpectedValue(""); setTolerance("0");
      setSelectedStudents([]);
      updateLibrarySelection("");
      setNotice("Đã giao bài tập cho học sinh thành công.");
      await loadData();
    } catch {
      setError(
        "Không thể giao bài. Chỉ giao được các mô phỏng đã lưu trong thư viện cá nhân.",
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
    const updated = await gradeAssignmentSubmission(inspectingAssignment.id, submissionId, score, inspectingAssignment.maxScore ?? 10, feedback, true);
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
            maxScore={maxScore}
            autoGrade={autoGrade}
            expectedValue={expectedValue}
            tolerance={tolerance}
            dueAt={dueAt}
            students={students}
            selectedStudents={selectedStudents}
            submitting={submitting}
            onSubmit={handleSubmit}
            onLibraryChange={updateLibrarySelection}
            onTitleChange={setTitle}
            onPromptChange={setPrompt}
            onMaxScoreChange={setMaxScore}
            onAutoGradeChange={setAutoGrade}
            onExpectedValueChange={setExpectedValue}
            onToleranceChange={setTolerance}
            onDueAtChange={setDueAt}
            onDescriptionChange={setDescription}
            onToggleStudent={toggleStudent}
            onSelectAll={handleSelectAllStudents}
          />}

          {!workspaceLayout && view === "history" && <TeacherAssignmentHistory
            workspaceLayout={workspaceLayout}
            items={items}
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
