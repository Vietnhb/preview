import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import StudentAssignments from "../student/StudentAssignments";
import {
  createAssignment,
  studentOptions,
  teacherAssignments,
  assignmentSubmissions,
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
import { usePhysliveStore } from "../../store/usePhysliveStore";
import TeacherLibraryPane from "../../components/workspace/TeacherLibraryPane";
import "../../styles/modern-roles.css";

const questionPrompt = (questions: unknown) => {
  if (
    typeof questions === "object" &&
    questions !== null &&
    "prompt" in questions &&
    typeof (questions as { prompt?: unknown }).prompt === "string"
  ) {
    return (questions as { prompt: string }).prompt;
  }
  return "";
};

type TeacherAssignmentFormProps = {
  workspaceLayout: boolean;
  saved: LibraryItem[];
  selectedLibrary: LibraryItem | undefined;
  libraryItemId: string;
  title: string;
  description: string;
  prompt: string;
  dueAt: string;
  students: StudentOption[];
  selectedStudents: number[];
  submitting: boolean;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  onLibraryChange: (id: string) => void;
  onTitleChange: (value: string) => void;
  onPromptChange: (value: string) => void;
  onDueAtChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onToggleStudent: (id: number) => void;
  onSelectAll: () => void;
};

function TeacherAssignmentForm({
  workspaceLayout,
  saved,
  selectedLibrary,
  libraryItemId,
  title,
  description,
  prompt,
  dueAt,
  students,
  selectedStudents,
  submitting,
  onSubmit,
  onLibraryChange,
  onTitleChange,
  onPromptChange,
  onDueAtChange,
  onDescriptionChange,
  onToggleStudent,
  onSelectAll,
}: Readonly<TeacherAssignmentFormProps>) {
  return (
    <div
      className={`modern-card${workspaceLayout ? " assignment-workspace-panel" : ""}`}
    >
      <div className="modern-card-header">
        <div>
          <h2>Giao bài mới</h2>
          <p>Chọn mô phỏng từ thư viện của bạn và chỉ định học sinh.</p>
        </div>
      </div>
      {saved.length === 0 ? (
        <div
          style={{
            padding: "30px 10px",
            textAlign: "center",
            color: "var(--text-muted)",
          }}
        >
          <p style={{ margin: "0 0 10px 0" }}>
            Bạn chưa có mô phỏng nào trong thư viện cá nhân.
          </p>
          <small>
            Hãy hoàn thành một mô phỏng tại Workspace và chọn &quot;Lưu vào thư
            viện&quot; trước.
          </small>
        </div>
      ) : (
        <form onSubmit={onSubmit}>
          <div className="form-group" style={{ marginBottom: "14px" }}>
            <label htmlFor="assignment-library-item">
              Mô phỏng trong thư viện cá nhân *
            </label>
            <select
              id="assignment-library-item"
              required
              value={libraryItemId}
              onChange={(event) => onLibraryChange(event.target.value)}
            >
              <option value="">-- Chọn mô phỏng đã kiểm định --</option>
              {saved.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.title} ({item.topic || "Vật lý"})
                </option>
              ))}
            </select>
          </div>
          {selectedLibrary && (
            <div
              style={{
                background: "#f8fafc",
                padding: "10px 12px",
                borderRadius: "8px",
                border: "1px solid var(--border-subtle)",
                marginBottom: "14px",
                fontSize: "12.5px",
              }}
            >
              Đang giao: <strong>{selectedLibrary.title}</strong> · Chủ đề:{" "}
              {selectedLibrary.topic || "Chung"} · Trạng thái:{" "}
              <span className="status-pill pass">Dual Validation Passed</span>
            </div>
          )}
          <div className="form-group" style={{ marginBottom: "14px" }}>
            <label htmlFor="assignment-title">Tiêu đề bài giao *</label>
            <input
              id="assignment-title"
              required
              placeholder="Ví dụ: Bài tập Ném ngang - Dự đoán vận tốc chạm đất"
              value={title}
              onChange={(event) => onTitleChange(event.target.value)}
            />
          </div>
          <div className="form-group" style={{ marginBottom: "14px" }}>
            <label htmlFor="assignment-prompt">
              Câu hỏi dự đoán cho học sinh (Prediction Gate) *
            </label>
            <textarea
              id="assignment-prompt"
              rows={2}
              required
              placeholder="Ví dụ: Theo em, khi góc bắn tăng từ 30° lên 45° thì tầm xa cực đại tăng hay giảm? Tại sao?"
              value={prompt}
              onChange={(event) => onPromptChange(event.target.value)}
            />
            <small style={{ color: "var(--text-muted)", fontSize: "11.5px" }}>
              Học sinh bắt buộc phải gửi câu trả lời cho câu hỏi này trước khi
              mô phỏng mở khóa kết quả.
            </small>
          </div>
          <div className="form-row" style={{ marginBottom: "14px" }}>
            <div className="form-group">
              <label htmlFor="assignment-due-at">Hạn hoàn thành</label>
              <input
                id="assignment-due-at"
                type="datetime-local"
                value={dueAt}
                onChange={(event) => onDueAtChange(event.target.value)}
              />
            </div>
            <div className="form-group">
              <label htmlFor="assignment-description">
                Ghi chú hướng dẫn thêm
              </label>
              <input
                id="assignment-description"
                placeholder="Không bắt buộc"
                value={description}
                onChange={(event) => onDescriptionChange(event.target.value)}
              />
            </div>
          </div>
          <div className="form-group" style={{ marginBottom: "18px" }}>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                marginBottom: "6px",
              }}
            >
              <span className="form-label">
                Học sinh nhận bài ({selectedStudents.length}/{students.length})
                *
              </span>
              {students.length > 0 && (
                <button
                  type="button"
                  className="role-switch-pill"
                  style={{ border: "1px solid var(--border-subtle)" }}
                  onClick={onSelectAll}
                >
                  {selectedStudents.length === students.length
                    ? "Bỏ chọn tất cả"
                    : "Chọn tất cả"}
                </button>
              )}
            </div>
            <div
              style={{
                maxHeight: "150px",
                overflowY: "auto",
                border: "1px solid var(--border-strong)",
                borderRadius: "8px",
                padding: "10px",
                background: "#ffffff",
              }}
            >
              {students.length === 0 ? (
                <small style={{ color: "var(--text-muted)" }}>
                  Chưa có tài khoản học sinh hoạt động.
                </small>
              ) : (
                students.map((student) => (
                  <label
                    key={student.id}
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "8px",
                      padding: "4px 0",
                      cursor: "pointer",
                      fontSize: "13px",
                    }}
                  >
                    <input
                      type="checkbox"
                      checked={selectedStudents.includes(student.id)}
                      onChange={() => onToggleStudent(student.id)}
                    />
                    <span>{student.fullName}</span>
                  </label>
                ))
              )}
            </div>
          </div>
          <button
            type="submit"
            className="prediction-submit-btn"
            disabled={
              submitting ||
              !libraryItemId ||
              !title.trim() ||
              !prompt.trim() ||
              selectedStudents.length === 0
            }
            style={{ width: "100%", justifyContent: "center" }}
          >
            {submitting
              ? "Đang giao bài…"
              : `Giao bài cho ${selectedStudents.length} học sinh`}
          </button>
        </form>
      )}
    </div>
  );
}

type TeacherAssignmentHistoryProps = {
  workspaceLayout: boolean;
  items: Assignment[];
  loading: boolean;
  onRefresh: () => void;
  onOpenSubmissions: (assignment: Assignment) => void;
};

function TeacherAssignmentHistory({
  workspaceLayout,
  items,
  loading,
  onRefresh,
  onOpenSubmissions,
}: Readonly<TeacherAssignmentHistoryProps>) {
  return (
    <div
      className={`modern-card${workspaceLayout ? " assignment-workspace-panel" : ""}`}
    >
      <div className="modern-card-header">
        <div>
          <h2>Bài đã giao &amp; Theo dõi nộp bài</h2>
          <p>Danh sách các bài tập đã phát hành cho học sinh.</p>
        </div>
        <button
          type="button"
          className="modern-tab-btn"
          style={{
            background: "#ffffff",
            border: "1px solid var(--border-subtle)",
          }}
          onClick={onRefresh}
          disabled={loading}
        >
          {loading ? "…" : "Làm mới"}
        </button>
      </div>
      {loading && (
        <p style={{ color: "var(--text-muted)", padding: "20px" }}>
          Đang tải danh sách bài đã giao…
        </p>
      )}
      {!loading && items.length === 0 && (
        <div
          style={{
            padding: "40px 10px",
            textAlign: "center",
            color: "var(--text-muted)",
          }}
        >
          Chưa có bài tập nào được giao.
        </div>
      )}
      {!loading && items.length > 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
          {items.map((item) => (
            <div
              key={item.id}
              style={{
                border: "1px solid var(--border-subtle)",
                borderRadius: "10px",
                padding: "16px",
                background: "#ffffff",
                boxShadow: "var(--shadow-sm)",
              }}
            >
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "flex-start",
                  marginBottom: "6px",
                }}
              >
                <strong
                  style={{ fontSize: "15px", color: "var(--text-primary)" }}
                >
                  {item.title}
                </strong>
                <span className="status-pill pass">{item.status}</span>
              </div>
              <p
                style={{
                  margin: "0 0 10px 0",
                  fontSize: "13px",
                  color: "var(--text-secondary)",
                }}
              >
                {questionPrompt(item.questions) ||
                  item.description ||
                  "Bài tập mô phỏng"}
              </p>
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  fontSize: "12px",
                  color: "var(--text-muted)",
                }}
              >
                <span>
                  👥 {item.studentIds.length} học sinh nhận bài
                  {item.dueAt
                    ? ` · Hạn ${new Date(item.dueAt).toLocaleDateString("vi-VN")}`
                    : ""}
                </span>
                <button
                  type="button"
                  className="prediction-submit-btn"
                  style={{
                    padding: "5px 12px",
                    fontSize: "12px",
                    background: "var(--role-teacher)",
                  }}
                  onClick={() => onOpenSubmissions(item)}
                >
                  Xem bài nộp ({item.studentIds.length}) →
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

type SubmissionViewerProps = {
  assignment: Assignment;
  submissions: AssignmentSubmission[];
  loading: boolean;
  error: string;
  onClose: () => void;
};

function SubmissionViewer({
  assignment,
  submissions,
  loading,
  error,
  onClose,
}: Readonly<SubmissionViewerProps>) {
  return (
    <dialog
      open
      className="modern-modal-overlay"
      onPointerDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className="modern-modal-content" style={{ maxWidth: "760px" }}>
        <div className="modern-modal-header">
          <div>
            <span className="status-pill info" style={{ marginBottom: "4px" }}>
              Báo cáo nộp bài
            </span>
            <h3 style={{ marginTop: "4px" }}>{assignment.title}</h3>
            <small style={{ color: "var(--text-muted)" }}>
              Câu hỏi dự đoán: &quot;{questionPrompt(assignment.questions)}
              &quot;
            </small>
          </div>
          <button
            type="button"
            className="modern-modal-close"
            onClick={onClose}
          >
            ✕
          </button>
        </div>
        {loading && (
          <p style={{ color: "var(--text-muted)", padding: "20px" }}>
            Đang tải danh sách câu trả lời của học sinh…
          </p>
        )}
        {!loading && error !== "" && (
          <div className="status-pill fail">{error}</div>
        )}
        {!loading && error === "" && submissions.length === 0 && (
          <div
            style={{
              padding: "40px 20px",
              textAlign: "center",
              color: "var(--text-muted)",
            }}
          >
            <p style={{ fontSize: "24px", margin: "0 0 8px 0" }}>⏳</p>
            <strong>Chưa có học sinh nào nộp dự đoán</strong>
            <p style={{ margin: "4px 0 0 0", fontSize: "13px" }}>
              Khi học sinh mở bài và xác nhận dự đoán tại Prediction Gate, câu
              trả lời sẽ xuất hiện tại đây ngay lập tức.
            </p>
          </div>
        )}
        {!loading && error === "" && submissions.length > 0 && (
          <div className="modern-table-wrapper">
            <table className="modern-table">
              <thead>
                <tr>
                  <th>Học sinh</th>
                  <th>Câu trả lời dự đoán</th>
                  <th>Thời gian nộp</th>
                  <th>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {submissions.map((sub) => (
                  <tr key={sub.id}>
                    <td>
                      <strong>{sub.studentName}</strong>
                      <small
                        style={{ display: "block", color: "var(--text-muted)" }}
                      >
                        ID: {sub.studentId}
                      </small>
                    </td>
                    <td style={{ maxWidth: "320px" }}>
                      <div
                        style={{
                          background: "#f8fafc",
                          padding: "8px 10px",
                          borderRadius: "6px",
                          fontSize: "13px",
                        }}
                      >
                        {typeof sub.predictions === "object" &&
                        sub.predictions !== null
                          ? JSON.stringify(sub.predictions)
                          : String(sub.predictions)}
                      </div>
                    </td>
                    <td>
                      <small style={{ color: "var(--text-muted)" }}>
                        {new Date(sub.submittedAt).toLocaleString("vi-VN")}
                      </small>
                    </td>
                    <td>
                      <span className="status-pill pass">Đã nộp</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div style={{ marginTop: "20px", textAlign: "right" }}>
          <button
            type="button"
            className="role-switch-pill"
            style={{
              border: "1px solid var(--border-subtle)",
              padding: "8px 18px",
              fontSize: "13px",
            }}
            onClick={onClose}
          >
            Đóng
          </button>
        </div>
      </div>
    </dialog>
  );
}

type Props = {
  workspaceLayout?: boolean;
};

export default function Assignments({
  workspaceLayout = false,
}: Readonly<Props>) {
  const user = usePhysliveStore((state) => state.user);
  const [searchParams] = useSearchParams();

  const isStudent = user?.role === "STUDENT";

  // TEACHER ASSIGNMENT STUDIO & SUBMISSIONS MANAGEMENT
  const [items, setItems] = useState<Assignment[]>([]);
  const [saved, setSaved] = useState<LibraryItem[]>([]);
  const [folders, setFolders] = useState<LibraryFolder[]>([]);
  const [students, setStudents] = useState<StudentOption[]>([]);
  const [libraryItemId, setLibraryItemId] = useState(
    searchParams.get("libraryItemId") ?? "",
  );
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [prompt, setPrompt] = useState("");
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
    setLibraryItemId(item.id);
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
      });
      setTitle("");
      setDescription("");
      setPrompt("");
      setDueAt("");
      setSelectedStudents([]);
      setLibraryItemId("");
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

  if (isStudent) return <StudentAssignments />;
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
          className={workspaceLayout ? "assignment-workspace-grid" : undefined}
          style={
            workspaceLayout
              ? undefined
              : {
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: "24px",
                  alignItems: "start",
                }
          }
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
            dueAt={dueAt}
            students={students}
            selectedStudents={selectedStudents}
            submitting={submitting}
            onSubmit={handleSubmit}
            onLibraryChange={setLibraryItemId}
            onTitleChange={setTitle}
            onPromptChange={setPrompt}
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
          <SubmissionViewer
            assignment={inspectingAssignment}
            submissions={submissions}
            loading={submissionsLoading}
            error={submissionsError}
            onClose={() => setInspectingAssignment(null)}
          />
        )}
      </div>
    </div>
  );
}
