import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import Icon from "../components/common/LearningIcon";
import LearningHeader from "../components/common/LearningHeader";
import { assignmentSubmissions, studentOptions, teacherAssignments } from "../api/assignmentApi";
import type { AssignmentSubmission, StudentOption } from "../types/physlive";
import { TeacherAssignmentList } from "../components/roles/teacher/submissions/TeacherAssignmentList";
import { TeacherSubmissionTable } from "../components/roles/teacher/submissions/TeacherSubmissionTable";
import { TeacherSubmissionTableLoading } from "../components/roles/teacher/submissions/TeacherSubmissionTableLoading";
import type { AssignmentRecord, SubmissionFilter } from "../components/roles/teacher/submissions/teacherSubmissionTypes";
import { formatDate } from "../components/roles/teacher/submissions/teacherSubmissionUtils";
import "../styles/learning.css";
import "../styles/modern-roles.css";
import "../styles/lab.css";

export default function Lab() {
  const [records, setRecords] = useState<AssignmentRecord[]>([]);
  const [students, setStudents] = useState<Map<number, StudentOption>>(
    new Map(),
  );
  const [selectedId, setSelectedId] = useState("");
  const [submissionFilter, setSubmissionFilter] =
    useState<SubmissionFilter>("all");
  const [studentQuery, setStudentQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [submissionsLoading, setSubmissionsLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setSubmissionsLoading(true);
    setError("");
    try {
      const assignments = await teacherAssignments();
      const nextRecords = assignments.map((assignment) => ({
        assignment,
        submissions: [] as AssignmentSubmission[],
      }));
      setRecords(nextRecords);
      setSelectedId((current) =>
        nextRecords.some((item) => item.assignment.id === current)
          ? current
          : (nextRecords[0]?.assignment.id ?? ""),
      );
      setLoading(false);

      const [studentItems, submissionResults] = await Promise.all([
        studentOptions(),
        Promise.all(
          assignments.map(async (assignment) => [
            assignment.id,
            await assignmentSubmissions(assignment.id),
          ] as const),
        ),
      ]);
      const submissionsByAssignment = new Map(submissionResults);
      setStudents(
        new Map(studentItems.map((student) => [student.id, student])),
      );
      setRecords((current) => current.map((item) => ({
        ...item,
        submissions: submissionsByAssignment.get(item.assignment.id) ?? [],
      })));
    } catch {
      setError("Không thể tải danh sách bài nộp. Vui lòng thử lại.");
    } finally {
      setLoading(false);
      setSubmissionsLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const selected = records.find((item) => item.assignment.id === selectedId);
  const totals = useMemo(() => {
    const assigned = records.reduce(
      (total, item) => total + item.assignment.studentIds.length,
      0,
    );
    const submitted = records.reduce(
      (total, item) => total + item.submissions.length,
      0,
    );
    return { assigned, submitted, pending: Math.max(assigned - submitted, 0) };
  }, [records]);

  const selectedTotal = selected?.assignment.studentIds.length ?? 0;
  const selectedSubmitted = selected?.submissions.length ?? 0;
  const selectedProgress =
    selectedTotal === 0
      ? 0
      : Math.round((selectedSubmitted / selectedTotal) * 100);

  return (
    <div className="learning-app lab-page">
      <LearningHeader variant="submissions" />
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
                <strong className="lab-summary-success">{submissionsLoading ? "—" : totals.submitted}</strong>
                <span>Đã nộp</span>
              </div>
              <div className="lab-summary-item">
                <strong className="lab-summary-warning">{submissionsLoading ? "—" : totals.pending}</strong>
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
                submissionsLoading={submissionsLoading}
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
                          {submissionsLoading
                            ? "—"
                            : `${selectedSubmitted} / ${selectedTotal}`}
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
                          placeholder="Tên hoặc ID"
                        />
                      </label>
                    </div>
                    {submissionsLoading ? (
                      <TeacherSubmissionTableLoading />
                    ) : (
                      <TeacherSubmissionTable
                        record={selected}
                        students={students}
                        filter={submissionFilter}
                        query={studentQuery}
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
    </div>
  );
}
