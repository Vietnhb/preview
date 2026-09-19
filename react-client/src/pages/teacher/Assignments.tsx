import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from "react";
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
  libraryFolders,
  personalLibrary,
} from "../../api/libraryApi";
import type {
  Assignment,
  AssignmentSubmission,
  LibraryFolder,
  LibraryItem,
  StudentOption,
} from "../../types/physlive";
import { isStudentRole } from "../../types/roles";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import TeacherLibraryPane from "../../components/workspace/TeacherLibraryPane";
import { TeacherAssignmentForm } from "../../components/roles/teacher/TeacherAssignmentForm";
import { TeacherAssignmentHistory } from "../../components/roles/teacher/TeacherAssignmentHistory";
import { TeacherSubmissionViewer } from "../../components/roles/teacher/TeacherSubmissionViewer";
import "../../styles/modern-roles.css";

const StudentAssignments = lazy(() => import("../student/StudentAssignments"));

type Props = {
  workspaceLayout?: boolean;
};




export default function Assignments({
  workspaceLayout = false,
}: Readonly<Props>) {
  const user = usePhysliveStore((state) => state.user);
  const [searchParams, setSearchParams] = useSearchParams();
  const queryLibraryItemId = searchParams.get("libraryItemId") ?? "";

  const isStudent = isStudentRole(user?.role);

  // TEACHER ASSIGNMENT STUDIO & SUBMISSIONS MANAGEMENT
  const [items, setItems] = useState<Assignment[]>([]);
  const [saved, setSaved] = useState<LibraryItem[]>([]);
  const [folders, setFolders] = useState<LibraryFolder[]>([]);
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
      const [assignments, libraryItems, folderItems, studentItems] =
        await Promise.all([
          teacherAssignments(),
          personalLibrary(),
          libraryFolders(),
          studentOptions(),
        ]);
      setItems(assignments);
      setSaved(libraryItems);
      setFolders(folderItems);
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

  useEffect(() => {
    const selected = saved.find((item) => item.id === libraryItemId);
    if (selected && !title) setTitle(selected.title);
  }, [saved, libraryItemId, title]);

  const selectedLibrary = useMemo(
    () => saved.find((item) => item.id === libraryItemId),
    [saved, libraryItemId],
  );

  const createWorkspaceFolder = async (name: string) => {
    try {
      const folder = await createLibraryFolder(name);
      setFolders((current) =>
        [...current, folder].sort((left, right) =>
          left.name.localeCompare(right.name, "vi"),
        ),
      );
      return true;
    } catch {
      setError("Chưa tạo được thư mục. Tên thư mục có thể đã tồn tại.");
      return false;
    }
  };

  const selectLibraryItem = async (item: LibraryItem) => {
    updateLibrarySelection(item.id);
    setTitle((current) => (current.trim() ? current : item.title));
  };

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
    setInspectingAssignment(assignment);
    setSubmissionsLoading(true);
    setSubmissionsError("");
    try {
      const data = await assignmentSubmissions(assignment.id);
      setSubmissions(data);
    } catch {
      setSubmissionsError("Chưa tải được danh sách bài nộp của học sinh.");
    } finally {
      setSubmissionsLoading(false);
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
      <div
        className={
          workspaceLayout
            ? "assignment-workspace-container"
            : "modern-container"
        }
      >
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

        {/* Grid: Create Assignment & Assigned List */}
        <div
          className={workspaceLayout ? "assignment-workspace-grid" : "assignment-standard-grid"}
        >
          {workspaceLayout && (
            <TeacherLibraryPane
              folders={folders}
              items={saved}
              currentSimulationId={selectedLibrary?.simulationId ?? ""}
              loading={loading}
              error={error}
              openingId={null}
              onCreateFolder={createWorkspaceFolder}
              onOpen={selectLibraryItem}
            />
          )}
          <TeacherAssignmentForm
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
          />

          <TeacherAssignmentHistory
            workspaceLayout={workspaceLayout}
            items={items}
            loading={loading}
            onRefresh={loadData}
            onOpenSubmissions={(assignment) =>
              void handleOpenSubmissions(assignment)
            }
          />
        </div>

        {inspectingAssignment && (
          <TeacherSubmissionViewer
            assignment={inspectingAssignment}
            submissions={submissions}
            loading={submissionsLoading}
            error={submissionsError}
            onClose={() => setInspectingAssignment(null)}
            onGrade={gradeSubmission}
            onReopen={reopenSubmission}
          />
        )}
      </div>
    </div>
  );
}
