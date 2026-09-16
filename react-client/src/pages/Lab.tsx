import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import Icon from "../components/common/LearningIcon";
import LearningHeader from "../components/common/LearningHeader";
import {
  assignmentSubmissions,
  studentOptions,
  teacherAssignments,
} from "../api/assignmentApi";
import type {
  Assignment,
  AssignmentSubmission,
  StudentOption,
} from "../types/physlive";
import "../styles/learning.css";
import "../styles/modern-roles.css";
import "../styles/lab.css";

type AssignmentRecord = {
  assignment: Assignment;
  submissions: AssignmentSubmission[];
};

type SubmissionFilter = "all" | "submitted" | "pending";

function formatDate(value: string | undefined) {
  if (!value) return "Chưa đặt hạn";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "Chưa đặt hạn"
    : date.toLocaleDateString("vi-VN");
}

function formatShortDate(value: string | undefined) {
  if (!value) return "Chưa đặt hạn";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Chưa đặt hạn";
  return `${String(date.getDate()).padStart(2, "0")} Th${String(date.getMonth() + 1).padStart(2, "0")}`;
}

function formatDateTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString("vi-VN");
}

function predictionText(predictions: unknown) {
  if (typeof predictions === "string") return predictions;
  if (predictions === null || predictions === undefined)
    return "Chưa có dự đoán";
  return JSON.stringify(predictions) ?? "Chưa có dự đoán";
}

function initials(name: string, studentId: number) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return `HS${studentId}`.slice(0, 3).toUpperCase();
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}

function AssignmentList({
  records,
  selectedId,
  onSelect,
  submissionsLoading,
}: Readonly<{
  records: AssignmentRecord[];
  selectedId: string;
  onSelect: (id: string) => void;
  submissionsLoading: boolean;
}>) {
  return (
    <aside className="lab-assignment-list" aria-label="Danh sách bài đã giao">
      <div className="lab-list-heading">
        <div>
          <h2>Bài đã giao</h2>
          <p>Chọn một bài để xem tiến độ.</p>
        </div>
        <span className="lab-list-count">{records.length}</span>
      </div>

      <label className="lab-mobile-selector">
        <span>Bài đang xem</span>
        <select
          value={selectedId}
          onChange={(event) => onSelect(event.target.value)}
          aria-label="Chọn bài đang xem"
        >
          {records.map(({ assignment }) => (
            <option key={assignment.id} value={assignment.id}>
              {assignment.title}
            </option>
          ))}
        </select>
      </label>

      <nav className="lab-assignment-items" aria-label="Bài tập đã giao">
        {records.map(({ assignment, submissions }) => (
          <button
            type="button"
            key={assignment.id}
            className={`lab-assignment-item${selectedId === assignment.id ? " active" : ""}`}
            aria-pressed={selectedId === assignment.id}
            onClick={() => onSelect(assignment.id)}
          >
            <span className="lab-assignment-item-title">
              {assignment.title}
            </span>
            <span className="lab-assignment-item-meta">
              {submissionsLoading
                ? "Đang tải bài nộp…"
                : `${submissions.length}/${assignment.studentIds.length} đã nộp`} · Hạn{" "}
              {formatShortDate(assignment.dueAt)}
            </span>
          </button>
        ))}
      </nav>

      <Link className="lab-create-link" to="/assignments/workspace">
        <Icon name="plus" />
        Tạo bài giao mới
      </Link>
    </aside>
  );
}

function SubmissionTable({
  record,
  students,
  filter,
  query,
}: Readonly<{
  record: AssignmentRecord;
  students: Map<number, StudentOption>;
  filter: SubmissionFilter;
  query: string;
}>) {
  const rows = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase("vi-VN");
    const submissionsByStudent = new Map(
      record.submissions.map((submission) => [
        submission.studentId,
        submission,
      ]),
    );

    return record.assignment.studentIds
      .map((studentId) => {
        const submission = submissionsByStudent.get(studentId);
        const student = students.get(studentId);
        const studentName =
          submission?.studentName?.trim() ||
          student?.fullName?.trim() ||
          `Học sinh #${studentId}`;
        return { studentId, studentName, submission };
      })
      .filter(({ submission, studentId, studentName }) => {
        const matchesFilter =
          filter === "all" ||
          (filter === "submitted" ? Boolean(submission) : !submission);
        const matchesQuery =
          !normalizedQuery ||
          studentName.toLocaleLowerCase("vi-VN").includes(normalizedQuery) ||
          String(studentId).includes(normalizedQuery);
        return matchesFilter && matchesQuery;
      });
  }, [filter, query, record, students]);

  if (record.assignment.studentIds.length === 0) {
    return (
      <div className="lab-empty-state">
        <strong>Chưa có học sinh trong bài giao này</strong>
        <span>Thêm học sinh ở khu giao bài để bắt đầu theo dõi.</span>
      </div>
    );
  }

  if (rows.length === 0) {
    return (
      <div className="lab-empty-state">
        Không có học sinh phù hợp với bộ lọc hiện tại.
      </div>
    );
  }

  return (
    <div className="modern-table-wrapper lab-submission-table-wrapper">
      <table className="modern-table lab-submission-table">
        <thead>
          <tr>
            <th scope="col">Học sinh</th>
            <th scope="col">Dự đoán</th>
            <th scope="col">Nộp lúc</th>
            <th scope="col">Trạng thái</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(({ studentId, studentName, submission }) => (
            <tr key={studentId}>
              <td data-label="Học sinh">
                <div className="lab-student-cell">
                  <span className="lab-student-avatar" aria-hidden="true">
                    {initials(studentName, studentId)}
                  </span>
                  <span>
                    <strong>{studentName}</strong>
                    <small>ID {studentId}</small>
                  </span>
                </div>
              </td>
              <td
                data-label="Dự đoán"
                className={submission ? "" : "lab-muted-cell"}
              >
                {submission
                  ? predictionText(submission.predictions)
                  : "Chưa có dự đoán"}
              </td>
              <td data-label="Nộp lúc">
                {submission ? (
                  <time dateTime={submission.submittedAt}>
                    {formatDateTime(submission.submittedAt)}
                  </time>
                ) : (
                  "—"
                )}
              </td>
              <td data-label="Trạng thái">
                <span
                  className={`lab-status ${submission ? "submitted" : "pending"}`}
                >
                  <span className="lab-status-dot" aria-hidden="true" />
                  {submission ? "Đã nộp" : "Chưa nộp"}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function SubmissionTableLoading() {
  return (
    <div className="lab-submissions-loading" aria-live="polite" aria-busy="true">
      <span className="lab-loading-spinner" aria-hidden="true" />
      <strong>Đang tải bài nộp…</strong>
      <span>Danh sách học sinh sẽ hiển thị ngay khi dữ liệu sẵn sàng.</span>
    </div>
  );
}

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
              <AssignmentList
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
                      <SubmissionTableLoading />
                    ) : (
                      <SubmissionTable
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
