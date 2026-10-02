import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Icon from "../../../shared/ui/LearningIcon";
import { assignmentSubmissions, gradeAssignmentSubmission, reopenAssignmentSubmission, studentOptions, teacherAssignments } from "../../assignments/api/assignmentApi";
import type { AssignmentSubmission, StudentOption } from "../../../shared/types/physlive";
import { TeacherAssignmentList } from "../../assignments/components/TeacherAssignmentList";
import { TeacherSubmissionTable } from "../../assignments/components/TeacherSubmissionTable";
import { TeacherSubmissionTableLoading } from "../../assignments/components/TeacherSubmissionTableLoading";
import type { AssignmentRecord, SubmissionFilter } from "../../assignments/model/teacherSubmissionTypes";
import { formatDate } from "../../assignments/model/teacherSubmissionUtils";
import "../styles/learning.css";
import "../styles/lab.css";

export default function Lab() {
  const [records, setRecords] = useState<AssignmentRecord[]>([]);
  const [students, setStudents] = useState<Map<number, StudentOption>>(
    new Map(),
  );
  // "Xem và chấm bài nộp" on the assignment page links here with ?assignment=<id>.
  const [searchParams] = useSearchParams();
  const [selectedId, setSelectedId] = useState(() => searchParams.get("assignment") ?? "");
  const [submissionFilter, setSubmissionFilter] =
    useState<SubmissionFilter>("all");
  const [studentQuery, setStudentQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadedIds, setLoadedIds] = useState<ReadonlySet<string>>(new Set());
  const [failedIds, setFailedIds] = useState<ReadonlySet<string>>(new Set());
  const [error, setError] = useState("");
  const requested = useRef(new Set<string>());
  const generation = useRef(0);

  /** Fetches one assignment's submissions once; a failure only affects that assignment. */
  const loadSubmissions = useCallback(async (assignmentId: string) => {
    if (requested.current.has(assignmentId)) return;
    requested.current.add(assignmentId);
    const run = generation.current;
    try {
      const submissions = await assignmentSubmissions(assignmentId);
      if (run !== generation.current) return;
      setRecords(current => current.map(item => item.assignment.id === assignmentId ? { ...item, submissions } : item));
      setLoadedIds(current => new Set(current).add(assignmentId));
    } catch {
      if (run !== generation.current) return;
      requested.current.delete(assignmentId);
      setFailedIds(current => new Set(current).add(assignmentId));
    }
  }, []);

  const load = useCallback(async () => {
    const run = ++generation.current;
    requested.current = new Set();
    setLoading(true);
    setLoadedIds(new Set());
    setFailedIds(new Set());
    setError("");
    try {
      const assignments = await teacherAssignments();
      if (run !== generation.current) return;
      setRecords(assignments.map(assignment => ({ assignment, submissions: [] as AssignmentSubmission[] })));
      const wanted = new URLSearchParams(window.location.search).get("assignment");
      const firstId = assignments.find(item => item.id === wanted)?.id ?? assignments[0]?.id ?? "";
      setSelectedId(current => assignments.some(item => item.id === current) ? current : firstId);
      setLoading(false);
      void studentOptions().then(items => { if (run === generation.current) setStudents(new Map(items.map(student => [student.id, student]))); }).catch(() => undefined);
      // The assignment on screen first, then the rest a few at a time so a long list does not flood the server.
      const queue = [...assignments.map(item => item.id)].sort((a, b) => Number(b === firstId) - Number(a === firstId));
      const worker = async () => { for (let id = queue.shift(); id && run === generation.current; id = queue.shift()) await loadSubmissions(id); };
      await Promise.all(Array.from({ length: 4 }, worker));
    } catch {
      if (run === generation.current) setError("Không thể tải danh sách bài đã giao. Vui lòng thử lại.");
    } finally {
      if (run === generation.current) setLoading(false);
    }
  }, [loadSubmissions]);

  useEffect(() => {
    void load();
  }, [load]);

  // Opening an assignment the queue has not reached yet fetches it right away.
  useEffect(() => {
    if (selectedId && !loading) void loadSubmissions(selectedId);
  }, [selectedId, loading, loadSubmissions]);

  const submissionsLoading = records.some(item => !loadedIds.has(item.assignment.id) && !failedIds.has(item.assignment.id));
  const selectedLoaded = loadedIds.has(selectedId);
  const selectedFailed = failedIds.has(selectedId);

  const selected = records.find((item) => item.assignment.id === selectedId);
  const totals = useMemo(() => {
    const assigned = records.reduce(
      (total, item) => total + item.assignment.studentIds.length,
      0,
    );
    const submitted = records.reduce(
      (total, item) => total + item.submissions.filter(submission => submission.completedAt).length,
      0,
    );
    return { assigned, submitted, pending: Math.max(assigned - submitted, 0) };
  }, [records]);

  const selectedTotal = selected?.assignment.studentIds.length ?? 0;
  const selectedSubmitted = selected?.submissions.filter(submission => submission.completedAt).length ?? 0;
  const selectedProgress =
    selectedTotal === 0
      ? 0
      : Math.round((selectedSubmitted / selectedTotal) * 100);

  const updateSubmission = useCallback((assignmentId: string, updated: AssignmentSubmission) => {
    setRecords(current => current.map(record => record.assignment.id === assignmentId
      ? { ...record, submissions: record.submissions.map(item => item.id === updated.id ? updated : item) }
      : record));
  }, []);

  const handleGrade = useCallback(async (submissionId: string, score: number, feedback: string) => {
    const record = records.find(item => item.assignment.id === selectedId);
    if (!record) return;
    const updated = await gradeAssignmentSubmission(record.assignment.id, submissionId, score, feedback, true);
    updateSubmission(record.assignment.id, updated);
  }, [records, selectedId, updateSubmission]);

  const handleReopen = useCallback(async (submissionId: string) => {
    const record = records.find(item => item.assignment.id === selectedId);
    if (!record) return;
    const updated = await reopenAssignmentSubmission(record.assignment.id, submissionId);
    updateSubmission(record.assignment.id, updated);
  }, [records, selectedId, updateSubmission]);

  return (
    <>
      <main className="lab-main">
        <div className="lab-container">
          <section className="lab-summary-strip" aria-label="Tổng quan bài nộp">
            <span className="lab-summary-label">Tổng quan</span>
            <div className="lab-summary-items">
              <div className="lab-summary-item">
                <strong>{records.length}</strong>
                <span>Bài đã giao</span>
              </div>
              <div className="lab-summary-item">
                <strong className="lab-summary-success">
                  {submissionsLoading ? "—" : totals.submitted}
                </strong>
                <span>Đã nộp</span>
              </div>
              <div className="lab-summary-item">
                <strong className="lab-summary-warning">
                  {submissionsLoading ? "—" : totals.pending}
                </strong>
                <span>Chưa nộp</span>
              </div>
            </div>
          </section>

          {error && (
            <div className="lab-error" role="alert">
              {error}
            </div>
          )}
          {loading && records.length === 0 && (
            <div className="lab-loading" aria-live="polite">
              Đang tải bài giao và bài nộp…
            </div>
          )}
          {!loading && !error && records.length === 0 && (
            <div className="lab-empty-card">
              <Icon name="message" />
              <h2>Chưa có bài giao để theo dõi</h2>
              <p>
                Hãy giao một bài tập trước. Bài nộp của học sinh sẽ xuất hiện
                tại đây.
              </p>
              <Link to="/assignments/workspace">
                Đi tới khu giao bài <Icon name="arrow" />
              </Link>
            </div>
          )}

          {!error && records.length > 0 && (
            <div className="lab-content-grid">
              <TeacherAssignmentList
                records={records}
                selectedId={selectedId}
                onSelect={setSelectedId}
                loadedIds={loadedIds}
              />
              <section
                className="lab-submission-card"
                aria-label="Chi tiết bài nộp"
              >
                {selected ? (
                  <>
                    <div className="lab-detail-header">
                      <div>
                        <h2>{selected.assignment.title}</h2>
                        <p className="lab-detail-meta">
                          Hạn nộp: {formatDate(selected.assignment.dueAt)}
                        </p>
                      </div>
                      <div className="lab-progress-summary">
                        <strong>
                          {selectedLoaded
                            ? `${selectedSubmitted} / ${selectedTotal}`
                            : "—"}
                        </strong>
                        <span>đã nộp</span>
                      </div>
                    </div>
                    <div
                      className="lab-progress-track"
                      role="progressbar"
                      aria-label="Tiến độ nộp bài"
                      aria-valuemin={0}
                      aria-valuemax={100}
                      aria-valuenow={selectedProgress}
                    >
                      <span style={{ width: `${selectedProgress}%` }} />
                    </div>

                    <div className="lab-table-toolbar">
                      <div
                        className="lab-filter-group"
                        role="group"
                        aria-label="Lọc bài nộp"
                      >
                        <button
                          type="button"
                          className={submissionFilter === "all" ? "active" : ""}
                          aria-pressed={submissionFilter === "all"}
                          onClick={() => setSubmissionFilter("all")}
                        >
                          Tất cả
                        </button>
                        <button
                          type="button"
                          className={
                            submissionFilter === "submitted" ? "active" : ""
                          }
                          aria-pressed={submissionFilter === "submitted"}
                          onClick={() => setSubmissionFilter("submitted")}
                        >
                          Đã nộp
                        </button>
                        <button
                          type="button"
                          className={
                            submissionFilter === "pending" ? "active" : ""
                          }
                          aria-pressed={submissionFilter === "pending"}
                          onClick={() => setSubmissionFilter("pending")}
                        >
                          Chưa nộp
                        </button>
                      </div>
                      <label className="lab-search">
                        <span>Tìm học sinh</span>
                        <input
                          value={studentQuery}
                          onChange={(event) =>
                            setStudentQuery(event.target.value)
                          }
                          placeholder="Nhập tên học sinh"
                        />
                      </label>
                    </div>
                    {selectedFailed ? (
                      <div className="lab-empty-state" role="alert">
                        <strong>Chưa tải được bài nộp của bài này</strong>
                        <button type="button" className="lab-grade-button" onClick={() => { setFailedIds(current => { const next = new Set(current); next.delete(selectedId); return next; }); void loadSubmissions(selectedId); }}>Thử lại</button>
                      </div>
                    ) : !selectedLoaded ? (
                      <TeacherSubmissionTableLoading />
                    ) : (
                      <TeacherSubmissionTable
                        record={selected}
                        students={students}
                        filter={submissionFilter}
                        query={studentQuery}
                        onGrade={handleGrade}
                        onReopen={handleReopen}
                      />
                    )}
                  </>
                ) : (
                  <div className="lab-empty-state">
                    Chọn một bài tập để xem bài nộp.
                  </div>
                )}
              </section>
            </div>
          )}
        </div>
      </main>
    </>
  );
}
